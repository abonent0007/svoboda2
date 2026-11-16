package svoboda.runet.abonent0007.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import svoboda.runet.abonent0007.data.Prefs
import svoboda.runet.abonent0007.net.NetProbe
import kotlin.random.Random

enum class ReconnectState {
    IDLE, CONNECTING, CONNECTED, DEGRADED,
    WAITING_FOR_NETWORK, RECONNECT_WAIT, RECONNECTING,
    FAILED, STOPPING,
}

data class ConnectionIntent(
    var shouldBeConnected: Boolean = false,
    var sessionId: Long = 0L,
)

class ReconnectCoordinator(
    private val context: Context,
    private val controller: VpnController,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
    private val manualConnect: suspend () -> Boolean,
    private val autoConnect: suspend () -> Boolean,
    private val disconnectAction: () -> Unit,
) {
    private val mutex = Mutex()
    private val intent = ConnectionIntent()
    private val _state = MutableStateFlow(ReconnectState.IDLE)
    val state: StateFlow<ReconnectState> = _state.asStateFlow()

    @Volatile
    var testingMode: Boolean = false

    val shouldBeConnected: Boolean get() = intent.shouldBeConnected

    private var failureCount = 0
    private var healthJob: Job? = null
    private var reconnectJob: Job? = null
    private var latestCaps: NetworkCapabilities? = null
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val backoffBase = listOf(2L, 5, 10, 20, 40, 60)

    suspend fun <T> withVpnLock(block: suspend () -> T): T = mutex.withLock { block() }

    fun cancelHealth() {
        healthJob?.cancel()
        healthJob = null
    }

    fun startNetworkMonitoring() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { onNetworkEvent() }
            override fun onLost(network: Network) { onNetworkEvent() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                latestCaps = caps
                onNetworkEvent()
            }
            override fun onLinkPropertiesChanged(network: Network, lp: android.net.LinkProperties) {
                onNetworkEvent()
            }
        }
        runCatching { cm.registerNetworkCallback(request, cb) }
    }

    private fun onNetworkEvent() {
        scope.launch {
            delay(2000)
            if (!intent.shouldBeConnected) return@launch
            val currentSession = intent.sessionId
            if (!isNetworkAvailable()) {
                _state.value = ReconnectState.WAITING_FOR_NETWORK
                controller.setStatus("Ожидание сети…")
                return@launch
            }
            if (isCaptivePortal()) {
                _state.value = ReconnectState.WAITING_FOR_NETWORK
                controller.setStatus("Требуется авторизация в сети")
                return@launch
            }
            mutex.withLock {
                if (intent.sessionId != currentSession) return@withLock
                val healthy = checkTunnel()
                if (healthy) {
                    _state.value = ReconnectState.CONNECTED
                    failureCount = 0
                    // Соединение восстановлено — убираем устаревший обратный отсчёт.
                    controller.setStatus(null)
                } else {
                    scheduleReconnect(currentSession)
                }
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val caps = latestCaps ?: cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        return caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun isCaptivePortal(): Boolean {
        val caps = latestCaps ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) ||
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun checkTunnel(): Boolean {
        return runBlocking {
            // DPI-режим: рукопожатия нет; здоровье проверяем через локальный SOCKS5 (ciadpi).
            controller.dpiProbePort?.let { port ->
                return@runBlocking NetProbe.fetchExitViaSocks(port) != null
            }
            if (controller.lastHandshake() <= 0) return@runBlocking false
            NetProbe.fetchExit() != null
        }
    }

    suspend fun userConnect(manual: Boolean) = mutex.withLock {
        intent.sessionId++
        intent.shouldBeConnected = true
        val currentSession = intent.sessionId
        cancelReconnect()
        cancelHealth()
        _state.value = ReconnectState.CONNECTING
        // Новая попытка — убираем устаревший обратный отсчёт прошлой ошибки.
        controller.setStatus(null)
        val ok = if (manual) manualConnect() else autoConnect()
        if (intent.sessionId != currentSession) return@withLock
        if (ok) {
            _state.value = ReconnectState.CONNECTED
            failureCount = 0
            // Успешное подключение — сбрасываем статус реконнекта, чтобы он не «прилипал» в UI.
            controller.setStatus(null)
            startHealth(currentSession)
        } else {
            _state.value = ReconnectState.FAILED
            scheduleReconnect(currentSession)
        }
    }

    suspend fun userDisconnect() = mutex.withLock {
        intent.sessionId++
        intent.shouldBeConnected = false
        cancelReconnect()
        cancelHealth()
        disconnectAction()
        _state.value = ReconnectState.IDLE
        controller.setStatus(null)
    }

    private suspend fun scheduleReconnect(currentSession: Long) {
        if (!intent.shouldBeConnected || intent.sessionId != currentSession) return
        cancelReconnect()
        // Потолок backoff — последний элемент политики (60 с), а не отдельное значение.
        val baseDelay = backoffBase[failureCount.coerceAtMost(backoffBase.lastIndex)]
        failureCount = (failureCount + 1).coerceAtMost(backoffBase.size)
        val jitter = (baseDelay * 0.2 * (Random.nextDouble() * 2 - 1)).toLong()
        val delaySec = (baseDelay + jitter).coerceAtLeast(1)
        _state.value = ReconnectState.RECONNECT_WAIT
        controller.setStatus("Повтор через ${delaySec}с…")
        reconnectJob = scope.launch {
            delay(delaySec * 1000L)
            if (!intent.shouldBeConnected || intent.sessionId != currentSession) return@launch
            _state.value = ReconnectState.RECONNECTING
            mutex.withLock {
                if (intent.sessionId != currentSession) return@withLock
                val ok = autoConnect()
                if (intent.sessionId != currentSession) return@withLock
                if (ok) {
                    _state.value = ReconnectState.CONNECTED
                    failureCount = 0
                    startHealth(currentSession)
                } else {
                    scheduleReconnect(currentSession)
                }
            }
        }
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun startHealth(currentSession: Long) {
        cancelHealth()
        healthJob = scope.launch {
            var consecutiveFailures = 0
            while (intent.shouldBeConnected && intent.sessionId == currentSession) {
                delay(30000)
                if (!intent.shouldBeConnected || intent.sessionId != currentSession) break
                val healthy = checkTunnel()
                if (healthy) {
                    consecutiveFailures = 0
                    _state.value = ReconnectState.CONNECTED
                    // Туннель восстановился — снимаем деградированный статус.
                    controller.markDegraded(false)
                } else {
                    consecutiveFailures++
                    if (consecutiveFailures >= 2) {
                        // Туннель сохраняем, показываем «интернет не подтверждён».
                        controller.markDegraded(true)
                    }
                    if (consecutiveFailures >= 3) {
                        _state.value = ReconnectState.DEGRADED
                        scheduleReconnect(currentSession)
                        break
                    }
                    _state.value = ReconnectState.DEGRADED
                }
            }
        }
    }
}
