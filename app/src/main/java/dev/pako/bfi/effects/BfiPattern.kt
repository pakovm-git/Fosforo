package dev.pako.bfi.effects

class BfiPattern private constructor(runs: List<Int>) {
    val runs: List<Int> = java.util.Collections.unmodifiableList(runs.toList())
    val encoded = runs.joinToString(", ")
    val summary = if (runs[1] == 0) "All visible · no black frames" else runs.mapIndexed { index, frames ->
        "${if (index % 2 == 0) "Visible" else "Black"} $frames"
    }.joinToString(" → ")
    private val frames = runs.sum()

    internal fun blackAt(slot: Long): Boolean {
        var remaining = slot.coerceAtLeast(0) % frames
        runs.forEachIndexed { index, count ->
            if (remaining < count) return index % 2 == 1
            remaining -= count
        }
        return false
    }

    companion object {
        fun parse(text: String): BfiPattern? {
            val parts = text.split(',')
            if (parts.size !in 2..32 || parts.size % 2 != 0) return null
            val runs = parts.map { it.trim().toIntOrNull()?.takeIf { count -> count in 1..120 } ?: return null }
            return BfiPattern(runs)
        }
        fun legacy(visible: Int, black: Int) = BfiPattern(listOf(visible.coerceIn(1,120), black.coerceIn(0,120)))
    }
}

fun blackSlot(timeNanos: Long, startNanos: Long, hz: Double, pattern: BfiPattern): Boolean {
    if (!hz.isFinite() || hz <= 0) return false
    val slot = ((timeNanos - startNanos).coerceAtLeast(0) * hz / 1e9).toLong()
    return pattern.blackAt(slot)
}
