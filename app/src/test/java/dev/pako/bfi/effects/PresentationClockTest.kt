package dev.pako.bfi.effects

import org.junit.Assert.*
import org.junit.Test

class PresentationClockTest {
    @Test fun firstPresentationAnchorsPhaseAndJitterDoesNotDuplicateSlots() {
        val clock = PresentationClock(100.0)
        assertEquals(0L, clock.slot(10_050))
        assertEquals(1L, clock.slot(10_149))
        assertEquals(2L, clock.slot(10_251))
        assertEquals(3L, clock.slot(10_350))
    }
    @Test fun missedCallbacksAdvanceByPresentationTime() {
        val clock = PresentationClock(100.0)
        clock.slot(1000)
        assertEquals(4L, clock.slot(1400))
        assertTrue(BfiPattern.parse("1,1,2,1")!!.blackAt(clock.slot(1400)))
    }
    @Test fun periodMustBeFiniteAndPositive() {
        listOf(0.0,-1.0,Double.NaN,Double.POSITIVE_INFINITY).forEach {
            assertThrows(IllegalArgumentException::class.java) { PresentationClock(it) }
        }
    }
}
