package svoboda3.runet.abonent0007.dpi.benchmark

import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.SecureRandom
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/** Маршрут измерения. */
enum class BenchRoute {
    /** Через локальный SOCKS5 = через проверяемую стратегию ciadpi. */
    SOCKS,

    /** Напрямую по физической сети (baseline). */
    DIRECT,
}

/** Классификация результата попытки (по ТЗ-ревью). */
enum class BenchOutcome {
    /** HTTP 2xx/3xx — сайт ответил штатно. */
    FULL_SUCCESS,

    /** HTTP 401/403/429 — сервер достигнут, запрос ограничен. */
    SERVER_LIMITED,

    /** HTTP 5xx — сервер ответил, но у него ошибка. */
    SERVER_ERROR,

    /** DNS+TCP+TLS прошли, HTTP-ответа нет. */
    TRANSPORT_ONLY,

    /** Не соединились (DNS/TCP/TLS). */
    NETWORK_FAILURE,
}

/** Одна попытка проверки домена по фазам DNS → TCP → TLS → HTTP. */
data class DpiAttemptResult(
    val attemptNo: Int,
    val route: BenchRoute = BenchRoute.SOCKS,
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
    /** Сеть и TLS работают (независимо от кода ответа сервера). */
    fun transportOk(domain: DpiTestDomain): Boolean =
        dnsOk && tcpOk && tlsOk && (httpOk || (domain.httpOptional && tlsOk))

    /** HTTP-ответ ограничен сервером (401/403/429). */
    fun serverLimited(): Boolean =
        dnsOk && tcpOk && tlsOk && httpOk && httpStatus != null && httpStatus in setOf(401, 403, 429)

    /** Чистый ответ 2xx/3xx. */
    fun httpClean(): Boolean =
        dnsOk && tcpOk && tlsOk && httpOk && httpStatus != null &&
            httpStatus in setOf(200, 201, 204, 301, 302, 303, 307, 308)

    /** Итоговая классификация попытки. */
    fun outcome(domain: DpiTestDomain): BenchOutcome = when {
        !dnsOk || !tcpOk || !tlsOk -> BenchOutcome.NETWORK_FAILURE
        httpOk && httpStatus in setOf(200, 201, 204, 301, 302, 303, 307, 308) -> BenchOutcome.FULL_SUCCESS
        httpOk && httpStatus in setOf(401, 403, 429) -> BenchOutcome.SERVER_LIMITED
        httpOk && httpStatus != null && httpStatus >= 500 -> BenchOutcome.SERVER_ERROR
        else -> BenchOutcome.TRANSPORT_ONLY
    }
}

data class DpiDomainTestResult(
    val domain: DpiTestDomain,
    val attempts: List<DpiAttemptResult>,
    val route: BenchRoute = BenchRoute.SOCKS,
) {
    /** Сеть доступна (DNS+TCP+TLS), независимо от кода ответа. */
    val networkSuccess: Boolean
        get() = attempts.count { it.transportOk(domain) } >= 2

    /** Сеть доступна, но сервер ограничил HTTP. */
    val httpLimited: Boolean
        get() = networkSuccess && attempts.filter { it.transportOk(domain) }.all { it.serverLimited() }

    /** Полный успех: сеть доступна и есть чистый HTTP-ответ. */
    val cleanSuccess: Boolean
        get() = networkSuccess && attempts.any { it.httpClean() }

    /** Итоговый исход по домену (для сводки). */
    val outcome: BenchOutcome
        get() {
            val ok = attempts.filter { it.transportOk(domain) }
            if (ok.isEmpty()) return BenchOutcome.NETWORK_FAILURE
            if (ok.any { it.httpClean() }) return BenchOutcome.FULL_SUCCESS
            if (ok.all { it.serverLimited() }) return BenchOutcome.SERVER_LIMITED
            if (ok.any { it.httpOk && it.httpStatus != null && it.httpStatus >= 500 }) return BenchOutcome.SERVER_ERROR
            return BenchOutcome.TRANSPORT_ONLY
        }

    val success: Boolean get() = networkSuccess

    val successRate: Double
        get() = if (attempts.isEmpty()) 0.0 else attempts.count { it.transportOk(domain) }.toDouble() / attempts.size

    val medianHttpMs: Long?
        get() = attempts.mapNotNull { it.httpMs }.sorted().let {
            if (it.isEmpty()) null else it[it.size / 2]
        }

    val medianTlsMs: Long?
        get() = attempts.mapNotNull { it.tlsMs }.sorted().let {
            if (it.isEmpty()) null else it[it.size / 2]
        }

    val p90HttpMs: Long?
        get() = attempts.mapNotNull { it.httpMs }.sorted().let {
            if (it.isEmpty()) null else it[(it.size * 0.9).toInt().coerceAtMost(it.size - 1)]
        }

    val errorType: String?
        get() = attempts.firstOrNull { it.errorType != null }?.errorType

    val errorMessage: String?
        get() = attempts.firstOrNull { it.errorMessage != null }?.errorMessage
}

