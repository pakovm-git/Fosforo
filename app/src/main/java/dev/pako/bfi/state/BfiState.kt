package dev.pako.bfi.state

import dev.pako.bfi.model.*

enum class Phase { DISABLED, STARTING, RUNNING, SUSPENDED, RETRY_WAIT, BLOCKED, FAILED }
enum class Reason { READY, MANUAL, TIMEOUT, SLEEP, CONTROLS, EXCLUDED, UNKNOWN_APP, OVERLAY, NOTIFICATION, USAGE,
    ACCESSIBILITY, CAPTURE, NO_EFFECT, UNSUPPORTED, LOW_RATE, NO_DISPLAY, SETTINGS, NOTICE, EMPTY_SELECTED, TARGET_UNAVAILABLE, SERVICE, SURFACE, STALLED, TIMING, DISPLAY, BUSY, RETRIES }
enum class RequestOutcome { OFF, SETTLING, APPLIED, OVERRIDDEN, UNAVAILABLE }
data class Epoch(val session: Long, val generation: Long)
data class BfiState(
    val userEnabled: Boolean = false, val phase: Phase = Phase.DISABLED, val reason: Reason = Reason.READY,
    val mode: SessionMode = SessionMode.Continuous, val session: Long = 0, val generation: Long = 0,
    val settings: BfiSettings = BfiSettings(), val effectivePattern: BfiPattern = BfiPattern(),
    val retryCount: Int = 0, val deadlineUptimeMillis: Long? = null, val startedUptimeMillis: Long = 0,
    val reportedHz: Double = 0.0, val requestOutcome: RequestOutcome = RequestOutcome.OFF
) { val epoch get() = Epoch(session, generation) }
sealed interface Effect {
    data class Render(val epoch: Epoch) : Effect
    data object RemoveRender : Effect
    data object EndService : Effect
    data class Timeout(val session: Long, val deadline: Long) : Effect
    data class Retry(val epoch: Epoch, val delayMillis: Long) : Effect
    data class Stable(val epoch: Epoch) : Effect
}
