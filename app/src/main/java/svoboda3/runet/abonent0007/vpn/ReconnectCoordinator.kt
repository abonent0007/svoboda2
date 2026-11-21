package svoboda3.runet.abonent0007.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import svoboda3.runet.abonent0007.data.Prefs
import svoboda3.runet.abonent0007.net.NetProbe
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
    private val manualConnect: suspend () -> ConnectionResult,
    private val autoConnect: suspend (Boolean, Boolean) -> ConnectionResult,
    private val disconnectAction: () -> Unit,
) {
    private val mutex = Mutex()
    private val intent = ConnectionIntent()
    private val _state = MutableStateFlow(ReconnectState.IDLE)
    val state: StateFlow<ReconnectState> = _state.asStateFlow()
    private val jobs = SingleJobTracker()

    @Volatile
    var testingMode: Boolean = false

    val shouldBeConnected: Boolean get() = intent.shouldBeConnected

    private var failureCount = 0
    private var latestCaps: NetworkCapabilities? = null
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val backoffBase = listOf(2L, 5, 10, 20, 40, 60)

    suspend fun <T> withVpnLock(block: suspend () -> T): T = mutex.withLock { block() }

    fun cancelHealth() {
        jobs.clearAllHealth()
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
        VpnLog.i("onNetworkEvent entry")
        scope.launch {
            delay(2000)
            if (!intent.shouldBeConnected) {
                VpnLog.i("onNetworkEvent skip=incoming_shouldBeConnected=false")
                return@launch
            }
            val currentSession = intent.sessionId
            if (!isNetworkAvailable()) {
                VpnLog.i("onNetworkEvent skip=network_unavailable session=$currentSession")
                _state.value = ReconnectState.WAITING_FOR_NETWORK
                controller.setStatus("Ожидание сети…")
                return@launch
            }
            if (isCaptivePortal()) {
                VpnLog.i("onNetworkEvent skip=captive_portal session=$currentSession")
                _state.value = ReconnectState.WAITING_FOR_NETWORK
                controller.setStatus("Требуется авторизация в сети")
                return@launch
            }
            VpnLog.i("onNetworkEvent proceed=checkTunnel req=false session=$currentSession")
            mutex.withLock {
                if (intent.sessionId != currentSession) {
                    VpnLog.i("onNetworkEvent skip=sessionChanged session=$currentSession now=${intent.sessionId}")
                    return@withLock
                }
                val healthy = checkTunnel()
                if (healthy) {
                    _state.value = ReconnectState.CONNECTED
                    failureCount = 0
                    // Соединение восстановлено — убираем устаревший обратный отсчёт.
                    controller.setStatus(null)
                    VpnLog.i("onNetworkEvent result=healthy session=$currentSession state=CONNECTED")
                } else {
                    VpnLog.i("onNetworkEvent result=unhealthy session=$currentSession call=scheduleReconnect")
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

    private fun checkTunnel(): Boolean = runBlocking {
        // Во время бенчмарка стратегий health-проверка не должна вмешиваться:
        // ciadpi перезапускается, и контрольный SOCKS-проб был бы ложным сигналом.
        if (testingMode) {
            VpnLog.i("checkTunnel result=bypass_testingMode")
            return@runBlocking true
        }
        val sessionId = intent.sessionId
        val port = controller.dpiProbePort
        if (port != null) {
            val exit = NetProbe.fetchExitViaSocks(port)
            VpnLog.i("checkTunnel dpi port=$port session=$sessionId result=${if (exit != null) "healthy" else "unhealthy"}")
            return@runBlocking exit != null
        }
        val hs = controller.lastHandshake()
        if (hs <= 0) {
            VpnLog.i("checkTunnel session=$sessionId result=unhealthy reason=handshake_not_ready handshake=$hs")
            return@runBlocking false
        }
        // Проба обязана идти через TUN: приложение исключено из VPN, поэтому прямой
        // fetchExit() измерял бы мимо туннеля (ложный healthy).
        val exit = NetProbe.fetchExitBound(context)
        val ok = exit != null
        VpnLog.i("checkTunnel session=$sessionId handshake=$hs result=${if (ok) "healthy" else "unhealthy"} exit=${exit?.ip}")
        ok
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
        val result = try {
            VpnLog.i("userConnect begin manual=$manual session=$currentSession phase=${_state.value}")
            if (manual) manualConnect() else autoConnect(false, false)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // Управляющее событие: отмена — не сетевая ошибка и не повод перетирать статус.
            VpnLog.i("userConnect cancelled manual=$manual session=$currentSession")
            throw ce
        } catch (t: Throwable) {
            // Никаких exception.toString() в пользовательском статусе.
            VpnLog.e("userConnect internal manual=$manual session=$currentSession class=${t::class.java.simpleName}", t)
            ConnectionResult.NoWorkingRoute(listOf("internal: ${t::class.java.simpleName}"))
        }
        if (intent.sessionId != currentSession) {
            VpnLog.i("userConnect stale result session=$currentSession now=${intent.sessionId}")
            return@withLock
        }
        VpnLog.i("userConnect result=${result::class.java.simpleName} session=$currentSession phase=${_state.value}")
        when (result) {
            is ConnectionResult.ConnectedHealthy -> {
                _state.value = ReconnectState.CONNECTED
                failureCount = 0
                controller.setStatus(null)
                startHealth(currentSession)
            }
            is ConnectionResult.ReconnectDeferred -> {
                // Отложенная попытка — НЕ показатель подключения.
                // По смыслу ветки туннель уже здоров (anti-bounce сработал только на HEALTHY);
                // оставляем как есть, но если экран завис на CONNECTING — возвращаемся в CONNECTED.
                // DEGRADED-туннель сюда не попадает: для него hasHealthySession=false и запуск идёт через перебор.
                if (_state.value == ReconnectState.CONNECTING && controller.phase.value == VpnPhase.CONNECTED) {
                    _state.value = ReconnectState.CONNECTED
                    controller.setStatus(null)
                }
                return@withLock
            }
            is ConnectionResult.TransportUpValidating -> {
                _state.value = ReconnectState.CONNECTING
                controller.setStatus("Проверяю данные…")
            }
            is ConnectionResult.NoWorkingRoute,
            is ConnectionResult.CandidateDegraded,
            is ConnectionResult.FailoverInProgress -> {
                _state.value = ReconnectState.FAILED
                controller.setStatus("Нет рабочего маршрута")
                if (!manual) {
                    // Авто-подключение (не пользовательская кнопка): честный backoff.
                    scheduleReconnect(currentSession)
                }
            }
            is ConnectionResult.UserDisconnected -> {
                _state.value = ReconnectState.IDLE
                controller.setStatus(null)
            }
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
        if (!intent.shouldBeConnected || intent.sessionId != currentSession) {
            VpnLog.i("scheduleReconnect skip shouldBeConnected=${intent.shouldBeConnected} session=$currentSession now=${intent.sessionId}")
            return
        }
        // Снимок здоровья на случай, если к моменту ветки FAILED/RECONNECT кто-то
        // уже успел заменить health-job (не удаляем чужой/новый).
        val expectedHealth = jobs.snapshotHealth()
        cancelReconnect()
        // Потолок backoff — последний элемент политики (60 с), а не отдельное значение.
        val baseDelay = backoffBase[failureCount.coerceAtMost(backoffBase.lastIndex)]
        failureCount = (failureCount + 1).coerceAtMost(backoffBase.size)
        val jitter = (baseDelay * 0.2 * (Random.nextDouble() * 2 - 1)).toLong()
        val delaySec = (baseDelay + jitter).coerceAtLeast(1)
        VpnLog.i("scheduleReconnect session=$currentSession baseDelay=${baseDelay}s failureCount=$failureCount delay=${delaySec}s state=RECONNECT_WAIT")
        _state.value = ReconnectState.RECONNECT_WAIT
        controller.setStatus("Повтор через ${delaySec}с…")
        jobs.setReconnect(scope.launch {
            delay(delaySec * 1000L)
            if (!intent.shouldBeConnected || intent.sessionId != currentSession) return@launch
            _state.value = ReconnectState.RECONNECTING
            VpnLog.i("scheduleReconnect tick session=$currentSession now=${intent.sessionId} shouldBeConnected=${intent.shouldBeConnected}")
            try {
                mutex.withLock {
                    if (intent.sessionId != currentSession) {
                        VpnLog.i("scheduleReconnect stale session=$currentSession now=${intent.sessionId}")
                        return@withLock
                    }
                    val result = try {
                        // Failover от доказанно нерабочего маршрута: антидребезг не применяется,
                        // но quarantine — применяется (автоматика не долбит провалившиеся endpoint).
                        autoConnect(true, false)
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        VpnLog.i("scheduleReconnect cancelled session=$currentSession")
                        throw ce
                    } catch (t: Throwable) {
                        android.util.Log.e("SvobodaVpn", "scheduleReconnect internal session=$currentSession", t)
                        ConnectionResult.NoWorkingRoute(listOf("internal: ${t::class.java.simpleName}"))
                    }
                    if (intent.sessionId != currentSession) {
                        VpnLog.i("scheduleReconnect stale autoConnect session=$currentSession now=${intent.sessionId}")
                        return@withLock
                    }
                    VpnLog.i("scheduleReconnect result=${result::class.java.simpleName} session=$currentSession")
                    when (result) {
                        is ConnectionResult.ConnectedHealthy -> {
                            _state.value = ReconnectState.CONNECTED
                            failureCount = 0
                            controller.setStatus(null)
                            startHealth(currentSession)
                        }
                        is ConnectionResult.NoWorkingRoute -> {
                            // Все в quarantine — ждём истечения окна backoff'ом, а не вечным FAILED.
                            if (result.quarantine) {
                                scheduleReconnect(currentSession)
                            } else {
                                expectedHealth?.let(jobs::clearHealthIfCurrent)
                                _state.value = ReconnectState.FAILED
                                controller.setStatus("Нет рабочего маршрута")
                            }
                        }
                        is ConnectionResult.CandidateDegraded,
                        is ConnectionResult.FailoverInProgress -> {
                            expectedHealth?.let(jobs::clearHealthIfCurrent)
                            _state.value = ReconnectState.FAILED
                            // Честный терминал: дальнейших попыток не планируем.
                            controller.setStatus("Нет рабочего маршрута")
                        }
                        is ConnectionResult.ReconnectDeferred -> {
                            // Отложенная попытка — остаёмся в DEGRADED, планируем следующую попытку.
                            scheduleReconnect(currentSession)
                        }
                        is ConnectionResult.TransportUpValidating,
                        is ConnectionResult.UserDisconnected -> {
                            // Unknown here: продолжаем backoff.
                            scheduleReconnect(currentSession)
                        }
                    }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                // Отмена — управляющее событие: никаких «ошибок» в статусе.
                return@launch
            }
        })
    }

    private fun cancelReconnect() {
        jobs.clearAllReconnect()
    }

    private fun startHealth(currentSession: Long) {
        VpnLog.i("startHealth entry session=$currentSession")
        cancelHealth()
        jobs.setHealth(scope.launch {
            var consecutiveFailures = 0
            VpnLog.i("startHealth jobLaunched session=$currentSession conFail=$consecutiveFailures")
            while (intent.shouldBeConnected && intent.sessionId == currentSession) {
                delay(30000)
                if (!intent.shouldBeConnected || intent.sessionId != currentSession) {
                    VpnLog.i("startHealth loopBreak session=$currentSession shouldBeConnected=${intent.shouldBeConnected} sessionNow=${intent.sessionId}")
                    break
                }
                val healthy = checkTunnel()
                VpnLog.i("startHealth tick session=$currentSession healthy=$healthy conFail=$consecutiveFailures")
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
                        VpnLog.i("startHealth triggerDegraded3 session=$currentSession scheduleReconnect")
                        _state.value = ReconnectState.DEGRADED
                        scheduleReconnect(currentSession)
                        break
                    }
                    _state.value = ReconnectState.DEGRADED
                }
            }
        })
    }
}
