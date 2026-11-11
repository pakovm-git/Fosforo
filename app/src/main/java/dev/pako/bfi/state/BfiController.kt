package dev.pako.bfi.state

import dev.pako.bfi.model.*

/** Pure serialized state owner. Effects are applied only after the new snapshot is published. */
class BfiController {
    var state = BfiState()
        private set
    fun accepts(epoch: Epoch) = state.userEnabled && state.epoch == epoch
    fun start(settings: BfiSettings, mode: SessionMode, now: Long, problem: Reason? = null,deadline:Long?=null): List<Effect> {
        if (state.userEnabled) return emptyList()
        if (problem != null) return stop(problem, Phase.BLOCKED)
        if(deadline!=null && (mode !is SessionMode.Timed || now>=deadline))return stop(Reason.TIMEOUT)
        state = BfiState(true, Phase.STARTING, Reason.READY, mode, state.session + 1, state.generation + 1,
            settings, settings.globalPattern, startedUptimeMillis = now,
            deadlineUptimeMillis = (mode as? SessionMode.Timed)?.let { deadline ?: now + it.seconds * 1000L })
        return listOfNotNull(Effect.Render(state.epoch), state.deadlineUptimeMillis?.let { Effect.Timeout(state.session, it) })
    }
    fun stop(reason: Reason = Reason.MANUAL, phase: Phase = Phase.DISABLED): List<Effect> {
        state = state.copy(userEnabled = false, phase = phase, reason = reason, generation = state.generation + 1, deadlineUptimeMillis = null, requestOutcome = RequestOutcome.OFF)
        return listOf(Effect.RemoveRender, Effect.EndService)
    }
    /** Apply committed edits without replacing the session or extending a timed deadline. */
    fun updateSettings(settings: BfiSettings): List<Effect> {
        if (!state.userEnabled || settings == state.settings) return emptyList()
        val old=state.settings
        val rebuild=settings.globalPattern != old.globalPattern || settings.activationMode != old.activationMode ||
            settings.includedPackages != old.includedPackages || settings.excludedPackages != old.excludedPackages ||
            settings.overrides != old.overrides || settings.refreshTarget != old.refreshTarget || settings.requestRefresh != old.requestRefresh
        state=state.copy(settings=settings)
        if (state.phase == Phase.RETRY_WAIT && !settings.alwaysForce) return stop(state.reason,Phase.FAILED)
        if (rebuild && (state.phase == Phase.STARTING || state.phase == Phase.RUNNING)) {
            state=state.copy(phase=Phase.SUSPENDED,reason=Reason.SETTINGS,generation=state.generation+1,requestOutcome=RequestOutcome.OFF)
            return listOf(Effect.RemoveRender)
        }
        return if (!old.alwaysForce && settings.alwaysForce && state.phase == Phase.RUNNING) listOf(Effect.Stable(state.epoch)) else emptyList()
    }
    fun timeout(session: Long, now: Long): List<Effect> =
        if (state.userEnabled && state.session == session && state.mode is SessionMode.Timed && now >= (state.deadlineUptimeMillis ?: Long.MAX_VALUE)) stop(Reason.TIMEOUT)
        else emptyList()
    fun firstFrame(epoch: Epoch): List<Effect> {
        if (!accepts(epoch) || state.phase != Phase.STARTING) return emptyList()
        state = state.copy(phase = Phase.RUNNING, reason = Reason.READY)
        return if (state.mode == SessionMode.Continuous && state.settings.alwaysForce) listOf(Effect.Stable(epoch)) else emptyList()
    }
    fun stable(epoch: Epoch) {
        if (accepts(epoch) && state.phase == Phase.RUNNING && state.settings.alwaysForce) state = state.copy(retryCount = 0)
    }
    fun observed(epoch: Epoch, hz: Double, outcome: RequestOutcome) {
        if (accepts(epoch)) state = state.copy(reportedHz = hz, requestOutcome = outcome)
    }
    fun fail(epoch: Epoch, reason: Reason, recoverable: Boolean): List<Effect> {
        if (!accepts(epoch)) return emptyList()
        if (!recoverable) return stop(reason, Phase.BLOCKED)
        if (state.mode != SessionMode.Continuous || !state.settings.alwaysForce) return stop(reason, Phase.FAILED)
        if (state.retryCount >= 5) return stop(Reason.RETRIES, Phase.FAILED)
        val count = state.retryCount + 1
        state = state.copy(phase = Phase.RETRY_WAIT, reason = reason, retryCount = count, generation = state.generation + 1, requestOutcome = RequestOutcome.OFF)
        return listOf(Effect.RemoveRender, Effect.Retry(state.epoch, (1L shl (count - 1)) * 1000L))
    }
    fun reconcile(awake: Boolean, problem: Reason?, pattern: BfiPattern?, ruleReason: Reason = Reason.UNKNOWN_APP, retry: Boolean = false): List<Effect> {
        if (!state.userEnabled) return emptyList()
        // Sleeping displays may drop their reported rate or temporarily lack a usable mode.
        // Permission/storage failures still clear intent; wake revalidates the display.
        val displayMaySleep = problem == Reason.LOW_RATE || problem == Reason.NO_DISPLAY || problem == Reason.TARGET_UNAVAILABLE
        if (problem != null && (awake || !displayMaySleep)) return stop(problem, Phase.BLOCKED)
        if (!awake) {
            if (state.mode is SessionMode.Timed || !state.settings.resumeAfterUnlock) return stop(Reason.SLEEP)
            return suspend(Reason.SLEEP)
        }
        if (pattern == null) return suspend(ruleReason)
        if (state.phase == Phase.RETRY_WAIT && !retry) return emptyList()
        if (state.phase == Phase.SUSPENDED || state.phase == Phase.RETRY_WAIT || pattern != state.effectivePattern) {
            state = state.copy(phase = Phase.STARTING, reason = Reason.READY, effectivePattern = pattern, generation = state.generation + 1)
            return listOf(Effect.RemoveRender, Effect.Render(state.epoch))
        }
        return emptyList()
    }
    private fun suspend(reason: Reason): List<Effect> {
        if (state.phase == Phase.SUSPENDED && state.reason == reason) {
            if (reason == Reason.CONTROLS) state=state.copy(effectivePattern=state.settings.globalPattern)
            return emptyList()
        }
        state = state.copy(phase = Phase.SUSPENDED, reason = reason, generation = state.generation + 1, requestOutcome = RequestOutcome.OFF,
            effectivePattern=if (reason == Reason.CONTROLS) state.settings.globalPattern else state.effectivePattern)
        return listOf(Effect.RemoveRender)
    }
}
