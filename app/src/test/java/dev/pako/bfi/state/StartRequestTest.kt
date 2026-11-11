package dev.pako.bfi.state

import dev.pako.bfi.model.*
import org.junit.Assert.*
import org.junit.Test

class StartRequestTest {
    @Test fun stopOrNewCommandInvalidatesPendingSaveAndConsent() {
        val request=StartRequest(10,SessionMode.Continuous)
        assertTrue(request.accepts(10,1000))
        assertFalse(request.accepts(11,1000))
    }
    @Test fun restartAndCaptureConsentKeepOriginalTimedDeadline() {
        val controller=BfiController()
        controller.start(BfiSettings(),SessionMode.Timed(15),1000)
        val original=controller.state
        controller.stop()
        val request=StartRequest(10,original.mode,original.deadlineUptimeMillis)
        assertTrue(request.accepts(10,6000))
        controller.start(BfiSettings(crtEnabled=true),request.mode,6000,deadline=request.deadline)
        assertEquals(16000L,controller.state.deadlineUptimeMillis)
        controller.timeout(controller.state.session,16000)
        assertFalse(controller.state.userEnabled)
    }
    @Test fun expiredConsentCannotRestartOrExtendTimedSession() {
        val request=StartRequest(10,SessionMode.Timed(15),16000)
        assertFalse(request.accepts(10,16000))
        val controller=BfiController()
        controller.start(BfiSettings(),request.mode,17000,deadline=request.deadline)
        assertFalse(controller.state.userEnabled)
        assertEquals(Reason.TIMEOUT,controller.state.reason)
    }
    @Test fun freshCustomTestStartsItsFullDurationAfterConsent() {
        val request=StartRequest(10,SessionMode.Timed(47))
        val controller=BfiController()
        controller.start(BfiSettings(crtEnabled=true),request.mode,10000,deadline=request.deadline)
        assertEquals(57000L,controller.state.deadlineUptimeMillis)
    }
}
