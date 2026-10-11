package dev.pako.bfi.ui

import dev.pako.bfi.model.BfiPattern
import org.junit.Assert.*
import org.junit.Test

class LocalBfiClockTest {
    @Test fun sameVsyncTimestampKeepsPreviewAndMaskInPhaseAcrossDifferentAttachmentTimes() {
        val pattern=BfiPattern(2,3)
        val frames=(0..19).map { 900_000_000_000L+it*10_000_000L }
        val preview=frames.map { LocalBfiClock.isBlack(it,100.0,pattern) }
        val maskAttachedLater=frames.drop(7).map { LocalBfiClock.isBlack(it,100.0,pattern) }
        assertEquals(preview.drop(7),maskAttachedLater)
        assertEquals(12,preview.count { it })
    }
    @Test fun callbackJitterDoesNotMoveBoundaryToAdjacentSlot() {
        val p=BfiPattern()
        assertFalse(LocalBfiClock.isBlack(9_900_000L,100.0,p.copy(visibleSlots=2)))
        assertTrue(LocalBfiClock.isBlack(19_900_000L,100.0,p.copy(visibleSlots=2)))
        assertTrue(LocalBfiClock.isBlack(20_100_000L,100.0,p.copy(visibleSlots=2)))
    }
}
