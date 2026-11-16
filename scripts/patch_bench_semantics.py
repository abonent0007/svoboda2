p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\dpi\benchmark\DpiDomainTester.kt'
s = open(p, encoding='utf-8').read()

old = """    /** Сеть и TLS работают; HTTP-ответ получен (либо домен помечен httpOptional). */
    fun networkOk(domain: DpiTestDomain): Boolean =
        dnsOk && tcpOk && tlsOk && (httpOk || (domain.httpOptional && tlsOk))"""
new = """    /** Сеть и TLS работают; HTTP-ответ получен (либо домен помечен httpOptional). */
    fun networkOk(domain: DpiTestDomain): Boolean =
        dnsOk && tcpOk && tlsOk && (httpOk || (domain.httpOptional && tlsOk))

    /**
     * Сеть доступна, но HTTP ограничен сервером (401/403/429).
     * Это НЕ сетевой отказ и не полноценный успех — отдельное состояние.
     */
    fun httpLimitedByServer(): Boolean =
        dnsOk && tcpOk && tlsOk && httpOk && httpStatus != null && httpStatus in setOf(401, 403, 429)

    /** Чистый ответ (2xx/3xx) — сервер отдал содержимое. */
    fun httpClean(): Boolean =
        dnsOk && tcpOk && tlsOk && httpOk && httpStatus != null && httpStatus in setOf(200, 201, 204, 301, 302, 303, 307, 308)"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = """    val success: Boolean
        get() = attempts.count { it.networkOk(domain) } >= 2"""
new = """    /** Сеть доступна (DNS+TCP+TLS), независимо от кода ответа сервера. */
    val networkSuccess: Boolean
        get() = attempts.count { it.networkOk(domain) } >= 2

    /** Сеть доступна, но сервер ограничил HTTP (401/403/429) — контент не получен. */
    val httpLimited: Boolean
        get() = networkSuccess && attempts.filter { it.networkOk(domain) }.all { it.httpLimitedByServer() }

    /** Полный успех: сеть доступна и получен чистый ответ. */
    val cleanSuccess: Boolean
        get() = networkSuccess && attempts.any { it.httpClean() }

    val success: Boolean
        get() = networkSuccess"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('tester: 403 semantics')
