package dev.pako.bfi.state

/** Main-thread controller token: a late result cannot resurrect a stopped session. */
class SessionGuard {
    private var generation = 0L
    var enabled = false
        private set

    fun begin(): Long {
        generation++
        enabled = true
        return generation
    }

    fun stop() {
        enabled = false
        generation++
    }

    fun accepts(token: Long) = enabled && token == generation
}
