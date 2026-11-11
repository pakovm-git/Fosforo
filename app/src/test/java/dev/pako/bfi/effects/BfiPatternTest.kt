package dev.pako.bfi.effects

import org.junit.Assert.*
import org.junit.Test

class BfiPatternTest {
    @Test fun requestedSequenceAlternatesRunsAndRepeats() {
        val pattern = BfiPattern.parse("1, 1, 2, 1")!!
        assertEquals(listOf(false,true,false,false,true,false,true,false,false,true), (0L..9L).map { pattern.blackAt(it) })
        assertEquals("1, 1, 2, 1", pattern.encoded)
        assertEquals("Visible 1 → Black 1 → Visible 2 → Black 1", pattern.summary)
    }
    @Test fun invalidIncompleteAndAllBlackEditsAreRejected() {
        listOf("", "1", "1,", "1,1,2", "1,0", "0,1", "-1,1", "121,1", "1.5,1", "1,1,,1", List(34){"1"}.joinToString(",")).forEach {
            assertNull(it, BfiPattern.parse(it))
        }
        assertNotNull(BfiPattern.parse(List(32){"120"}.joinToString(",")))
    }
    @Test fun legacyZeroBlackFramesStayAllVisible() {
        val pattern = BfiPattern.legacy(2,0)
        assertTrue((0L..100L).none { pattern.blackAt(it) })
        assertEquals("2, 0",pattern.encoded)
        assertFalse(BfiPattern.legacy(0,0).blackAt(0))
    }
    @Test fun timingUsesElapsedRefreshSlotsAndHandlesBadClocks() {
        val pattern = BfiPattern.parse("1,1,2,1")!!
        assertFalse(blackSlot(0,0,100.0,pattern))
        assertTrue(blackSlot(10_000_000,0,100.0,pattern))
        assertFalse(blackSlot(30_000_000,0,100.0,pattern))
        assertTrue(blackSlot(40_000_000,0,100.0,pattern))
        assertFalse(blackSlot(50_000_000,0,100.0,pattern))
        assertFalse(blackSlot(0,100,100.0,pattern))
        assertFalse(blackSlot(10_000_000,0,Double.NaN,pattern))
        assertFalse(blackSlot(10_000_000,0,0.0,pattern))
    }
    @Test fun simplePatternMatchesLegacyCadence() {
        val pattern = BfiPattern.legacy(2,3)
        (0L..100L).forEach { time ->
            assertEquals(dev.pako.bfi.timing.SlotClock.isBlack(time,2,3),blackSlot(time*10_000_000,0,100.0,pattern))
        }
    }
}
