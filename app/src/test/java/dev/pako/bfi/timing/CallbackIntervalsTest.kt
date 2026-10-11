package dev.pako.bfi.timing

import org.junit.Assert.*
import org.junit.Test

class CallbackIntervalsTest {
    @Test fun isolatedMissingCallbackDoesNotBiasMedianLikeMean() {
        val stats = CallbackIntervals()
        var now = 0L
        stats.record(now)
        repeat(100) {
            now += if (it == 40) 16_666_666L else 8_333_333L
            stats.record(now)
        }
        assertEquals(120.0, stats.snapshot().estimatedHz, 0.0001)
    }
    @Test fun sustainedThrottlingRemainsVisible() {
        val stats = CallbackIntervals(16)
        var now = 0L
        stats.record(now)
        repeat(32) { now += 16_666_667L; stats.record(now) }
        assertEquals(60.0, stats.snapshot().estimatedHz, 0.0001)
        assertEquals(16, stats.snapshot().samples)
    }
    @Test fun smallRateDifferenceCanBeReportedWithoutRescheduling() {
        val stats = CallbackIntervals()
        var now = 0L
        stats.record(now)
        repeat(20) { now += 8_340_000L; stats.record(now) }
        assertEquals(119.9040767, stats.snapshot().estimatedHz, 0.0001)
    }
    @Test fun shortSampleDoesNotClaimAUsefulEstimate() {
        val stats = CallbackIntervals()
        stats.record(100)
        stats.record(200)
        assertEquals(0.0, stats.snapshot().estimatedHz, 0.0)
        assertEquals(1, stats.snapshot().samples)
    }
}
