package dev.pako.bfi.state

import dev.pako.bfi.model.BfiSettings
import dev.pako.bfi.model.SessionMode
import org.junit.Assert.*
import org.junit.Test

class OutputPermissionsTest {
    @Test fun modernOutputCannotStartWithEitherPermissionMissing() {
        for(accessibility in listOf(false,true)) for(notifications in listOf(false,true)) {
            val controller=BfiController()
            val problem=outputPermissionProblem(true,accessibility,false,notifications)
            controller.start(BfiSettings(),SessionMode.Continuous,0,problem)
            assertEquals(accessibility && notifications,controller.state.userEnabled)
        }
    }
    @Test fun bothGrantOrdersLeadToTheRemainingRequirementThenReady() {
        assertEquals(Reason.ACCESSIBILITY,outputPermissionProblem(true,false,false,false))
        assertEquals(Reason.NOTIFICATION,outputPermissionProblem(true,true,false,false))
        assertEquals(Reason.ACCESSIBILITY,outputPermissionProblem(true,false,false,true))
        assertNull(outputPermissionProblem(true,true,false,true))
    }
    @Test fun legacyOutputStillRequiresOverlayAndNotifications() {
        assertEquals(Reason.OVERLAY,outputPermissionProblem(false,true,false,true))
        assertEquals(Reason.NOTIFICATION,outputPermissionProblem(false,false,true,false))
        assertNull(outputPermissionProblem(false,false,true,true))
    }
}
