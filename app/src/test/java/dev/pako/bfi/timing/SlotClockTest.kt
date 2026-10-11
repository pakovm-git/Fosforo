package dev.pako.bfi.timing

import org.junit.Assert.*
import org.junit.Test

class SlotClockTest {
    @Test fun skipsAdvanceByTimeWithoutCatchUp() {
        val clock = SlotClock(10)
        assertEquals(0L, clock.next(100))
        assertEquals(1L, clock.next(110))
        assertEquals(4L, clock.next(140))
        assertEquals(2L, clock.skippedSlots)
        assertFalse(SlotClock.isBlack(4))
    }
    @Test fun duplicateDoesNotSubmitOrMovePhase() {
        val clock = SlotClock(100)
        assertEquals(0L, clock.next(1000))
        assertEquals(-1L, clock.next(1020))
        assertEquals(1L, clock.next(1102))
        assertTrue(SlotClock.isBlack(1))
    }
    @Test fun backwardsAndLongPauseRequireNewEpoch() {
        val clock = SlotClock(100)
        clock.next(1000)
        assertEquals(-2L, clock.next(999))
        assertEquals(-3L, clock.next(2301))
        assertEquals(0L, SlotClock(100).next(2301))
    }
    @Test fun longerPatternHasContiguousBlackSlots() {
        assertFalse(SlotClock.isBlack(0, 1, 3))
        for (slot in 1L..3L) assertTrue(SlotClock.isBlack(slot, 1, 3))
        assertFalse(SlotClock.isBlack(4, 1, 3))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidPeriod() { SlotClock(0) }
    @Test fun everyPresetRepeatsWithExactVisibleAndBlackCounts() {
        for (black in listOf(1,2,3,5)) {
            for (slot in 0L..100L) assertEquals(slot%(1+black) >= 1,SlotClock.isBlack(slot,1,black))
        }
    }
    @Test fun groupedVisibleSlotsAreDifferentFromEqualDutyAlternation() {
        assertTrue(SlotClock.isBlack(1,1,1)); assertFalse(SlotClock.isBlack(1,2,2))
        val expected=listOf(false,false,false,true,true,false,false,false,true,true)
        assertEquals(expected,(0L..9L).map { SlotClock.isBlack(it,3,2) })
    }
    @Test fun skippedCallbackUsesAbsoluteCustomPatternSlot() {
        val c=SlotClock(100); assertEquals(0L,c.next(1000)); val slot=c.next(1400)
        assertEquals(4L,slot);assertEquals(3L,c.skippedSlots);assertTrue(SlotClock.isBlack(slot,3,2))
    }
}
