package svoboda.runet.abonent0007.awg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AwgQuicMaskTest {

    @Test
    fun generatesAmneziaBlob() {
        val blob = AwgQuicMask.generate("akbars.ru")
        assertTrue("начинается с <b 0x: $blob", blob.startsWith("<b 0x"))
        assertTrue("содержит пропуски <r>: $blob", blob.contains("<r "))
        assertTrue("достаточно длинная: ${blob.length}", blob.length > 100)
    }

    @Test
    fun sniAffectsResult() {
        val a = AwgQuicMask.generate("akbars.ru")
        val b = AwgQuicMask.generate("ivi.ru")
        assertNotEquals(a, b)
    }

    @Test
    fun levelsAreStableShapes() {
        for (level in 0..4) {
            val blob = AwgQuicMask.generate("sdamgia.ru", level)
            assertTrue("level $level", blob.startsWith("<b 0x") || blob.contains("<r "))
        }
    }

    @Test
    fun emptySniDoesNotCrash() {
        val blob = AwgQuicMask.generate("")
        assertTrue(blob.startsWith("<b 0x"))
    }
}
