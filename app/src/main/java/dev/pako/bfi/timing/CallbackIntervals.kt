package dev.pako.bfi.timing

/** Bounded recent callback intervals. This measures app callbacks, never physical refresh. */
class CallbackIntervals(private val capacity: Int = 256) {
    init { require(capacity >= 16) }
    private val intervals = LongArray(capacity)
    private var count = 0
    private var index = 0
    private var previousTime = -1L

    @Synchronized
    fun record(timeNanos: Long) {
        if (previousTime >= 0 && timeNanos > previousTime) {
            intervals[index] = timeNanos - previousTime
            index = (index + 1) % capacity
            count = (count + 1).coerceAtMost(capacity)
        }
        previousTime = timeNanos
    }

    @Synchronized
    fun snapshot(): Snapshot {
        if (count < 16) return Snapshot(0.0, 0.0, count)
        val sorted = intervals.copyOf(count)
        sorted.sort() // Only at end of trial, outside the render hot path.
        val middle = count / 2
        val median = if (count % 2 == 0)
            sorted[middle - 1].toDouble() + (sorted[middle] - sorted[middle - 1]) / 2.0
        else sorted[middle].toDouble()
        return Snapshot(1_000_000_000.0 / median, median / 1_000_000.0, count)
    }

    data class Snapshot(val estimatedHz: Double, val medianMillis: Double, val samples: Int)
}
