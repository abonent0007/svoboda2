import re

root = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007'

# --- TelegramDc: проверка через SOCKS (DPI) или напрямую (AWG/WARP) ---
p = root + r'\net\TelegramDc.kt'
s = open(p, encoding='utf-8').read()
old = """    suspend fun check(timeoutMs: Int = 2_000): Status = withContext(Dispatchers.IO) {
        val ok = mutableListOf<String>()
        var lastError: String? = null
        for (ip in DC_IPS) {
            val reachable = try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, 443), timeoutMs)
                    true
                }
            } catch (e: Exception) {
                lastError = e.javaClass.simpleName
                false
            }
            if (reachable) ok += ip
            if (ok.size >= 2) break
        }
        Status(reachable = ok.isNotEmpty(), reachableIps = ok, error = if (ok.isEmpty()) lastError else null)
    }"""
new = """    /**
     * @param viaSocks true — проверять через локальный SOCKS5 (то есть через активный
     *   DPI-обход ciadpi); false — обычным сокетом (в режиме AWG/WARP наш пакет идёт
     *   через туннель, в режиме DPI пакет исключён из VPN — тогда это физическая сеть).
     */
    suspend fun check(
        viaSocks: Boolean = false,
        socksPort: Int = 1080,
        timeoutMs: Int = 2_500,
    ): Status = withContext(Dispatchers.IO) {
        val ok = mutableListOf<String>()
        var lastError: String? = null
        val client = svoboda.runet.abonent0007.dpi.benchmark.Socks5Client(port = socksPort)
        for (ip in DC_IPS) {
            val reachable = try {
                if (viaSocks) {
                    client.connectIp(ip, 443).socket.close()
                } else {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(ip, 443), timeoutMs)
                    }
                }
                true
            } catch (e: Exception) {
                lastError = e.javaClass.simpleName
                false
            }
            if (reachable) ok += ip
            if (ok.size >= 2) break
        }
        Status(reachable = ok.isNotEmpty(), reachableIps = ok, error = if (ok.isEmpty()) lastError else null)
    }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('TelegramDc: route-aware')

# --- ConnectionManager: в AUTO проверяем DC по активному транспорту ---
p = root + r'\vpn\ConnectionManager.kt'
s = open(p, encoding='utf-8').read()
old = """                val dc = TelegramDc.check()"""
new = """                val dc = TelegramDc.check(viaSocks = true, socksPort = prefs.dpiSocksPort)"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('ConnectionManager: DC check via SOCKS in DPI mode')

# --- UI: проверка DC с учётом режима ---
p = root + r'\ui\method\MethodSettings.kt'
s = open(p, encoding='utf-8').read()
old = "            val status = svoboda.runet.abonent0007.net.TelegramDc.check()"
new = """            val viaSocks = prefs.connectionMethod == "DPI_DIRECT"
            val status = svoboda.runet.abonent0007.net.TelegramDc.check(
                viaSocks = viaSocks,
                socksPort = prefs.dpiSocksPort,
            )"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('AUTO UI: DC check route-aware')
