package svoboda3.runet.abonent0007.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CandidateCycle.nextEligibleAt и порядок отказов — логика quarantine для AUTO-кандидатов. */
class CandidateCycleEligibilityTest {

    private var now = 100_000L
    private fun cycle() = CandidateCycle(clock = { now })

    @Test
    fun `no failures gives null nextEligibleAt`() {
        val c = cycle()
        c.beginCycle()
        assertNull(c.nextEligibleAt())
        assertFalse(c.attemptedSomething())
    }

    @Test
    fun `single failure exposes quarantine-until`() {
        val c = cycle()
        c.beginCycle()
        c.recordFailure("public:nl_1", "transport")
        assertEquals(now + CandidateCycle.QUARANTINE_MS, c.nextEligibleAt())
        assertTrue(c.attemptedSomething())
    }

    @Test
    fun `nextEligibleAt is the minimum of quarantines`() {
        val c = cycle()
        c.recordFailure("a", "x")        // quarantine "a" until 700_000 @ t=100_000
        now += 5_000                     // t=105_000
        c.recordFailure("b", "x")        // quarantine "b" until 705_000
        // минимум дедлайнов — у "a" (записан раньше)
        assertEquals(100_000L + CandidateCycle.QUARANTINE_MS, c.nextEligibleAt())
    }

    @Test
    fun `quarantine lifts for all and reports null`() {
        val c = cycle()
        c.recordFailure("a", "x")
        c.recordFailure("b", "x")
        now += CandidateCycle.QUARANTINE_MS + 1
        assertNull(c.nextEligibleAt())
        c.beginCycle() // failedInCycle очищается только новым циклом (quarantine — НЕ очищается)
        assertTrue(c.canTry("a"))
        assertTrue(c.canTry("b"))
    }

    @Test
    fun `repeated failures of same id do not duplicate entries`() {
        val c = cycle()
        c.recordFailure("a", "x")
        c.recordFailure("a", "x")
        assertEquals(listOf("a"), c.failures())
    }
}
