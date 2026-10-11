package dev.pako.bfi.model

data class BfiPattern(val visibleSlots: Int = 1, val blackSlots: Int = 1) {
    init { require(visibleSlots in 1..12 && blackSlots in 1..12) }
    val totalSlots get() = visibleSlots + blackSlots
    val blackFraction get() = blackSlots.toDouble() / totalSlots
    fun cyclesPerSecond(reportedHz: Double) = reportedHz / totalSlots
    companion object { val presets = listOf(BfiPattern(), BfiPattern(1, 2), BfiPattern(1, 3), BfiPattern(1, 5)) }
}
