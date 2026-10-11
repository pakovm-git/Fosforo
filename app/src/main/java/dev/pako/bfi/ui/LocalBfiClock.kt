package dev.pako.bfi.ui

import dev.pako.bfi.model.BfiPattern
import dev.pako.bfi.timing.SlotClock
import kotlin.math.roundToLong

/** Shared absolute vsync phase for the filtered ball and every in-app mask. */
object LocalBfiClock {
    fun isBlack(frameTimeNanos: Long,hz: Double,pattern: BfiPattern): Boolean {
        val rate=hz.takeIf { it.isFinite() && it > 0 } ?: 60.0
        val slot=(frameTimeNanos.coerceAtLeast(0).toDouble()*rate/1_000_000_000.0).roundToLong()
        return SlotClock.isBlack(slot,pattern.visibleSlots,pattern.blackSlots)
    }
}
