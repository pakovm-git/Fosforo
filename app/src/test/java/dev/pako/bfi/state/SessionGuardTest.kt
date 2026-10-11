package dev.pako.bfi.state

import org.junit.Assert.*
import org.junit.Test

class SessionGuardTest {
    @Test fun manualStopRejectsPendingStartOrFailureResult() {
        val guard = SessionGuard()
        val token = guard.begin()
        assertTrue(guard.accepts(token))
        guard.stop()
        assertFalse(guard.enabled)
        assertFalse(guard.accepts(token))
    }
    @Test fun newStartCannotAcceptAnOlderSessionCallback() {
        val guard = SessionGuard()
        val old = guard.begin()
        guard.stop()
        val next = guard.begin()
        assertTrue(guard.accepts(next))
        assertFalse(guard.accepts(old))
    }
    @Test fun processRecreationIsDisabled() { assertFalse(SessionGuard().enabled) }
}
