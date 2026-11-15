package svoboda.runet.abonent0007.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.amnezia.awg.backend.Backend
import org.amnezia.awg.backend.BackendException
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import svoboda.runet.abonent0007.split.SplitTunnelManager
import svoboda.runet.abonent0007.split.SplitTunnelStore
import svoboda.runet.abonent0007.data.Prefs
import svoboda.runet.abonent0007.net.NetProbe
import svoboda.runet.abonent0007.warp.ConfigNormalizer
import java.io.BufferedReader
import java.io.StringReader

/** Фазы подключения (CONNECTED только после handshake + проброса). */
enum class VpnPhase {
    IDLE, PREPARING, CONNECTING, VERIFYING, CONNECTED, DEGRADED, ERROR,
}

/**
 * Контроллер AmneziaWG-туннеля поверх GoBackend (Apache-2.0).
 *
 * GoBackend сам создаёт VpnService/TUN и защищает сокеты; здесь только
 * жизненный цикл, верификация, мониторинг пинга и проброс состояния в UI.
 */
class VpnController(
    context: Context,
    private val prefs: Prefs,
) {

    companion object {
        private const val TUNNEL_NAME = "svoboda2"
        private const val HANDSHAKE_TIMEOUT_MS = 15_000L
        private const val HANDSHAKE_POLL_MS = 1_000L
        private const val PING_HISTORY_CAP = 60
    }

    private val backend: Backend = GoBackend(context.applicationContext)
    private val appContext = context.applicationContext
    private val splitManager = SplitTunnelManager(appContext, SplitTunnelStore(appContext), prefs)

    /** Идентификатор активного профиля (для per-profile split-правил). */
    var currentProfileId: String = ""

    private val tunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME

        override fun onStateChange(newState: Tunnel.State) {
            // Состояние транслируем через фазы ниже.
        }
    }

    private val _phase = MutableStateFlow(VpnPhase.IDLE)
    val phase: StateFlow<VpnPhase> = _phase.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** IP и страна выхода (после проверки через туннель). */
    private val _exit = MutableStateFlow<NetProbe.ExitInfo?>(null)
    val exit: StateFlow<NetProbe.ExitInfo?> = _exit.asStateFlow()

    private val _lastPing = MutableStateFlow<Int?>(null)
    val lastPing: StateFlow<Int?> = _lastPing.asStateFlow()

    private val _pingHistory = MutableStateFlow<List<Int>>(emptyList())
    val pingHistory: StateFlow<List<Int>> = _pingHistory.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    private var monitorJob: Job? = null

    /** Текущий применённый конфиг (для диагностики/рестарта). */
    var activeConfigText: String? = null
        private set

    fun prepareVpn(context: Context): Intent? = VpnService.prepare(context)

    val isConnected: Boolean get() = _phase.value == VpnPhase.CONNECTED

    /** Запускает фоновую петлю пинга (один раз из Application). */
    fun startMonitor(scope: CoroutineScope) {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (_phase.value == VpnPhase.CONNECTED && prefs.pingGraphEnabled) {
                    val ping = NetProbe.tcpPing()
                    _lastPing.value = ping
                    _pingHistory.update { history ->
                        (history + ping).takeLast(PING_HISTORY_CAP)
                    }
                }
                delay(prefs.pingIntervalMs.toLong())
            }
        }
    }

    suspend fun connect(configText: String): Boolean = withContext(Dispatchers.IO) {
        _phase.value = VpnPhase.PREPARING
        _error.value = null
        _exit.value = null
        _pingHistory.value = emptyList()
        _lastPing.value = null
        runCatching {
            val normalized = ConfigNormalizer.normalize(
                configText,
                ipv6Enabled = prefs.ipv6Enabled,
                mtuOverride = prefs.tunnelMtu,
                dnsOverride = prefs.dnsServers.ifBlank { null },
            )
            val config = Config.parse(
                BufferedReader(
                    StringReader(applySplitTunnel(normalized)),
                ),
            )
            activeConfigText = configText
            _phase.value = VpnPhase.CONNECTING
            backend.setState(tunnel, Tunnel.State.UP, config)

            // Ожидание handshake.
            val deadline = System.currentTimeMillis() + HANDSHAKE_TIMEOUT_MS
            var handshakeUp = false
            while (System.currentTimeMillis() < deadline) {
                if (backend.getLastHandshake(tunnel) > 0) {
                    handshakeUp = true
                    break
                }
                delay(HANDSHAKE_POLL_MS)
            }
            if (!handshakeUp) {
                backend.setState(tunnel, Tunnel.State.DOWN, null)
                _phase.value = VpnPhase.ERROR
                _error.value = "Нет ответа сервера (handshake timeout)"
                return@withContext false
            }

            // Proof: реальный трафик через туннель.
            _phase.value = VpnPhase.VERIFYING
            val exit = NetProbe.fetchExit()
            if (exit == null) {
                backend.setState(tunnel, Tunnel.State.DOWN, null)
                _phase.value = VpnPhase.ERROR
                _error.value = "Туннель поднят, но трафик не проходит"
                return@withContext false
            }
            _exit.value = exit
            _phase.value = VpnPhase.CONNECTED
            true
        }.getOrElse { e ->
            android.util.Log.e("SvobodaVpn", "connect failed", e)
            backend.runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
            _phase.value = VpnPhase.ERROR
            _error.value = when (e) {
                is BackendException -> "${e.javaClass.simpleName}(${e.reason}): ${e.message ?: ""}"
                else -> "${e.javaClass.simpleName}: ${e.message ?: "Ошибка подключения"}"
            }
            false
        }
    }

    fun disconnect() {
        runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
        activeConfigText = null
        _exit.value = null
        _lastPing.value = null
        _phase.value = VpnPhase.IDLE
    }

    /** Кнопка «Проверить связь»: IP/страна + три пинга. */
    suspend fun runTest() {
        _testResult.value = "Проверка…"
        val exit = NetProbe.fetchExit()
        val pings = (1..3).map { NetProbe.tcpPing() }
        val ok = pings.filter { it >= 0 }
        _testResult.value = buildString {
            if (exit != null) {
                append("IP ${exit.ip}")
                if (exit.country.isNotBlank()) {
                    append(" · ${NetProbe.countryLabel(exit.country)}")
                }
            } else {
                append("IP недоступен")
            }
            if (ok.isNotEmpty()) {
                append(" · ping ${ok.min()}/${ok.sum() / ok.size}/${ok.max()} мс")
            } else {
                append(" · пинг не проходит")
            }
        }
    }

    /** Текст статуса для UI (обновления, операции). */
    fun setStatus(text: String?) { _testResult.value = text }

    /** Последний handshake (мс от эпохи); -1 если нет. */
    fun lastHandshake(): Long = runCatching { backend.getLastHandshake(tunnel) }.getOrDefault(-1L)

    /**
     * Вставляет в [Interface] список приложений, идущих мимо VPN.
     * VpnService.Builder.addDisallowedApplication применяется в GoBackend.
     */
    private fun applySplitTunnel(config: String): String {
        val injection = splitManager.buildInjection(currentProfileId.ifBlank { "default" })
        if (injection == null) return config
        val lines = config.lines().toMutableList()
        val ifaceIndex = lines.indexOfFirst { it.trim().equals("[Interface]", ignoreCase = true) }
        if (ifaceIndex < 0) return config
        lines.add(ifaceIndex + 1, injection)
        android.util.Log.d("SvobodaVpn", "split tunnel: $injection")
        return lines.joinToString("\n")
    }
}
