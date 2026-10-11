package dev.pako.bfi.settings

import dev.pako.bfi.model.*
import org.junit.Assert.*
import org.junit.Test

class SettingsCodecTest {
    @Test fun emptyStorageLoadsDefaultsWithoutEnabledIntent() {
        val s=SettingsCodec.decode(emptyMap()); assertEquals(BfiSettings(),s)
        assertFalse(SettingsCodec.encode(s).keys.any { it.contains("enabled",true) || it.contains("session",true) })
    }
    @Test fun allSettingsRoundTripTogether() {
        val s=BfiSettings(globalPattern=BfiPattern(3,2),activationMode=ActivationMode.SELECTED_APPS,includedPackages=setOf("org.example.game"),excludedPackages=setOf("org.example.other"),overrides=mapOf("org.example.game" to BfiPattern(1,3)),refreshTarget=RefreshTarget.Numeric(119.88),requestRefresh=true,alwaysForce=true,diagnosticsEnabled=true,acceptedNoticeVersion=1)
        assertEquals(s,SettingsCodec.decode(SettingsCodec.encode(s)))
    }
    @Test fun malformedOrFutureSettingsAreRejected() {
        for (data in listOf(mapOf("schema" to 2),mapOf("visible" to 0),mapOf("target" to "NaN"),mapOf("overrides" to setOf("bad|1")),mapOf("overrides" to setOf("pkg|1|2","pkg|2|3")),mapOf("resume" to "false"),mapOf("black" to 13))) {
            assertTrue(runCatching { SettingsCodec.decode(data) }.isFailure)
        }
    }
    @Test fun sameFractionDoesNotImplySameSequence() {
        assertEquals(BfiPattern(1,1).blackFraction,BfiPattern(2,2).blackFraction,0.0)
        assertNotEquals(BfiPattern(1,1).cyclesPerSecond(120.0),BfiPattern(2,2).cyclesPerSecond(120.0))
    }
}
