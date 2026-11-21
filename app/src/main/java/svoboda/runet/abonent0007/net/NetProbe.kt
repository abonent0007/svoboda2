package svoboda.runet.abonent0007.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.Locale
import java.util.concurrent.TimeUnit

/** IP/страна выхода и пинги. */
object NetProbe {

    data class ExitInfo(val ip: String, val country: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * VPN-сеть приложения (tun). null — туннель не поднят.
     * Пробы, привязанные к этой сети, идут через TUN, даже если само приложение
     * исключено из VPN (addDisallowedApplication) — иначе проверка ушла бы напрямую.
     */
    fun vpnNetwork(context: Context): Network? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return runCatching {
            cm.allNetworks.firstOrNull { net ->
                cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        }.getOrNull()
    }

    private fun parseTrace(response: okhttp3.Response): ExitInfo? {
        if (!response.isSuccessful) return null
        val body = response.body?.string().orEmpty()
        var ip = ""
        var loc = ""
        body.lineSequence().forEach { line ->
            when {
                line.startsWith("ip=") -> ip = line.substringAfter("ip=").trim()
                line.startsWith("loc=") -> loc = line.substringAfter("loc=").trim()
            }
        }
        return if (ip.isBlank()) null else ExitInfo(ip, loc)
    }

    /** IP и код страны через Cloudflare trace (ip=, loc=). Прямой путь (может идти мимо туннеля). */
    suspend fun fetchExit(): ExitInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://www.cloudflare.com/cdn-cgi/trace")
                .header("User-Agent", "Svoboda2-Android")
                .build()
            client.newCall(request).execute().use { parseTrace(it) }
        }.getOrNull()
    }

    /**
     * Честное доказательство data-plane: HTTPS через VPN-сеть (tun).
     * Резолвинг имени и TLS идут по туннелю; null — туннель не поднят или путь не работает.
     */
    suspend fun fetchExitBound(context: Context): ExitInfo? = withContext(Dispatchers.IO) {
        val net = vpnNetwork(context) ?: return@withContext null
        val bound = OkHttpClient.Builder()
            .socketFactory(net.socketFactory)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
        runCatching {
            val request = Request.Builder()
                .url("https://www.cloudflare.com/cdn-cgi/trace")
                .header("User-Agent", "Svoboda2-Android")
                .build()
            bound.newCall(request).execute().use { parseTrace(it) }
        }.getOrNull()
    }

    /** TCP-пинг: миллисекунды до установления соединения; -1 при неудаче. */
    suspend fun tcpPing(
        host: String = "1.1.1.1",
        port: Int = 443,
        timeoutMs: Int = 2_000,
    ): Int = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val elapsed = (System.nanoTime() - started) / 1_000_000
                elapsed.toInt().coerceAtLeast(0)
            }
        }.getOrDefault(-1)
    }

    /**
     * TCP-пинг через VPN-сеть (tun). -1, если туннеля нет или путь не работает.
     * Используется для графика задержки, чтобы не показывать прямой (мимо туннеля) пинг.
     */
    suspend fun tcpPingBound(
        context: Context,
        host: String = "1.1.1.1",
        port: Int = 443,
        timeoutMs: Int = 2_000,
    ): Int = withContext(Dispatchers.IO) {
        val net = vpnNetwork(context) ?: return@withContext -1
        val started = System.nanoTime()
        runCatching {
            Socket().use { socket ->
                net.bindSocket(socket)
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val elapsed = (System.nanoTime() - started) / 1_000_000
                elapsed.toInt().coerceAtLeast(0)
            }
        }.getOrDefault(-1)
    }

    /** «RU — Россия» из кода страны; пустая строка при неизвестном коде. */
    private fun socksClient(port: Int): OkHttpClient = OkHttpClient.Builder()
        .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port)))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Выход через локальный SOCKS5 (ciadpi). Честное доказательство, что обход DPI работает. */
    suspend fun fetchExitViaSocks(port: Int): ExitInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://www.cloudflare.com/cdn-cgi/trace")
                .header("User-Agent", "Svoboda2-Android")
                .build()
            socksClient(port).newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string().orEmpty()
                var ip = ""
                var loc = ""
                body.lineSequence().forEach { line ->
                    when {
                        line.startsWith("ip=") -> ip = line.substringAfter("ip=").trim()
                        line.startsWith("loc=") -> loc = line.substringAfter("loc=").trim()
                    }
                }
                if (ip.isBlank()) null else ExitInfo(ip, loc)
            }
        }.getOrNull()
    }

    /** Пинг через локальный SOCKS5 (ciadpi): время полного HTTPS-ответа; -1 при ошибке. */
    suspend fun socksPing(port: Int): Int = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        val exit = fetchExitViaSocks(port)
        if (exit == null) return@withContext -1
        ((System.nanoTime() - started) / 1_000_000).toInt().coerceAtLeast(0)
    }

    /**
     * Достижим ли конкретный https-ресурс через локальный SOCKS5 (ciadpi).
     * true = получен любой HTTP-ответ (даже 4xx/5xx): домен не отваливается по таймауту.
     */
    suspend fun probeViaSocks(port: Int, url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Svoboda2-Android")
                .build()
            socksClient(port).newCall(request).execute().use { resp -> resp.code in 200..599 }
        }.getOrDefault(false)
    }

    fun countryLabel(code: String): String {
        if (code.isBlank()) return ""
        val name = Locale("", code.uppercase(Locale.US)).displayCountry
        val flag = code
            .uppercase(Locale.US)
            .filter { it.isLetter() }
            .map { Character.toChars(0x1F1E6 + (it.code - 'A'.code)) }
            .joinToString("") { String(it) }
        return if (name.isBlank()) code.uppercase() else "$flag $name"
    }
}
