package svoboda3.runet.abonent0007.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

/** AntiBounceGate: границы окна удержания, bypass, пустой сессией — без Android. */
class AntiBounceGateTest {

    private val hold = 5 // 5 минут

    @Test
    fun `bypass always wins`() {
        assertEquals(Proceed.BypassHold, AntiBounceGate.proceed(true, true, 0L, 10_000L, hold))
        assertEquals(Proceed.BypassHold, AntiBounceGate.proceed(true, false, 0L, 10_000L, 0))
        assertEquals(Proceed.BypassHold, AntiBounceGate.proceed(true, true, 9_999L, 10_000L, 60))
    }

    @Test
    fun `no healthy session proceeds immediately`() {
        assertEquals(Proceed.ProceedNow, AntiBounceGate.proceed(false, false, 1_000L, 2_000L, hold))
    }

    @Test
    fun `zero or negative hold proceeds immediately`() {
        assertEquals(Proceed.ProceedNow, AntiBounceGate.proceed(false, true, 1_000L, 2_000L, 0))
        assertEquals(Proceed.ProceedNow, AntiBounceGate.proceed(false, true, 1_000L, 2_000L, -1))
    }

    @Test
    fun `exactly at hold boundary proceeds`() {
        val last = 1_000_000L
        val now = last + hold * 60_000L // ровно через 5 минут
        assertEquals(Proceed.ProceedNow, AntiBounceGate.proceed(false, true, last, now, hold))
    }

    @Test
    fun `one ms before hold boundary defers`() {
        val last = 1_000_000L
        val now = last + hold * 60_000L - 1
        assertEquals(Proceed.Deferred, AntiBounceGate.proceed(false, true, last, now, hold))
    }

    @Test
    fun `deep inside hold defers`() {
        val last = 1_000_000L
        assertEquals(Proceed.Deferred, AntiBounceGate.proceed(false, true, last, last + 10_000L, hold))
    }
}
