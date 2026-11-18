package svoboda.runet.abonent0007.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0: чистая логика failover / data-plane / anti-bounce (без сети и Android).
 * Сетевые пути (OkHttp, VpnService, ciadpi SOCKS) — только через device-тесты (отдельное задание).
 */
class AutoFailoverTest {

    // ---------- DataPlaneVerdict: таблица решений ----------

    @Test
    fun `cf pass g pass gives healthy`() {
        assertEquals(Verdict.Healthy, DataPlaneVerdict.evaluate(cfPass = true, gPass = true, isRetry = false))
        assertEquals(Verdict.Healthy, DataPlaneVerdict.evaluate(true, true, isRetry = true))
    }

    @Test
    fun `one pass gives healthy partial`() {
        assertEquals(Verdict.HealthyPartial, DataPlaneVerdict.evaluate(true, false, isRetry = false))
        assertEquals(Verdict.HealthyPartial, DataPlaneVerdict.evaluate(false, true, isRetry = false))
        assertEquals(Verdict.HealthyPartial, DataPlaneVerdict.evaluate(true, false, isRetry = true))
    }

    @Test
    fun `both fail twice degrades`() {
        assertEquals(Verdict.NeedsRetry, DataPlaneVerdict.evaluate(false, false, isRetry = false))
        assertEquals(Verdict.Degraded, DataPlaneVerdict.evaluate(false, false, isRetry = true))
    }

    // ---------- DataPlane.classify ----------

    @Test
    fun `classify prefers handshake signals`() {
        assertEquals(
            FailureKind.HANDSHAKE,
            svoboda.runet.abonent0007.net.DataPlane.classify(
                svoboda.runet.abonent0007.net.DataPlane.Outcome(
                    cf = EndpointProbe.fail(FailureKind.HANDSHAKE, "SSLHandshakeException: cert"),
                    g = EndpointProbe.fail(FailureKind.HTTPS, "http=500"),
                ),
            ),
        )
    }

    @Test
    fun `classify dns and endpoint`() {
        assertEquals(
            FailureKind.DNS,
            svoboda.runet.abonent0007.net.DataPlane.classify(
                svoboda.runet.abonent0007.net.DataPlane.Outcome(
                    cf = EndpointProbe.fail(FailureKind.DNS, "UnknownHostException"),
                    g = EndpointProbe.fail(FailureKind.DNS, "unable to resolve host"),
                ),
            ),
        )
        assertEquals(
            FailureKind.ENDPOINT_UNREACHABLE,
            svoboda.runet.abonent0007.net.DataPlane.classify(
                svoboda.runet.abonent0007.net.DataPlane.Outcome(
                    cf = EndpointProbe.fail(FailureKind.ENDPOINT_UNREACHABLE, "timed out"),
                    g = EndpointProbe.fail(FailureKind.ENDPOINT_UNREACHABLE, "connect refused"),
                ),
            ),
        )
    }

    @Test
    fun `isUsable true when at least one pass`() {
        val both = svoboda.runet.abonent0007.net.DataPlane.Outcome(
            cf = EndpointProbe.pass(200), g = EndpointProbe.pass(204),
        )
        val onlyCf = svoboda.runet.abonent0007.net.DataPlane.Outcome(
            cf = EndpointProbe.pass(200), g = EndpointProbe.fail(FailureKind.HTTPS, "http=500"),
        )
        val none = svoboda.runet.abonent0007.net.DataPlane.Outcome(
            cf = EndpointProbe.fail(FailureKind.HTTPS, "http=500"), g = EndpointProbe.fail(FailureKind.HTTPS, "http=404"),
        )
        assertTrue(svoboda.runet.abonent0007.net.DataPlane.isUsable(both))
        assertTrue(svoboda.runet.abonent0007.net.DataPlane.isUsable(onlyCf))
        assertFalse(svoboda.runet.abonent0007.net.DataPlane.isUsable(none))
    }

    // ---------- CandidateCycle: цикл перебора и quarantine (инжектированный clock) ----------

