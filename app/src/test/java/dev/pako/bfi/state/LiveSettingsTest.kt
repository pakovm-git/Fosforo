package dev.pako.bfi.state

import dev.pako.bfi.model.*
import org.junit.Assert.*
import org.junit.Test

class LiveSettingsTest {
    @Test fun livePatternChangeInvalidatesOldCallbacksWithoutExtendingTimedDeadline() {
        val c=BfiController(); c.start(BfiSettings(),SessionMode.Timed(10),100); c.firstFrame(c.state.epoch)
        val old=c.state.epoch; val session=c.state.session; val deadline=c.state.deadlineUptimeMillis
        assertEquals(listOf(Effect.RemoveRender),c.updateSettings(c.state.settings.copy(globalPattern=BfiPattern(3,2))))
        assertFalse(c.accepts(old)); assertTrue(c.firstFrame(old).isEmpty())
        val effects=c.reconcile(true,null,BfiPattern(3,2))
        assertTrue(effects.any { it is Effect.Render }); assertEquals(session,c.state.session)
        assertEquals(deadline,c.state.deadlineUptimeMillis); assertTrue(c.state.userEnabled)
        c.timeout(session,10_100); assertFalse(c.state.userEnabled)
    }
    @Test fun controlsPauseSystemOutputButKeepIntentAndLatestLocalPattern() {
        val c=BfiController(); c.start(BfiSettings(),SessionMode.Continuous,0); val old=c.state.epoch
        c.firstFrame(old); c.reconcile(true,null,null,Reason.CONTROLS)
        assertEquals(Phase.SUSPENDED,c.state.phase); assertTrue(c.state.userEnabled); assertFalse(c.accepts(old))
        c.updateSettings(c.state.settings.copy(globalPattern=BfiPattern(2,3)))
        c.reconcile(true,null,null,Reason.CONTROLS)
        assertEquals(BfiPattern(2,3),c.state.effectivePattern); assertEquals(0,c.state.retryCount)
        assertTrue(c.reconcile(true,null,BfiPattern(2,3)).any { it is Effect.Render })
    }
    @Test fun selectedScopeSwitchDoesNotRenderInUnselectedApps() {
        val c=BfiController(); c.start(BfiSettings(),SessionMode.Continuous,0); c.firstFrame(c.state.epoch)
        c.updateSettings(c.state.settings.copy(activationMode=ActivationMode.SELECTED_APPS,includedPackages=setOf("game.one")))
        val effects=c.reconcile(true,null,null,Reason.EXCLUDED)
        assertFalse(effects.any { it is Effect.Render }); assertTrue(c.state.userEnabled)
        assertEquals(Reason.EXCLUDED,c.state.reason)
    }
    @Test fun refreshVoteEditsRecreateRendererEvenWhenPatternIsUnchanged() {
        val c=BfiController(); c.start(BfiSettings(),SessionMode.Continuous,0); c.firstFrame(c.state.epoch)
        val old=c.state.epoch
        c.updateSettings(c.state.settings.copy(requestRefresh=true,refreshTarget=RefreshTarget.Numeric(120.0)))
        assertTrue(c.reconcile(true,null,BfiPattern()).any { it is Effect.Render }); assertFalse(c.accepts(old))
    }
    @Test fun diagnosticAndDurationPreferencesDoNotInterruptCurrentOutput() {
        val c=BfiController(); c.start(BfiSettings(),SessionMode.Timed(10),0); c.firstFrame(c.state.epoch)
        val epoch=c.state.epoch
        assertTrue(c.updateSettings(c.state.settings.copy(diagnosticsEnabled=true,lastTestSeconds=600)).isEmpty())
        assertEquals(epoch,c.state.epoch); assertEquals(10_000L,c.state.deadlineUptimeMillis)
    }
    @Test fun saveCompletionAfterStopCannotReactivateOutput() {
        val c=BfiController(); c.start(BfiSettings(),SessionMode.Continuous,0); c.stop()
        assertTrue(c.updateSettings(BfiSettings(globalPattern=BfiPattern(12,12))).isEmpty())
        assertTrue(c.reconcile(true,null,BfiPattern(12,12)).isEmpty()); assertFalse(c.state.userEnabled)
    }
    @Test fun turningOffRecoveryCancelsAlreadyQueuedRetry() {
        val c=BfiController(); c.start(BfiSettings(alwaysForce=true),SessionMode.Continuous,0)
        c.fail(c.state.epoch,Reason.STALLED,true); val old=c.state.epoch
        assertTrue(c.updateSettings(c.state.settings.copy(alwaysForce=false)).contains(Effect.EndService))
        assertFalse(c.accepts(old)); assertFalse(c.state.userEnabled)
        assertTrue(c.reconcile(true,null,BfiPattern(),retry=true).isEmpty())
    }
}
