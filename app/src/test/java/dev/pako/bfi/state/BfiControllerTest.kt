package dev.pako.bfi.state

import dev.pako.bfi.model.*
import org.junit.Assert.*
import org.junit.Test

class BfiControllerTest {
    @Test fun continuousHasNoExpiryAndSurvivesTenMinutes() {
        val c = BfiController()
        val effects = c.start(BfiSettings(), SessionMode.Continuous, 100)
        assertFalse(effects.any { it is Effect.Timeout }); assertNull(c.state.deadlineUptimeMillis)
        c.firstFrame(c.state.epoch)
        assertTrue(c.timeout(c.state.session, 700_100).isEmpty())
        assertTrue(c.state.userEnabled); assertEquals(Phase.RUNNING, c.state.phase)
    }
    @Test fun staleTimedDeadlineCannotStopNewContinuous() {
        val c = BfiController(); val effects = c.start(BfiSettings(), SessionMode.Timed(10), 100)
        val timer = effects.filterIsInstance<Effect.Timeout>().single()
        assertEquals(10_100, timer.deadline); c.stop(); c.start(BfiSettings(), SessionMode.Continuous, 200)
        assertTrue(c.timeout(timer.session, 20_000).isEmpty()); assertTrue(c.state.userEnabled)
    }
    @Test fun timedDeadlineIsAbsoluteAndExpiresOnce() {
        val c = BfiController(); c.start(BfiSettings(), SessionMode.Timed(2), 100)
        assertTrue(c.timeout(c.state.session, 2099).isEmpty())
        assertTrue(c.timeout(c.state.session, 2100).contains(Effect.RemoveRender))
        assertFalse(c.state.userEnabled); assertEquals(Reason.TIMEOUT, c.state.reason)
    }
    @Test fun stopInvalidatesFirstFrameAndRetryAndWake() {
        val c = BfiController(); c.start(BfiSettings(alwaysForce = true), SessionMode.Continuous, 0)
        val old = c.state.epoch; c.fail(old, Reason.SURFACE, true); val retry = c.state.epoch
        c.stop(); assertTrue(c.firstFrame(old).isEmpty()); assertFalse(c.accepts(retry))
        assertTrue(c.reconcile(true, null, BfiPattern(), retry = true).isEmpty()); assertFalse(c.state.userEnabled)
    }
    @Test fun sleepRemovesOutputAndWakeCreatesNewEpoch() {
        val c = BfiController(); c.start(BfiSettings(), SessionMode.Continuous, 0); val before = c.state.epoch
        assertEquals(listOf(Effect.RemoveRender), c.reconcile(false, null, BfiPattern()))
        assertEquals(Phase.SUSPENDED, c.state.phase); assertTrue(c.state.userEnabled)
        assertTrue(c.reconcile(true, null, BfiPattern()).any { it is Effect.Render }); assertFalse(c.accepts(before))
    }
    @Test fun sleepEndsTimedAndOptOutSessions() {
        for ((s,m) in listOf(BfiSettings() to SessionMode.Timed(3), BfiSettings(resumeAfterUnlock = false) to SessionMode.Continuous)) {
            val c = BfiController(); c.start(s,m,0); assertTrue(c.reconcile(false,null,BfiPattern()).contains(Effect.EndService)); assertFalse(c.state.userEnabled)
        }
    }
    @Test fun retriesAreFiniteAndUseExpectedDelays() {
        val c = BfiController(); c.start(BfiSettings(alwaysForce = true), SessionMode.Continuous, 0)
        for (delay in listOf(1000L,2000L,4000L,8000L,16000L)) {
            val effects = c.fail(c.state.epoch, Reason.TIMING, true)
            assertTrue(effects.contains(Effect.RemoveRender)); assertEquals(delay, effects.filterIsInstance<Effect.Retry>().single().delayMillis)
            c.reconcile(true,null,BfiPattern(), retry=true)
        }
        assertTrue(c.fail(c.state.epoch,Reason.TIMING,true).contains(Effect.EndService)); assertFalse(c.state.userEnabled)
    }
    @Test fun onlyUninterruptedRunningEpochResetsRetryBudget() {
        val c = BfiController(); c.start(BfiSettings(alwaysForce=true),SessionMode.Continuous,0)
        c.fail(c.state.epoch,Reason.SURFACE,true); c.reconcile(true,null,BfiPattern(),retry=true)
        c.firstFrame(c.state.epoch); val epoch=c.state.epoch
        c.reconcile(false,null,BfiPattern()); c.stable(epoch); assertEquals(1,c.state.retryCount)
        c.reconcile(true,null,BfiPattern()); c.firstFrame(c.state.epoch); c.stable(c.state.epoch); assertEquals(0,c.state.retryCount)
    }
    @Test fun permissionLossIsBlockedWithoutRetryAndGrantDoesNotRestart() {
        val c = BfiController(); c.start(BfiSettings(alwaysForce=true),SessionMode.Continuous,0)
        assertTrue(c.reconcile(true,Reason.USAGE,BfiPattern()).contains(Effect.EndService))
        assertEquals(Phase.BLOCKED,c.state.phase); assertTrue(c.reconcile(true,null,BfiPattern()).isEmpty())
    }
    @Test fun exclusionSuspendsWithoutSpendingRetryAndOverridesReplaceEpoch() {
        val c=BfiController(); c.start(BfiSettings(),SessionMode.Continuous,0); c.firstFrame(c.state.epoch)
        c.reconcile(true,null,null,Reason.EXCLUDED); assertEquals(0,c.state.retryCount)
        val p=BfiPattern(3,2); val effects=c.reconcile(true,null,p)
        assertEquals(p,c.state.effectivePattern); assertTrue(effects.any { it is Effect.Render })
    }
    @Test fun timedFailureNeverRetries() {
        val c=BfiController(); c.start(BfiSettings(alwaysForce=true),SessionMode.Timed(3),0)
        assertTrue(c.fail(c.state.epoch,Reason.SURFACE,true).contains(Effect.EndService)); assertFalse(c.state.userEnabled)
    }
}
