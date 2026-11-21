package svoboda3.runet.abonent0007.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** classifyFailure: покрытие всех веток, включая null-kind и усечение сообщения. */
class ClassifyFailureTest {

    @Test
    fun `each kind maps to its stage`() {
        assertTrue(classifyFailure(FailureKind.HANDSHAKE, null, "x").startsWith("stage=handshake"))
        assertTrue(classifyFailure(FailureKind.DNS, null, "x").startsWith("stage=dns"))
        assertTrue(classifyFailure(FailureKind.HTTPS, null, "x").startsWith("stage=https"))
        assertTrue(classifyFailure(FailureKind.ENDPOINT_UNREACHABLE, null, "x").startsWith("stage=endpoint"))
        assertTrue(classifyFailure(FailureKind.UNKNOWN, null, "x").startsWith("stage=unknown"))
        assertTrue(classifyFailure(null, null, "x").startsWith("stage=unknown"))
    }

    @Test
    fun `http code appended when present`() {
        assertEquals("stage=https, http=502, boom", classifyFailure(FailureKind.HTTPS, 502, "boom"))
        assertEquals("stage=handshake, http=0, boom", classifyFailure(FailureKind.HANDSHAKE, 0, "boom"))
    }

    @Test
    fun `blank message leaves base without trailing comma`() {
        assertEquals("stage=dns, http=503", classifyFailure(FailureKind.DNS, 503, "   "))
        assertEquals("stage=dns", classifyFailure(FailureKind.DNS, null, ""))
    }

    @Test
    fun `message truncated to 80 chars`() {
        val long = "y".repeat(500)
        val s = classifyFailure(FailureKind.UNKNOWN, null, long)
        assertEquals("stage=unknown, " + "y".repeat(80), s)
    }

    @Test
    fun `failures list from cycle feeds readable summary`() {
        val cycle = CandidateCycle(clock = { 1000L })
        cycle.beginCycle()
        cycle.recordFailure("public:nl_1", "data-plane")
        cycle.recordFailure("warp:generated", "handshake")
        assertEquals(listOf("public:nl_1", "warp:generated"), cycle.failures())
    }
}
