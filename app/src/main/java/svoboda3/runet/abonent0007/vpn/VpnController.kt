package svoboda3.runet.abonent0007.vpn

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
import svoboda3.runet.abonent0007.split.SplitTunnelManager
import svoboda3.runet.abonent0007.split.SplitTunnelMode
import svoboda3.runet.abonent0007.split.SplitTunnelStore
import svoboda3.runet.abonent0007.state.ConnectionStateRepository
import svoboda3.runet.abonent0007.state.TunnelTransportPhase
import svoboda3.runet.abonent0007.state.VpnSessionPhase
import svoboda3.runet.abonent0007.data.Prefs
import svoboda3.runet.abonent0007.net.NetProbe
import svoboda3.runet.abonent0007.warp.ConfigNormalizer
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
    private val stateRepo: ConnectionStateRepository? = null,
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

    /** Вызывается на старте disconnect() — позволяет DpiEngine остановить ciadpi до закрытия TUN. */
    var onDpiDisconnect: (() -> Unit)? = null

    /** Порт локального SOCKS5 (ciadpi) в DPI-режиме; null вне DPI. */
    @Volatile
    var dpiProbePort: Int? = null
        private set

    fun prepareVpn(context: Context): Intent? = VpnService.prepare(context)

    val isConnected: Boolean
        get() = _phase.value == VpnPhase.CONNECTED || _phase.value == VpnPhase.DEGRADED

    /** Явно переводит фазу в/из DEGRADED (используется health-монитором). */
    fun markDegraded(degraded: Boolean) {
        android.util.Log.i("SvobodaVpn", "markDegraded degraded=$degraded oldPhase=${_phase.value}")
        _phase.value = if (degraded) VpnPhase.DEGRADED else VpnPhase.CONNECTED
        stateRepo?.setVpn(if (degraded) VpnSessionPhase.DEGRADED else VpnSessionPhase.CONNECTED)
        stateRepo?.setTransport(if (degraded) TunnelTransportPhase.DEGRADED else TunnelTransportPhase.FLOWING)
    }

    /** Запускает фоновую петлю пинга (один раз из Application). */
    fun startMonitor(scope: CoroutineScope) {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                if ((_phase.value == VpnPhase.CONNECTED || _phase.value == VpnPhase.DEGRADED) && prefs.pingGraphEnabled) {
                    val ping = dpiProbePort?.let { NetProbe.socksPing(it) }
                        ?: NetProbe.tcpPingBound(appContext)
                    // NetProbe возвращает -1 при ошибке: не показываем «-1 мс» и не портим график.
                    _lastPing.value = ping.takeIf { it >= 0 }
                    if (ping >= 0) {
                        _pingHistory.update { history ->
                            (history + ping).takeLast(PING_HISTORY_CAP)
                        }
                    }
                }
                delay(prefs.pingIntervalMs.toLong())
            }
        }
    }

    suspend fun connect(configText: String): Boolean = withContext(Dispatchers.IO) {
        android.util.Log.i("SvobodaVpn", "connect PREPARING start")
        _phase.value = VpnPhase.PREPARING
        stateRepo?.setVpn(VpnSessionPhase.PREPARING)
        stateRepo?.setTransport(TunnelTransportPhase.IDLE)
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
            android.util.Log.i("SvobodaVpn", "connect UP handshake poll start")
            _phase.value = VpnPhase.CONNECTING
            stateRepo?.setVpn(VpnSessionPhase.CONNECTING)
            stateRepo?.setTransport(TunnelTransportPhase.CONNECTING)
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
                android.util.Log.w("SvobodaVpn", "connect handshake_timeout_down")
                backend.setState(tunnel, Tunnel.State.DOWN, null)
                _phase.value = VpnPhase.ERROR
            stateRepo?.setVpn(VpnSessionPhase.ERROR)
            stateRepo?.setTransport(TunnelTransportPhase.FAILED)
                _error.value = "Нет ответа сервера (handshake timeout)"
                return@withContext false
            }
            android.util.Log.i("SvobodaVpn", "connect handshake_up")

            // Proof: реальный трафик через туннель.
            // Одной неудачи мало: пробуем до 3 раз. При первой ошибке туннель НЕ гасим —
            // Cloudflare/маршрут могут быть временно недоступны.
            _phase.value = VpnPhase.VERIFYING
            stateRepo?.setVpn(VpnSessionPhase.ESTABLISHING)
            stateRepo?.setTransport(TunnelTransportPhase.HANDSHAKING)
            android.util.Log.i("SvobodaVpn", "connect verifying_start")
            suspend fun tryFetch(): NetProbe.ExitInfo? {
                var r = NetProbe.fetchExit()
                repeat(2) { attempt ->
                    if (r == null) {
                        delay(if (attempt == 0) 2_000L else 5_000L)
                        r = NetProbe.fetchExit()
                    }
                }
                return r
            }
            val exit = tryFetch()
            if (exit == null) {
                // Handshake состоялся — туннель остаётся живым: «туннель есть, интернета нет».
                _phase.value = VpnPhase.DEGRADED
                stateRepo?.setVpn(VpnSessionPhase.DEGRADED)
                stateRepo?.setTransport(TunnelTransportPhase.DEGRADED)
                _error.value = "Туннель установлен, интернет не подтверждён"
                android.util.Log.w("SvobodaVpn", "connect degraded_exit_null")
                return@withContext true
            }
            android.util.Log.i("SvobodaVpn", "connect connected exit=${exit.ip} country=${exit.country}")
            _exit.value = exit
            _phase.value = VpnPhase.CONNECTED
            stateRepo?.setVpn(VpnSessionPhase.CONNECTED)
            stateRepo?.setTransport(TunnelTransportPhase.FLOWING)
            true
        }.getOrElse { e ->
            android.util.Log.e("SvobodaVpn", "connect failed", e)
            backend.runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
            _phase.value = VpnPhase.ERROR
            stateRepo?.setVpn(VpnSessionPhase.ERROR)
            stateRepo?.setTransport(TunnelTransportPhase.FAILED)
            _error.value = when (e) {
                is BackendException -> "${e.javaClass.simpleName}(${e.reason}): ${e.message ?: ""}"
                else -> "${e.javaClass.simpleName}: ${e.message ?: "Ошибка подключения"}"
            }
            false
        }
    }

    /** Подготовить одноразовую DPI-сессию: TUN fd уходит в HEV вместо amneziawg. */
    private fun prepareDpiSession(): GoBackend.DpiSession {
        val prefs = Prefs(appContext)
        val port = prefs.dpiSocksPort.coerceIn(1, 65535)
        val mtu = prefs.tunnelMtu.coerceIn(1200, 1480)
        val mapDns = prefs.dpiMapDns
        val yaml = buildString {
            appendLine("misc:")
            appendLine("  log-level: 'warn'")
            appendLine("tunnel:")
            appendLine("  name: 'svoboda-dpi'")
            appendLine("  mtu: $mtu")
            appendLine("  ipv4: '192.0.2.1'")
            appendLine("socks5:")
            appendLine("  address: '127.0.0.1'")
            appendLine("  port: $port")
            appendLine("  udp: 'udp'")
            if (mapDns) {
                appendLine("mapdns:")
                appendLine("  address: 198.18.0.2")
                appendLine("  port: 53")
                appendLine("  network: 100.64.0.0")
                appendLine("  netmask: 255.192.0.0")
                appendLine("  cache-size: 10000")
            }
        }
        return object : GoBackend.DpiSession {
            override fun onDpiTunFd(fd: Int) {
                HevHolder.nativeStart(yaml, fd)
            }
            override fun onDpiStop() {
                HevHolder.nativeStop()
            }
        }
    }

    /** Прокси к HevBridge, чтобы не тянуть объект в VpnController до инициализации JNI. */
    object HevHolder {
        fun nativeStart(config: String, fd: Int) { runCatching { svoboda3.runet.abonent0007.dpi.HevBridge.nativeStart(config, fd) }
            .onFailure { android.util.Log.e("SvobodaVpn", "HEV nativeStart failed", it) } }
        fun nativeStop() { runCatching { svoboda3.runet.abonent0007.dpi.HevBridge.nativeStop() }
            .onFailure { android.util.Log.e("SvobodaVpn", "HEV nativeStop failed", it) } }
    }

    suspend fun connectDpi(configText: String): Boolean = withContext(Dispatchers.IO) {
        _phase.value = VpnPhase.PREPARING
        stateRepo?.setVpn(VpnSessionPhase.PREPARING)
        stateRepo?.setTransport(TunnelTransportPhase.IDLE)
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
            stateRepo?.setVpn(VpnSessionPhase.CONNECTING)
            stateRepo?.setTransport(TunnelTransportPhase.CONNECTING)

            val goBackend = backend as? GoBackend
                ?: throw IllegalStateException("DPI mode requires GoBackend implementation")
            goBackend.setDpiSession(prepareDpiSession())
            goBackend.setDpiMode(true)
            try {
                backend.setState(tunnel, Tunnel.State.UP, config)
            } catch (e: Exception) {
                goBackend.setDpiMode(false)
                goBackend.setDpiSession(null)
                throw e
            }
            // Рукопожатия в DPI-режиме нет: доказательство связи делает DpiEngine через SOCKS5.
            _phase.value = VpnPhase.VERIFYING
            stateRepo?.setVpn(VpnSessionPhase.ESTABLISHING)
            stateRepo?.setTransport(TunnelTransportPhase.FLOWING)
            true
        }.getOrElse { e ->
            android.util.Log.e("SvobodaVpn", "connectDpi failed", e)
            (backend as? GoBackend)?.setDpiMode(false)
            (backend as? GoBackend)?.setDpiSession(null)
            backend.runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
            _phase.value = VpnPhase.ERROR
            stateRepo?.setVpn(VpnSessionPhase.ERROR)
            stateRepo?.setTransport(TunnelTransportPhase.FAILED)
            _error.value = when (e) {
                is BackendException -> "${e.javaClass.simpleName}(${e.reason}): ${e.message ?: ""}"
                else -> "${e.javaClass.simpleName}: ${e.message ?: "Ошибка подключения"}"
            }
            false
        }
    }

    fun disconnectDpi() {
        onDpiDisconnect?.invoke()
        runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
        dpiProbePort = null
        activeConfigText = null
        _exit.value = null
        _lastPing.value = null
        _phase.value = VpnPhase.IDLE
        stateRepo?.setVpn(VpnSessionPhase.STOPPED)
        stateRepo?.setTransport(TunnelTransportPhase.CLOSED)
        (backend as? GoBackend)?.setDpiMode(false)
        (backend as? GoBackend)?.setDpiSession(null)
    }

    fun setError(message: String?) {
        _error.value = message
    }

    fun setDpiProbePort(port: Int?) {
        dpiProbePort = port
    }

    fun setExitInfo(info: NetProbe.ExitInfo?) {
        _exit.value = info
    }

    fun markConnected() {
        _phase.value = VpnPhase.CONNECTED
        stateRepo?.setVpn(VpnSessionPhase.CONNECTED)
        stateRepo?.setTransport(TunnelTransportPhase.FLOWING)
    }

    fun disconnect() {
        onDpiDisconnect?.invoke()
        runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
        dpiProbePort = null
        activeConfigText = null
        _exit.value = null
        _lastPing.value = null
        _phase.value = VpnPhase.IDLE
        stateRepo?.setVpn(VpnSessionPhase.STOPPED)
        stateRepo?.setTransport(TunnelTransportPhase.CLOSED)
    }

    /** Кнопка «Проверить связь»: честный HTTPS-выход через туннель + пинги через туннель. */
    suspend fun runTest() {
        _testResult.value = "Проверка…"
        val port = dpiProbePort
        // DPI — через локальный SOCKS (он и есть туннель); остальное — через VPN-сеть (tun).
        val exit = port?.let { NetProbe.fetchExitViaSocks(it) } ?: NetProbe.fetchExitBound(appContext)
        val pings = (1..3).map {
            port?.let { p -> NetProbe.socksPing(p) } ?: NetProbe.tcpPingBound(appContext)
        }
        val ok = pings.filter { it >= 0 }
        _testResult.value = buildString {
            if (exit != null) {
                append("IP ${exit.ip}")
                if (exit.country.isNotBlank()) {
                    append(" · ${NetProbe.countryLabel(exit.country)}")
                }
                append(" · HTTPS через туннель: ок")
            } else {
                append("IP недоступен · HTTPS через туннель: не работает")
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
     * Единая логика раздельного туннелирования для всех методов.
     * Источники: профиль split-tunnel (шторка/пресет), глобальный direct-список, списки «в туннель».
     * Android не позволяет одновременно addAllowedApplication и addDisallowedApplication,
     * поэтому отдаём ровно один ключ. Глобальный direct-список приоритетнее allowlist.
     */
    private fun applySplitTunnel(config: String): String {
        val profileId = currentProfileId.ifBlank { "default" }
        val cfg = splitManager.resolve(profileId)
        val own = "svoboda3.runet.abonent0007"
        val pm = appContext.packageManager
        fun installed(pkg: String) = runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess

        val direct = LinkedHashSet<String>()
        if (cfg.mode == SplitTunnelMode.ALL_EXCEPT_SELECTED) direct += cfg.packageNames
        direct += prefs.globalDirectApps

        val allow = LinkedHashSet<String>()
        if (cfg.mode == SplitTunnelMode.ONLY_SELECTED) allow += cfg.packageNames
        allow += prefs.dpiTunnelApps
        allow += prefs.warpTunnelApps

        allow -= direct
        allow -= own
        direct -= own

        val line = when {
            allow.any { installed(it) } ->
                "IncludedApplications = " + allow.filter { installed(it) }.joinToString(", ")
            direct.any { installed(it) } ->
                "ExcludedApplications = " + direct.filter { installed(it) }.joinToString(", ")
            else -> null
        } ?: return config

        val lines = config.lines().toMutableList()
        val ifaceIndex = lines.indexOfFirst { it.trim().equals("[Interface]", ignoreCase = true) }
        if (ifaceIndex < 0) return config
        lines.add(ifaceIndex + 1, line)
        android.util.Log.d("SvobodaVpn", "split tunnel: $line")
        return lines.joinToString("\n")
    }
}