/**
 * Проверка доменов: через локальный SOCKS5 (ciadpi) и напрямую (baseline).
 *
 * Через SOCKS: DNS резолвит ciadpi (SOCKS CONNECT с доменом), дальше TCP/TLS/HTTP
 * идут по выбранной стратегии — это и есть то, что мы сравниваем.
 * Напрямую: системный DNS + обычные сокеты (в DPI-режиме наш пакет исключён из
 * VPN, поэтому это действительно физическая сеть).
 */
class DpiDomainTester(
    private val socksPort: Int,
    private val attemptsPerDomain: Int = 3,
    private val pauseBetweenAttemptsMs: Long = 1_000,
) {

    private val permissiveContext: SSLContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
        }), SecureRandom())
    }

    fun test(domain: DpiTestDomain, route: BenchRoute = BenchRoute.SOCKS): DpiDomainTestResult {
        val results = ArrayList<DpiAttemptResult>(attemptsPerDomain)
        repeat(attemptsPerDomain) { index ->
            results += singleAttempt(domain, index + 1, route)
            if (index < attemptsPerDomain - 1) Thread.sleep(pauseBetweenAttemptsMs)
        }
        return DpiDomainTestResult(domain, results, route)
    }

    private fun singleAttempt(domain: DpiTestDomain, attemptNo: Int, route: BenchRoute): DpiAttemptResult {
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
        var plain: Socket? = null

        try {
            // --- DNS ---
            val dnsStart = System.nanoTime()
            when (route) {
                BenchRoute.SOCKS -> {
                    val dns = client.connectDomain(domain.domain, 443)
                    resolvedIp = dns.boundAddress
                    dns.socket.close()
                    dnsOk = true
                }
                BenchRoute.DIRECT -> {
                    val ips = InetAddress.getAllByName(domain.domain).map { it.hostAddress }
                    if (ips.isEmpty()) throw UnknownHostException(domain.domain)
                    resolvedIp = ips.first()
                    dnsOk = true
                }
            }
            @Suppress("UNUSED_VARIABLE")
            val dnsElapsed = (System.nanoTime() - dnsStart) / 1_000_000

            // --- TCP ---
            val tcpStart = System.nanoTime()
            plain = when (route) {
                BenchRoute.SOCKS -> {
                    if (resolvedIp!!.contains(':')) {
                        client.connectDomain(domain.domain, 443).socket
                    } else {
                        client.connectIp(resolvedIp!!, 443).socket
                    }
                }
                BenchRoute.DIRECT -> {
                    val s = Socket()
                    s.connect(InetSocketAddress(resolvedIp!!, 443), 5_000)
                    s.soTimeout = 6_000
                    s.tcpNoDelay = true
                    s
                }
            }
            tcpMs = (System.nanoTime() - tcpStart) / 1_000_000
            tcpOk = true

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
            route = route,
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
            if (buffer.indexOf("\r\n") >= 0) break
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