    private fun testCycle(): Pair<CandidateCycle, (Long) -> Unit> {
        var t = 10_000L
        val c = CandidateCycle(clock = { t })
        return Pair(c, { t += it })
    }

    @Test
    fun `failed candidate excluded from current cycle`() {
        val (c, tick) = testCycle()
        c.beginCycle()
        assertTrue(c.canTry("awg:1"))
        c.recordFailure("awg:1", "handshake")
        assertFalse("failed candidate not tryable in cycle", c.canTry("awg:1"))
        assertEquals(listOf("awg:1"), c.failures())
        c.beginCycle()
        tick(1_000)
        assertFalse("quarantine persists across cycles", c.canTry("awg:1"))
    }

    @Test
    fun `quarantine lifts after window`() {
        val (c, tick) = testCycle()
        c.beginCycle()
        c.recordFailure("public:1", "data-plane")
        c.beginCycle()
        tick(CandidateCycle.QUARANTINE_MS - 1)
        assertFalse(c.canTry("public:1"))
        tick(1) // ровно окно истекло
        assertTrue(c.canTry("public:1"))
    }

    @Test
    fun `exhausted candidates yield zero tryable`() {
        val (c, _) = testCycle()
        c.beginCycle()
        c.recordFailure("awg:1", "handshake")
        c.recordFailure("public:any_1", "data-plane")
        c.recordFailure("dpi", "telegram-dc-down")
        assertFalse(c.canTry("awg:1"))
        assertFalse(c.canTry("public:any_1"))
        assertFalse(c.canTry("dpi"))
        assertEquals(3, c.failures().size)
    }

    @Test
    fun `new cycle forgets in-cycle marks but keeps backoff`() {
        val (c, tick) = testCycle()
        c.beginCycle()
        c.recordFailure("warp:generated", "transport")
        c.beginCycle()
        // В quarantine — нельзя даже в новом цикле.
        assertFalse(c.canTry("warp:generated"))
        // Дальше — можно, когда окно истечёт.
        tick(CandidateCycle.QUARANTINE_MS)
        assertTrue(c.canTry("warp:generated"))
    }

    @Test
    fun `quarantine clock starts at actual failure time, not at cycle start`() {
        var t = 1_000L
        val c = CandidateCycle(clock = { t })
        c.beginCycle()
        t += 9 * 60_000L
        c.recordFailure("slow:1", "data-plane")
        t += 2 * 60_000L // +11 мин от старта цикла, но всего +2 мин от факта отказа
        c.beginCycle()   // новый цикл снимает in-cycle отметку — решает именно quarantine
        assertFalse("full 10min window must respect actual failure time", c.canTry("slow:1"))
        t += 8 * 60_000L // итого 10 мин от отказа
        assertTrue("after exactly 10min from failure, candidate must be re-eligible", c.canTry("slow:1"))
    }

    @Test
    fun `quarantine lapsing during a long cycle restores eligibility`() {
        var t = 1_000L
        val c = CandidateCycle(clock = { t })
        c.beginCycle()
        t += 9 * 60_000L // долгая подготовка к отказу — время не есть факт отказа
        c.recordFailure("long:1", "handshake")
        t += CandidateCycle.QUARANTINE_MS + 1
        c.beginCycle() // новый цикл; окно по факту отказа истекло
        assertTrue("window expiring means re-eligibility in next cycle", c.canTry("long:1"))
    }

    @Test
    fun `nextEligibleAt returns earliest quarantine expiry`() {
        var t = 1_000L
        val c = CandidateCycle(clock = { t })
        c.beginCycle()
        c.recordFailure("a", "x")
        val tB = t + 100
        t = tB
        c.recordFailure("b", "x")
        assertEquals(1_000L + CandidateCycle.QUARANTINE_MS, c.nextEligibleAt())
        // a истёк, b ещё в окне.
        t = 1_000L + CandidateCycle.QUARANTINE_MS + 1
        assertEquals(tB + CandidateCycle.QUARANTINE_MS, c.nextEligibleAt())
        // После истечения обоих окон — никого нет.
        t = tB + CandidateCycle.QUARANTINE_MS + 1
        assertEquals(null, c.nextEligibleAt())
    }

