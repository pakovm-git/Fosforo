package dev.pako.bfi

import android.content.Context
import dev.pako.bfi.model.BfiPattern
import dev.pako.bfi.state.*
import java.text.NumberFormat

object UiText {
    fun integer(value: Number): String = NumberFormat.getIntegerInstance().format(value)
    fun decimal(value: Double): String = NumberFormat.getNumberInstance().apply { maximumFractionDigits=2 }.format(value)
    fun pattern(context: Context,p: BfiPattern) = context.getString(R.string.pattern_counts,integer(p.visibleSlots),integer(p.blackSlots))
    fun fraction(p: BfiPattern) = NumberFormat.getPercentInstance().apply { maximumFractionDigits=1 }.format(p.blackFraction)
    fun phase(context: Context,phase: Phase) = context.getString(when (phase) {
        Phase.DISABLED -> R.string.state_disabled; Phase.STARTING -> R.string.state_starting; Phase.RUNNING -> R.string.state_running
        Phase.SUSPENDED -> R.string.state_suspended; Phase.RETRY_WAIT -> R.string.state_retry; Phase.BLOCKED -> R.string.state_blocked; Phase.FAILED -> R.string.state_failed
    })
    fun reason(context: Context,reason: Reason) = context.getString(when (reason) {
        Reason.ACCESSIBILITY -> R.string.reason_accessibility; Reason.CAPTURE -> R.string.reason_capture; Reason.NO_EFFECT -> R.string.reason_no_effect; Reason.UNSUPPORTED -> R.string.reason_unsupported;
        Reason.READY -> R.string.reason_ready; Reason.MANUAL -> R.string.reason_manual; Reason.TIMEOUT -> R.string.reason_timeout; Reason.SLEEP -> R.string.reason_sleep
        Reason.CONTROLS -> R.string.reason_controls; Reason.EXCLUDED -> R.string.reason_excluded; Reason.UNKNOWN_APP -> R.string.reason_unknown; Reason.OVERLAY -> R.string.reason_permission
        Reason.NOTIFICATION -> R.string.reason_notification; Reason.USAGE -> R.string.reason_usage; Reason.LOW_RATE -> R.string.reason_low_rate
        Reason.NO_DISPLAY -> R.string.reason_no_display; Reason.SETTINGS -> R.string.reason_settings
        Reason.EMPTY_SELECTED -> R.string.empty_selected; Reason.TARGET_UNAVAILABLE -> R.string.reason_target; Reason.SERVICE -> R.string.reason_start_failed
        Reason.SURFACE -> R.string.reason_surface; Reason.STALLED -> R.string.reason_stalled; Reason.TIMING -> R.string.reason_unstable
        Reason.DISPLAY -> R.string.reason_display; Reason.BUSY -> R.string.reason_busy; Reason.RETRIES -> R.string.reason_retries
    })
    fun outcome(context: Context,outcome: RequestOutcome) = context.getString(when(outcome) {
        RequestOutcome.OFF -> R.string.request_off; RequestOutcome.SETTLING -> R.string.request_settling; RequestOutcome.APPLIED -> R.string.request_applied
        RequestOutcome.OVERRIDDEN -> R.string.request_overridden; RequestOutcome.UNAVAILABLE -> R.string.request_unavailable
    })
}
