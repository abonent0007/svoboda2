package svoboda.runet.abonent0007.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
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

    /** IP и код страны через Cloudflare trace (ip=, loc=). */
    suspend fun fetchExit(): ExitInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://www.cloudflare.com/cdn-cgi/trace")
                .header("User-Agent", "Svoboda2-Android")
                .build()
            client.newCall(request).execute().use { response ->
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

    /** «RU — Россия» из кода страны; пустая строка при неизвестном коде. */
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
