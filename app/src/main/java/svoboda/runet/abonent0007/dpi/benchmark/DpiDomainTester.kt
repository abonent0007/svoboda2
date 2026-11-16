package svoboda.runet.abonent0007.dpi.benchmark

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/** Одна попытка проверки домена по фазам DNS → TCP → TLS → HTTP. */
data class DpiAttemptResult(
    val attemptNo: Int,
    val dnsOk: Boolean = false,
    val resolvedIp: String? = null,
    val tcpOk: Boolean = false,
    val tcpMs: Long? = null,
    val tlsOk: Boolean = false,
    val tlsMs: Long? = null,
    val httpOk: Boolean = false,
    val httpStatus: Int? = null,
    val httpMs: Long? = null,
    val errorType: String? = null,
    val errorMessage: String? = null,
) {
    /** Сеть и TLS работают; HTTP-ответ получен (либо домен помечен httpOptional). */
    fun networkOk(domain: DpiTestDomain): Boolean =
        dnsOk && tcpOk && tlsOk && (httpOk || (domain.httpOptional && tlsOk))
}

data class DpiDomainTestResult(
    val domain: DpiTestDomain,
    val attempts: List<DpiAttemptResult>,
) {
    val success: Boolean
        get() = attempts.count { it.networkOk(domain) } >= 2

    val successRate: Double
        get() = if (attempts.isEmpty()) 0.0 else attempts.count { it.networkOk(domain) }.toDouble() / attempts.size

    val medianHttpMs: Long?
        get() = attempts.mapNotNull { it.httpMs }.sorted().let {
            if (it.isEmpty()) null else it[it.size / 2]
        }

    val medianTlsMs: Long?
        get() = attempts.mapNotNull { it.tlsMs }.sorted().let {
            if (it.isEmpty()) null else it[it.size / 2]
        }

    val errorType: String?
        get() = attempts.firstOrNull { it.errorType != null }?.errorType

    val errorMessage: String?
        get() = attempts.firstOrNull { it.errorMessage != null }?.errorMessage
}

/**
 * Проверка доменов через локальный SOCKS5 (ciadpi).
 *
 * Фазы:
 *  - DNS: SOCKS CONNECT по домену → ciadpi резолвит, в ответе приходит bound IP.
 *  - TCP: SOCKS CONNECT по IP-литералу → чистое время установления соединения.
 *  - TLS: TLS-рукопожатие с SNI на том же сокете (проверка имени отключена,
 *    ошибки сертификата фиксируются отдельно от факта установления TLS).
 *  - HTTP: минимальный GET по тому же сокету, TTFB до первой строки статуса.
 */
class DpiDomainTester(
    private val socksPort: Int,
    private val attemptsPerDomain: Int = 3,
    private val pauseBetweenAttemptsMs: Long = 1_000,
) {

    private val permissiveContext: SSLContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }), SecureRandom())
    }

    fun test(domain: DpiTestDomain): DpiDomainTestResult {
        val results = ArrayList<DpiAttemptResult>(attemptsPerDomain)
        repeat(attemptsPerDomain) { index ->
            results += singleAttempt(domain, index + 1)
            if (index < attemptsPerDomain - 1) Thread.sleep(pauseBetweenAttemptsMs)
        }
        return DpiDomainTestResult(domain, results)
    }

    private fun singleAttempt(domain: DpiTestDomain, attemptNo: Int): DpiAttemptResult {
        val client = Socks5Client(port = socksPort)
        var dnsOk = false
        var resolvedIp: String? = null
        var tcpOk = false
        var tcpMs: Long? = null
        var tlsOk = false
        var tlsMs: Long? = null
        var httpOk = false
        var httpStatus: Int? = null
        var httpMs: Long? = null
        var errorType: String? = null
        var errorMessage: String? = null

        var plain: java.net.Socket? = null
        try {
            // --- DNS (резолвит ciadpi) ---
            val dns = client.connectDomain(domain.domain, 443)
            resolvedIp = dns.boundAddress
            dnsOk = true
            dns.socket.close()

            // --- TCP (по уже разрешённому IP) ---
            val tcpStart = System.nanoTime()
            val tcp = if (resolvedIp.contains(':')) {
                client.connectDomain(domain.domain, 443)
            } else {
                client.connectIp(resolvedIp, 443)
            }
            tcpMs = (System.nanoTime() - tcpStart) / 1_000_000
            tcpOk = true
            plain = tcp.socket

            // --- TLS ---
            val tlsStart = System.nanoTime()
            val ssl = permissiveContext.socketFactory.createSocket(plain, domain.domain, 443, true) as SSLSocket
            ssl.useClientMode = true
            val params = ssl.sslParameters
            params.serverNames = listOf(SNIHostName(domain.domain))
            params.endpointIdentificationAlgorithm = ""
            ssl.sslParameters = params
            ssl.startHandshake()
            tlsMs = (System.nanoTime() - tlsStart) / 1_000_000
            tlsOk = true

            // --- HTTP ---
            val request = buildString {
                append("GET ${domain.testPath} HTTP/1.1\r\n")
                append("Host: ${domain.domain}\r\n")
                append("User-Agent: Svoboda2-Benchmark/1.0\r\n")
                append("Accept: */*\r\n")
                append("Connection: close\r\n\r\n")
            }
            val httpStart = System.nanoTime()
            ssl.outputStream.write(request.toByteArray(Charsets.UTF_8))
            ssl.outputStream.flush()
            val statusLine = readStatusLine(ssl)
            httpMs = (System.nanoTime() - httpStart) / 1_000_000
            httpStatus = statusLine.first
            httpOk = true

            ssl.close()
        } catch (e: Throwable) {
            val (type, message) = classify(e)
            errorType = type
            errorMessage = message
            runCatching { plain?.close() }
        }
        return DpiAttemptResult(
            attemptNo = attemptNo,
            dnsOk = dnsOk,
            resolvedIp = resolvedIp,
            tcpOk = tcpOk,
            tcpMs = tcpMs,
            tlsOk = tlsOk,
            tlsMs = tlsMs,
            httpOk = httpOk,
            httpStatus = httpStatus,
            httpMs = httpMs,
            errorType = errorType,
            errorMessage = errorMessage,
        )
    }

    /** Возвращает (HTTP-код, статусная строка). */
    private fun readStatusLine(socket: SSLSocket): Pair<Int, String> {
        val buffer = StringBuilder()
        val chunk = ByteArray(1024)
        while (buffer.length < 4096) {
            val n = socket.inputStream.read(chunk)
            if (n < 0) break
            buffer.append(String(chunk, 0, n, Charsets.ISO_8859_1))
            val idx = buffer.indexOf("\r\n")
            if (idx >= 0) break
        }
        val line = buffer.toString().substringBefore("\r\n")
        val code = line.split(' ').getOrNull(1)?.toIntOrNull()
            ?: throw IOException("HTTP: некорректная статусная строка «$line»")
        return code to line
    }

    private fun classify(e: Throwable): Pair<String, String> {
        val msg = e.message ?: e.javaClass.simpleName
        return when (e) {
            is Socks5Client.SocksError -> "socks" to msg
            is SocketTimeoutException -> "timeout" to msg
            is ConnectException -> "refused" to msg
            is NoRouteToHostException -> "no_route" to msg
            is PortUnreachableException -> "refused" to msg
            is UnknownHostException -> "dns" to msg
            is javax.net.ssl.SSLException -> "tls" to msg
            is IOException -> "network" to msg
            else -> "error" to msg
        }
    }
}
