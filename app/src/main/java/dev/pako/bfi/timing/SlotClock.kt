package dev.pako.bfi.timing

import kotlin.math.roundToLong

/** Slot selection from a fixed epoch, never from the number of callbacks received. */
class SlotClock(private val periodNanos: Long) {
    init { require(periodNanos > 0) }
    private var anchor = -1L
    private var previousTime = -1L
    private var lastSlot = -1L
    var skippedSlots = 0L
        private set

    /** >=0: new slot; -1: duplicate; -2: backwards time; -3: excessive gap. */
    fun next(timeNanos: Long): Long {
        require(timeNanos >= 0)
        if (anchor < 0) anchor = timeNanos
        if (previousTime >= 0 && timeNanos < previousTime) return -2
        if (previousTime >= 0 && timeNanos - previousTime > periodNanos * 12) return -3
        previousTime = timeNanos
        val slot = ((timeNanos - anchor).toDouble() / periodNanos).roundToLong()
        if (slot <= lastSlot) return -1
        if (lastSlot >= 0) skippedSlots += (slot - lastSlot - 1).coerceAtLeast(0)
        lastSlot = slot
        return slot
    }

    companion object {
        fun isBlack(slot: Long, visible: Int = 1, black: Int = 1): Boolean {
            require(slot >= 0 && visible in 1..12 && black in 1..12)
            return slot % (visible + black) >= visible
        }
    }
}
