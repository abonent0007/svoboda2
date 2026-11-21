package svoboda3.runet.abonent0007.vpn

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
        t.clearAllHealth()
        assertFalse("clearAllHealth stops current job", j2.isActive)
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
        t.clearAllReconnect()
        assertFalse("clearAllReconnect stops current job", j2.isActive)
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
        t.clearAllHealth()
        assertTrue(t.activeReconnect)
        t.clearAllReconnect()
        assertFalse(t.activeHealth)
        assertFalse(t.activeReconnect)
        j1.cancel(); j2.cancel()
    }

    @Test
    fun `clearHealthIfCurrent does not clear newer ownership`() {
        val t = SingleJobTracker()
        val a = kotlinx.coroutines.Job()
        val b = kotlinx.coroutines.Job()
        t.setHealth(a)
        t.setHealth(b)           // B теперь владеет слотом; A отменена изнутри setHealth
        assertFalse("foreign/completed job must not clear newer holder", t.clearHealthIfCurrent(a))
        assertTrue("current owner can clear itself", t.clearHealthIfCurrent(b))
        assertFalse(t.activeHealth)
    }

    @Test
    fun `clearAllHealth clears whatever is tracked`() {
        val t = SingleJobTracker()
        val a = kotlinx.coroutines.Job()
        t.setHealth(a)
        t.clearAllHealth()
        assertFalse(t.activeHealth)
        t.clearAllHealth()   // повтор — no-op, не падает
    }

    @Test
    fun `clearIfCurrent does not touch job registered after snapshot`() {
        val t = SingleJobTracker()
        // Старая ветка делает снимок до появления нового владельца.
        val snapshot = t.snapshotHealth()
        assertTrue("snapshot was empty", snapshot == null)
        val b = kotlinx.coroutines.Job()
        t.setHealth(b)                       // новый владелец регистрируется ПОСЛЕ снимка
        // Старая ветка пытается очистить по своему снимку (null) — ничего не трогаем.
        snapshot?.let { t.clearHealthIfCurrent(it) }
        assertTrue("newer job must survive stale clear", t.activeHealth)
    }

    @Test
    fun `clearIfCurrent with old snapshot keeps newer job`() {
        val t = SingleJobTracker()
        val a = kotlinx.coroutines.Job()
        t.setHealth(a)
        val snapshot = t.snapshotHealth()      // снимок A
        val b = kotlinx.coroutines.Job()
        t.setHealth(b)                         // B заменил A
        assertFalse("stale clear must not cancel successor", t.clearHealthIfCurrent(a))
        assertTrue("newer job stays active", t.activeHealth)
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
        t.clearAllHealth()
        assertFalse(t.activeHealth)
    }
}
