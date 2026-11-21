package svoboda.runet.abonent0007.dpi

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import svoboda.runet.abonent0007.data.Prefs
import svoboda.runet.abonent0007.split.SplitTunnelManager
import svoboda.runet.abonent0007.net.NetProbe
import svoboda.runet.abonent0007.dpi.dpiStrategyById
import svoboda.runet.abonent0007.dpi.DPI_DEFAULT_STRATEGY_ID
import svoboda.runet.abonent0007.vpn.VpnController
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * DPI-движок «Свободы 2.0» (без root).
 *
 * Архитектура:
 *   TUN (владелец — GoBackend.VpnService) → HEV (tun2socks, in-process JNI)
 *     → SOCKS5 127.0.0.1:<port> → ciadpi (отдельный процесс, стратегия DPI)
 *     → Internet
 *
 * Собственный пакет приложения исключён из VPN в DPI-режиме — иначе
 * исходящие сокеты ciadpi завернулись бы в TUN (рекурсия). Проба связи
 * идёт через SOCKS5 в обход TUN — честное доказательство, что обход DPI работает.
 */
class DpiEngine(
    private val context: Context,
    private val prefs: Prefs,
    private val splitManager: SplitTunnelManager,
    private val vpn: VpnController,
) {
    companion object {
        private const val TAG = "SvobodaDpiEngine"
        private const val SOCKS_HOST = "127.0.0.1"
        private const val SOCKS_WAIT_ATTEMPTS = 24
        private const val SOCKS_WAIT_DELAY_MS = 250L
    }

    @Volatile private var ciadpiProcess: Process? = null
    @Volatile private var started = false

    /** Аргументы ciadpi по идентификатору стратегии (BDP-00…BDP-12) или пользовательская строка. */
    private fun strategyArgs(): Array<String> {
        val custom = prefs.dpiCustomStrategy.trim()
        if (custom.isNotBlank()) {
            // Очень узко: только слова без пробелов — без кавычек, без спецсимволов.
            return custom.split(Regex("\\s+")).filter { it.isNotBlank() }.toTypedArray()
        }
        val strategy = dpiStrategyById(prefs.dpiStrategyId)?.args
            ?: dpiStrategyById(DPI_DEFAULT_STRATEGY_ID)!!.args
        return strategy.split(' ').filter { it.isNotBlank() }.toTypedArray()
    }

    /** YAML-конфиг HEV: тюн2сокс → SOCKS5 на 127.0.0.1:port. */
    private fun hevConfig(port: Int, mtu: Int): String = buildString {
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
    }

    /** Доказательство через SOCKS5 + получение exit info (IP/страна выхода через обход). */
    private suspend fun socks5Probe(port: Int): NetProbe.ExitInfo? {
        val exit = NetProbe.fetchExitViaSocks(port)
        if (exit == null) Log.w(TAG, "SOCKS5 probe failed")
        return exit
    }

    private fun waitForSocks5(port: Int): Boolean {
        repeat(SOCKS_WAIT_ATTEMPTS) {
            val alive = ciadpiProcess?.isAlive == true
            if (!alive) {
                Log.e(TAG, "ciadpi process died while waiting for SOCKS5")
                return false
            }
            try {
                Socket().use { s -> s.connect(InetSocketAddress(SOCKS_HOST, port), 250) }
                return true
            } catch (_: Exception) {
                // ещё не готов
            }
            Thread.sleep(SOCKS_WAIT_DELAY_MS)
        }
        return false
    }

    private fun startCiadpi(argsOverride: Array<String>? = null): Boolean {
        val port = prefs.dpiSocksPort.coerceIn(1, 65535)
        val binary = java.io.File(context.applicationInfo.nativeLibraryDir, "libbyedpi.so")
        if (!binary.exists()) {
            Log.e(TAG, "libbyedpi.so not found in ${context.applicationInfo.nativeLibraryDir}")
            return false
        }
        val args = buildList {
            addAll(argsOverride ?: strategyArgs())
            add("-p"); add(port.toString())
        }
        Log.d(TAG, "starting ciadpi: ${binary.absolutePath} ${args.joinToString(" ")}")
        return try {
            val process = ProcessBuilder(listOf(binary.absolutePath) + args)
                .redirectErrorStream(true)
                .start()
            ciadpiProcess = process
            waitForSocks5(port)
        } catch (e: Exception) {
            Log.e(TAG, "ciadpi start failed", e)
            false
        }
    }

    private fun stopCiadpi() {
        val p = ciadpiProcess ?: return
        ciadpiProcess = null
        try {
            p.destroy()
            if (!p.waitFor(2, TimeUnit.SECONDS)) {
                p.destroyForcibly()
            }
        } catch (e: Exception) {
            Log.w(TAG, "ciadpi stop error: ${e.message}")
        }
    }

    /**
     * Тест стратегии без туннеля: ciadpi поднимается отдельным процессом на время пробы.
     * [probe] получает порт SOCKS и должна проверить ресурс через него.
     * При активной DPI-сессии не используется (конфликт порта) — возвращает false.
     */
    suspend fun testStrategy(args: Array<String>, probe: suspend (Int) -> Boolean): Boolean {
        if (started) {
            Log.w(TAG, "testStrategy skipped: engine already started")
            return false
        }
        val port = prefs.dpiSocksPort.coerceIn(1, 65535)
        if (!startCiadpi(args)) return false
        return try {
            kotlinx.coroutines.delay(400)
            probe(port)
        } finally {
            stopCiadpi()
        }
    }

    /** Полный цикл: ciadpi → TUN/HEV → SOCKS5-проба → CONNECTED (или DEGRADED при ошибке, туннель сохраняем). */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (started) {
            Log.w(TAG, "start() called while already started")
            return@withContext true
        }

        // 1. ciadpi
        if (!startCiadpi()) {
            Log.e(TAG, "DPI engine: ciadpi failed")
            stop()
            return@withContext false
        }

        // 2. TUN через GoBackend в DPI-режиме (handoff fd → HEV внутри VpnController/GoBackend)
        val config = DpiConfig.build(prefs)
        val ok = vpn.connectDpi(config)
        if (!ok) {
            Log.e(TAG, "DPI engine: TUN bring-up failed")
            stop()
            return@withContext false
        }

        // 3. Доказательство через SOCKS5 (не через TUN — собственный пакет исключён из VPN)
        val port = prefs.dpiSocksPort.coerceIn(1, 65535)
        val exit = socks5Probe(port)
        vpn.setDpiProbePort(port)
        vpn.setExitInfo(exit)
        if (exit == null) {
            Log.e(TAG, "DPI engine: SOCKS5 probe failed (tunnel stays up, reporting DEGRADED)")
            // Туннель сохраняем: пользователь может диагностировать, а не получать «ERROR + оборванный VPN».
            vpn.markDegraded(true)
            vpn.setError("Обход DPI не работает — проверьте стратегию в настройках DPI")
            started = true
            return@withContext true
        }
        vpn.markConnected()
        started = true
        true
    }

    fun stop() {
        // HEV закрывает TUN fd сам при nativeStop; здесь ciadpi + флаги.
        vpn.setDpiProbePort(null)
        vpn.disconnectDpi()
        stopProcess()
    }

    /** Остановить только ciadpi-процесс (идемпотентно). */
    fun stopProcess() {
        stopCiadpi()
        started = false
    }

    fun isRunning(): Boolean = started && ciadpiProcess?.isAlive == true && HevBridge.nativeIsRunning()

    /**
     * Перезапуск ТОЛЬКО ciadpi с заданной стратегией (Android VPNService и HEV не трогаем).
     * Используется бенчмарком стратегий: смена стратегии не должна рвать VPN.
     */
    suspend fun restartWithStrategy(strategyId: String): Boolean {
        val strategy = dpiStrategyById(strategyId) ?: return false
        stopCiadpi()
        Thread.sleep(400)
        val ok = startCiadpi(strategy.args.split(' ').filter { it.isNotBlank() }.toTypedArray())
        Log.d(TAG, "restartWithStrategy($strategyId) -> $ok")
        return ok
    }

    /** Перезапуск ciadpi с текущей конфигурацией (пользовательская строка приоритетнее каталога). */
    suspend fun restartWithCurrentStrategy(): Boolean {
        stopCiadpi()
        Thread.sleep(400)
        return startCiadpi()
    }

    /** Готовность локального SOCKS5 (порт принимает соединения). */
    fun isSocksReady(): Boolean {
        val port = prefs.dpiSocksPort.coerceIn(1, 65535)
        if (ciadpiProcess?.isAlive != true) return false
        return try {
            java.net.Socket().use { s ->
                s.connect(java.net.InetSocketAddress("127.0.0.1", port), 500)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
