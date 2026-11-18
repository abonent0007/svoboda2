package svoboda.runet.abonent0007.vpn

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0: дедупликация health/reconnect-задач координатора.
 */
class SingleJobTrackerTest {

    @Test
    fun `new health job cancels previous`() = runBlocking {
        val t = SingleJobTracker()
        val j1 = launch { delay(10_000) }
        t.setHealth(j1)
        assertTrue(t.activeHealth)
        val j2 = launch { delay(10_000) }
        t.setHealth(j2)           // предыдущий отменяется
        assertFalse("old health job must be cancelled", j1.isActive)
        assertTrue("new health job is registered", t.activeHealth)
        t.clearHealth()
        assertFalse("clearHealth stops current job", j2.isActive)
        assertFalse(t.activeHealth)
    }

    @Test
    fun `new reconnect job cancels previous`() = runBlocking {
        val t = SingleJobTracker()
        val j1 = launch { delay(10_000) }
        t.setReconnect(j1)
        assertTrue(t.activeReconnect)
        val j2 = launch { delay(10_000) }
        t.setReconnect(j2)
        assertFalse("old reconnect job must be cancelled", j1.isActive)
        assertTrue(t.activeReconnect)
        t.clearReconnect()
        assertFalse("clearReconnect stops current job", j2.isActive)
        assertFalse(t.activeReconnect)
    }

    @Test
    fun `health and reconnect slots independent`() = runBlocking {
        val t = SingleJobTracker()
        val j1 = launch { delay(10_000) }
        val j2 = launch { delay(10_000) }
        t.setHealth(j1)
        t.setReconnect(j2)
        assertTrue(t.activeHealth)
        assertTrue(t.activeReconnect)
        t.clearHealth()
        assertTrue(t.activeReconnect)
        t.clearReconnect()
        assertFalse(t.activeHealth)
        assertFalse(t.activeReconnect)
        j1.cancel(); j2.cancel()
    }

    @Test
    fun `parallel health replacements never leave tracker empty mid-flight`() {
        val t = SingleJobTracker()
        val workers = (1..12).map {
            Thread {
                repeat(30) { t.setHealth(kotlinx.coroutines.Job()) }
            }.apply { start() }
        }
        workers.forEach { it.join() }
        // Последняя установленная задача всё ещё в трекере и жива.
        assertTrue(t.activeHealth)
        t.clearHealth()
        assertFalse(t.activeHealth)
    }
}