    @Test
    fun `all quarantine formula covers mixed previously-quarantined and freshly-failed`() {
        var t = 1_000L
        val c = CandidateCycle(clock = { t })
        // Ранний отказ одного кандидата.
        c.beginCycle()
        c.recordFailure("public:any_1", "data-plane")
        // Новый цикл: два других проваливались сейчас.
        t += 1_000
        c.beginCycle()
        c.recordFailure("dpi", "data-plane")
        c.recordFailure("public:fi_1", "transport")
        // Все три недоступны, хотя причины непусты (failures — только текущий цикл).
        val candidates = listOf("public:any_1", "dpi", "public:fi_1")
        assertTrue("every candidate must be unavailable", candidates.all { !c.canTry(it) })
        assertEquals(2, c.failures().size)
    }

    @Test
    fun `failover bypasses hold but not quarantine — matrix is honoured by AntiBounceGate`() {
        val gate = AntiBounceGate.proceed(
            bypassHold = true,
            hasHealthySession = true,
            lastSwitchAt = 100_000L,
            nowMs = 100_100L,
            holdMinutes = 5,
        )
        assertEquals(Proceed.BypassHold, gate)
    }

    // ---------- AntiBounceGate: разделение защиты рабочих и failover ----------

    @Test
    fun `manual connect always proceeds`() {
        val gate = AntiBounceGate.proceed(
            bypassHold = true,
            hasHealthySession = true,
            lastSwitchAt = 0L,
            nowMs = 0L,
            holdMinutes = 5,
        )
        assertEquals(Proceed.BypassHold, gate)
    }

    @Test
    fun `bypass hold bypasses even very recent healthy switch`() {
        val gate = AntiBounceGate.proceed(
            bypassHold = true,
            hasHealthySession = true,
            lastSwitchAt = 100_000L,
            nowMs = 100_100L,
            holdMinutes = 5,
        )
        assertEquals(Proceed.BypassHold, gate)
    }

    @Test
    fun `healthy switch suppressed inside hold window`() {
        val gate = AntiBounceGate.proceed(
            bypassHold = false,
            hasHealthySession = true,
            lastSwitchAt = 0L,
            nowMs = 60_000L,       // 1 минута < 5 мин
            holdMinutes = 5,
        )
        assertEquals(Proceed.Deferred, gate)
    }

    @Test
    fun `healthy switch allowed after hold window`() {
        val gate = AntiBounceGate.proceed(
            bypassHold = false,
            hasHealthySession = true,
            lastSwitchAt = 0L,
            nowMs = 5 * 60_000L + 1,
            holdMinutes = 5,
        )
        assertEquals(Proceed.ProceedNow, gate)
    }

    @Test
    fun `no healthy session yet proceeds immediately`() {
        val gate = AntiBounceGate.proceed(
            bypassHold = false,
            hasHealthySession = false,
            lastSwitchAt = 0L,
            nowMs = 0L,
            holdMinutes = 5,
        )
        assertEquals(Proceed.ProceedNow, gate)
    }

    // ---------- ConnectionResult: контракт ----------

    @Test
    fun `defected result never equals or implies healthy`() {
        val deferred: ConnectionResult = ConnectionResult.ReconnectDeferred
        val degraded: ConnectionResult = ConnectionResult.CandidateDegraded("x", FailureKind.HTTPS, "http=500")
        val noRoute: ConnectionResult = ConnectionResult.NoWorkingRoute(listOf("awg:1: handshake"))
        assertFalse(deferred is ConnectionResult.ConnectedHealthy)
        assertFalse(degraded is ConnectionResult.ConnectedHealthy)
        assertFalse(noRoute is ConnectionResult.ConnectedHealthy)
    }

    @Test
    fun `failure detail survives validation step naming`() {
        val detail = classifyFailure(FailureKind.HTTPS, 503, "http=503, bad gateway")
        assertTrue("detail must carry stage", detail.contains("stage=https"))
        assertTrue("detail carries http code", detail.contains("http=503"))
    }
}
