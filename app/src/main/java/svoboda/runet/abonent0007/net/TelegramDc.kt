package svoboda.runet.abonent0007.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Проверка доступности Telegram DC (MTProto).
 *
 * Telegram-приложение ходит напрямую к IP-адресам DC (149.154.167.50, 91.108.56.130 …),
 * и в РФ эти адреса часто отдают RST/не отвечают — это блок по IP, который
 * desync-стратегии (ByeDPI) не обходят. DPI-стратегии имеют смысл для
 * Telegram Web (web.telegram.org по HTTPS), но не для MTProto к DC.
 *
 * Проверка = обычный TCP-connect к порту 443 DC.
 */
object TelegramDc {

    val DC_IPS: List<String> = listOf(
        "149.154.167.50",
        "149.154.167.51",
        "149.154.175.50",
        "91.108.56.130",
        "91.108.4.130",
        "91.108.8.130",
    )

    data class Status(val reachable: Boolean, val reachableIps: List<String>, val error: String?)

    /**
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
    }
}
