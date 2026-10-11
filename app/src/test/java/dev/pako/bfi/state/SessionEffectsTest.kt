package dev.pako.bfi.state

import dev.pako.bfi.model.*
import org.junit.Assert.*
import org.junit.Test

/** A fake clock/scheduler exercises effects and resource ownership, including callbacks already dequeued at Stop. */
class SessionEffectsTest {
    private class Harness {
        val c=BfiController()
        var now=0L
        var output: Epoch?=null
        var service=false
        val queued=mutableListOf<Pair<Long,() -> Unit>>()
        fun apply(effects: List<Effect>) {
            for (effect in effects) when(effect) {
                is Effect.Render -> if (c.accepts(effect.epoch)) { service=true; output=effect.epoch }
                Effect.RemoveRender -> output=null
                Effect.EndService -> { service=false; queued.clear() }
                is Effect.Timeout -> queued.add(effect.deadline to { apply(c.timeout(effect.session,now)) })
                is Effect.Retry -> queued.add(now+effect.delayMillis to { if(c.accepts(effect.epoch)) apply(c.reconcile(true,null,BfiPattern(),retry=true)) })
                is Effect.Stable -> queued.add(now+30_000 to { c.stable(effect.epoch) })
            }
        }
        fun start(mode: SessionMode,settings: BfiSettings=BfiSettings()) { apply(c.start(settings,mode,now)) }
        fun advance(to: Long) {
            while (true) {
                val task=queued.filter { it.first <= to }.minByOrNull { it.first } ?: break
                queued.remove(task); now=task.first; task.second()
            }
            now=to
        }
    }
    @Test fun continuousHasNoScheduledExpiryAndKeepsOneOutput() {
        val h=Harness();h.start(SessionMode.Continuous); val output=h.output!!;h.apply(h.c.firstFrame(output))
        assertTrue(h.queued.isEmpty());h.advance(660_000);assertEquals(output,h.output);assertTrue(h.service)
    }
    @Test fun timedBudgetDoesNotResetAfterAProfileHandoff() {
        val h=Harness();h.start(SessionMode.Timed(30)); val deadline=h.c.state.deadlineUptimeMillis
        h.advance(10_000);h.apply(h.c.reconcile(true,null,BfiPattern(3,2)));assertEquals(BfiPattern(3,2),h.c.state.effectivePattern)
        assertEquals(deadline,h.c.state.deadlineUptimeMillis);h.advance(30_000);assertNull(h.output);assertFalse(h.service)
    }
    @Test fun alreadyDequeuedRetryCannotResurrectAfterManualStop() {
        val h=Harness();h.start(SessionMode.Continuous,BfiSettings(alwaysForce=true));h.apply(h.c.fail(h.output!!,Reason.STALLED,true))
        assertNull(h.output);val dequeued=h.queued.single().second
        h.apply(h.c.stop());dequeued();assertNull(h.output);assertFalse(h.service);assertTrue(h.queued.isEmpty())
    }
    @Test fun alreadyDequeuedOldTimeoutCannotKillTheNextSession() {
        val h=Harness();h.start(SessionMode.Timed(1));val oldTimer=h.queued.single().second;h.apply(h.c.stop())
        h.start(SessionMode.Continuous);val newEpoch=h.output;h.now=2000;oldTimer();assertEquals(newEpoch,h.output);assertTrue(h.service)
    }
    @Test fun sleepingRateDropSuspendsButPermissionLossBlocks() {
        val h=Harness();h.start(SessionMode.Continuous);h.apply(h.c.reconcile(false,Reason.LOW_RATE,BfiPattern()))
        assertNull(h.output);assertTrue(h.c.state.userEnabled);assertTrue(h.service)
        h.apply(h.c.reconcile(false,Reason.OVERLAY,BfiPattern()));assertFalse(h.c.state.userEnabled);assertFalse(h.service)
    }
}
