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

    // ---------- CandidateCycle: цикл перебора и quarantine ----------

    @Test
    fun `failed candidate excluded from current cycle`() {
        val c = CandidateCycle()
        c.beginCycle()
        val t = 1_000L
        assertTrue(c.canTry("awg:1", t))
        c.recordFailure("awg:1", t, "handshake")
        assertFalse("failed candidate not tryable in cycle", c.canTry("awg:1", t))
        assertEquals(listOf("awg:1"), c.failures())
        // Новый цикл: «внутрицикловый» отказ забывается, quarantine остаётся.
        c.beginCycle()
        assertFalse("quarantine persists across cycles", c.canTry("awg:1", t + 1))
    }

    @Test
    fun `quarantine lifts after window`() {
        val c = CandidateCycle()
        c.beginCycle()
        val t0 = 1_000L
        c.recordFailure("public:1", t0, "data-plane")
        c.beginCycle()
        assertFalse(c.canTry("public:1", t0 + CandidateCycle.QUARANTINE_MS - 1))
        assertTrue(c.canTry("public:1", t0 + CandidateCycle.QUARANTINE_MS))
    }

    @Test
    fun `exhausted candidates yield zero tryable`() {
        val c = CandidateCycle()
        c.beginCycle()
        val t = 5_000L
        c.recordFailure("awg:1", t, "handshake")
        c.recordFailure("public:any_1", t, "data-plane")
        c.recordFailure("dpi", t, "telegram-dc-down")
        assertFalse(c.canTry("awg:1", t))
        assertFalse(c.canTry("public:any_1", t))
        assertFalse(c.canTry("dpi", t))
        assertEquals(3, c.failures().size)
    }

    @Test
    fun `new cycle forgets in-cycle marks but keeps backoff`() {
        val c = CandidateCycle()
        c.beginCycle()
        val t = 9_000L
        c.recordFailure("warp:generated", t, "transport")
        c.beginCycle()
        // В quarantine — нельзя даже в новом цикле.
        assertFalse(c.canTry("warp:generated", t))
        // Дальше — можно, когда окно истечёт.
        assertTrue(c.canTry("warp:generated", t + CandidateCycle.QUARANTINE_MS))
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
