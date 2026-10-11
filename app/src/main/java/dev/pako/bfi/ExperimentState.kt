package dev.pako.bfi

/** Bounded diagnostic value object; enabled state lives exclusively in BfiController. */
data class TrialSummary(
    val callbacks: Long,
    val posts: Long,
    val skipped: Long,
    val late: Long,
    val meanIntervalMillis: Double,
    val recentCallbackCadenceHz: Double,
    val recentMedianMillis: Double,
    val recentIntervalSamples: Int
)
