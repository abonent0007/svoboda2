p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\dpi\DpiEngine.kt'
s = open(p, encoding='utf-8').read()

# 1. startCiadpi(argsOverride) + public helpers
old = """    private fun startCiadpi(): Boolean {
        val port = prefs.dpiSocksPort.coerceIn(1, 65535)
        val binary = java.io.File(context.applicationInfo.nativeLibraryDir, "libbyedpi.so")
        if (!binary.exists()) {
            Log.e(TAG, "libbyedpi.so not found in ${context.applicationInfo.nativeLibraryDir}")
            return false
        }
        val args = buildList {
            addAll(strategyArgs())
            add("-p"); add(port.toString())
        }"""
new = """    private fun startCiadpi(argsOverride: Array<String>? = null): Boolean {
        val port = prefs.dpiSocksPort.coerceIn(1, 65535)
        val binary = java.io.File(context.applicationInfo.nativeLibraryDir, "libbyedpi.so")
        if (!binary.exists()) {
            Log.e(TAG, "libbyedpi.so not found in ${context.applicationInfo.nativeLibraryDir}")
            return false
        }
        val args = buildList {
            addAll(argsOverride ?: strategyArgs())
            add("-p"); add(port.toString())
        }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 2. public API for the benchmark: restart ciadpi only (VpnService/HEV stay up)
anchor = "    fun isRunning(): Boolean = started && ciadpiProcess?.isAlive == true && HevBridge.nativeIsRunning()\n"
addition = anchor + """
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
"""
assert s.count(anchor) == 1
s = s.replace(anchor, addition, 1)

open(p, 'w', encoding='utf-8').write(s)
print('DpiEngine patched for benchmark')
