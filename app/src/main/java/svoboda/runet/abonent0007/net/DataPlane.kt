package svoboda.runet.abonent0007.net

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import svoboda.runet.abonent0007.vpn.EndpointProbe
import svoboda.runet.abonent0007.vpn.FailureKind
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * Валидация data-plane через сам выбранный транспорт.
 *
 * AWG/WARP/публичные → собственный пакет идёт через tun0 (собственный пакет не исключён в этих режимах).
 * DPI → отдельный выход через локальный SOCKS5 (ciadpi), туннель не участвует.
 */
object DataPlane {

    private const val TIMEOUT_SEC = 5L
    private const val MAX_CF_BODY_BYTES = 16L * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    data class Outcome(
        val cf: EndpointProbe,
        val g: EndpointProbe,
    )

    /** Результат комбинированной проверки (ok = хотя бы один эндпоинт PASS). */
    fun isUsable(o: Outcome): Boolean = o.cf.ok || o.g.ok

    /** Классификация отказа: определяется по обоим эндпоинтам (приоритет HTTPS→ENDPOINT→DNS→TLS). */
    fun classify(o: Outcome): FailureKind {
        val details = listOfNotNull(o.cf.detail.takeIf { it.isNotBlank() }, o.g.detail.takeIf { it.isNotBlank() })
        val joined = details.joinToString(" ").lowercase()
        return when {
            "cert" in joined || "ssl" in joined || "handshake" in joined -> FailureKind.HANDSHAKE
            "unknownhost" in joined || "unable to resolve" in joined || "name or service not known" in joined -> FailureKind.DNS
            "timed out" in joined || "timeout" in joined -> FailureKind.ENDPOINT_UNREACHABLE
            "refused" in joined || "eof" in joined -> FailureKind.ENDPOINT_UNREACHABLE
            "http=" in joined -> FailureKind.HTTPS
            else -> FailureKind.UNKNOWN
        }
    }

    /** Одна пара проверок (Cloudflare trace + generate_204); параллельно. */
    suspend fun validate(proxySocksPort: Int? = null): Outcome = coroutineScope {
        val cf = async { probe("https://cloudflare.com/cdn-cgi/trace", proxySocksPort, expect = Expect.CF) }
        val g = async { probe("https://connectivitycheck.gstatic.com/generate_204", proxySocksPort, expect = Expect.G204) }
        Outcome(cf.await(), g.await())
    }

    private enum class Expect { CF, G204 }

    private suspend fun probe(url: String, socksPort: Int?, expect: Expect): EndpointProbe =
        withContext(Dispatchers.IO) {
            val c = socksPort?.let { socksClient(it) } ?: client
            runCatching {
                val req = Request.Builder().url(url).header("User-Agent", "Svoboda2-Android").build()
                c.newCall(req).execute().use { resp ->
                    val code = resp.code
                    when (expect) {
                        Expect.CF -> {
                            // Ограничиваем чтение тела (16 КБ достаточно для строки ip=...)
                            val body = resp.body?.source()?.use { src ->
                                val buf = okio.Buffer()
                                src.request(MAX_CF_BODY_BYTES)
                                buf.write(src, MAX_CF_BODY_BYTES)
                                buf.readUtf8()
                            }.orEmpty()
                            val ok = resp.isSuccessful && Regex("""(?m)^ip=\S+""").containsMatchIn(body)
                            if (ok) EndpointProbe.pass(code)
                            else EndpointProbe.fail(FailureKind.HTTPS, "http=$code, no-ip-line", code)
                        }
                        Expect.G204 -> {
                            if (code == 204) EndpointProbe.pass(code)
                            else EndpointProbe.fail(FailureKind.HTTPS, "http=$code, expected 204", code)
                        }
                    }
                }
            }.getOrElse { e ->
                EndpointProbe.fail(classifyException(e), exceptionDetail(e))
            }
        }

    private fun socksClient(port: Int): OkHttpClient = client.newBuilder()
        .proxy(java.net.Proxy(java.net.Proxy.Type.SOCKS, java.net.InetSocketAddress("127.0.0.1", port)))
        .build()

    private fun classifyException(e: Throwable): FailureKind = when (e) {
        is UnknownHostException -> FailureKind.DNS
        is SocketTimeoutException, is ConnectException -> FailureKind.ENDPOINT_UNREACHABLE
        is SSLException, is CertificateException -> FailureKind.HANDSHAKE
        is EOFException -> FailureKind.ENDPOINT_UNREACHABLE
        else -> FailureKind.UNKNOWN
    }

    private fun exceptionDetail(e: Throwable): String =
        e::class.java.simpleName.let { "$it: ${e.message?.take(64) ?: ""}".trim() }
}
