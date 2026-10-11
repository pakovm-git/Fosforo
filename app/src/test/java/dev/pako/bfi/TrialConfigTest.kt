package dev.pako.bfi

import org.junit.Assert.*
import org.junit.Test

class TrialConfigTest {
    @Test fun acceptsLimitsAndLongerTrial() {
        assertEquals(1, TrialConfig.parseSeconds("1"))
        assertEquals(120, TrialConfig.parseSeconds(" 120 "))
        assertEquals(600, TrialConfig.parseSeconds("600"))
    }
    @Test fun rejectsInvalidOrUnboundedDuration() {
        for (input in listOf("", "0", "-1", "601", "1.5", "unlimited", "999999999999")) {
            assertNull(input, TrialConfig.parseSeconds(input))
        }
    }
    @Test fun validatesServiceRequestAsWellAsUiText() {
        assertFalse(TrialConfig.isValid(Int.MAX_VALUE))
        assertFalse(TrialConfig.isValid(0))
        assertTrue(TrialConfig.isValid(TrialConfig.DEFAULT_SECONDS))
    }
}
