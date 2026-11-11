package dev.pako.bfi.effects

import kotlin.math.roundToLong

internal class PresentationClock(private val periodNanos: Double) {
    init { require(periodNanos.isFinite() && periodNanos > 0) }
    private var anchor: Long? = null
    fun slot(targetNanos: Long): Long {
        val start = anchor ?: targetNanos.also { anchor = it }
        return ((targetNanos - start) / periodNanos).roundToLong().coerceAtLeast(0)
    }
}
