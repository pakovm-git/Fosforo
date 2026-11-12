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
    @Test fun appShaderOverridesRoundTripWithoutChangingGlobalOrPatternSettings() {
        val original=BfiSettings(shaderId="lottes",shaderValues=mapOf("MASK" to 1f),overrides=mapOf("test.game" to BfiPattern(2,1)))
        val edited=original.withShader("test.game",AppShader("geom",mapOf("CURVATURE" to .5f)))
        assertEquals(edited,SettingsCodec.decode(SettingsCodec.encode(edited)))
        assertEquals(AppShader("geom",mapOf("CURVATURE" to .5f)),edited.shaderFor("test.game"))
        assertEquals(original.shaderFor(null),edited.shaderFor("other.app"))
        assertEquals(original.overrides,edited.overrides)
        assertEquals(original.shaderFor(null),edited.shaderFor(null))
        assertTrue(edited.needsAppIdentity)
        assertEquals(original.shaderFor(null),edited.copy(shaderOverrides=emptyMap()).shaderFor("test.game"))
        assertTrue(SettingsCodec.decode(emptyMap()).shaderOverrides.isEmpty())
    }
    @Test fun invalidAppShaderOverridesAreRejected() {
        val invalid=listOf("test.game|geom|GAIN", "test.game|../bad", "test.game|geom|GAIN|NaN", "test.game|geom|GAIN|1|GAIN|2", "test.game|geom|bad key|1")
        invalid.forEach { row -> assertTrue(row,runCatching { SettingsCodec.decode(mapOf("shaderOverrides" to setOf(row))) }.isFailure) }
        assertTrue(runCatching { SettingsCodec.decode(mapOf("shaderOverrides" to setOf("test.game|geom","test.game|lottes"))) }.isFailure)
    }
    @Test fun shaderOnlyOverridesRequireAppIdentityAndUseIndependentValues() {
        val settings=BfiSettings().withShader("test.game",AppShader("lottes",mapOf("GAIN" to .5f)))
        assertTrue(settings.needsAppIdentity)
        assertTrue(settings.overrides.isEmpty())
        assertEquals(.5f,settings.shaderFor("test.game").values["GAIN"]!!,0f)
        assertTrue(settings.shaderFor("other.app").values.isEmpty())
        assertEquals(settings.shaderOverrides,settings.withShader(null,AppShader("geom")).shaderOverrides)
    }
    @Test fun sameFractionDoesNotImplySameSequence() {
        assertEquals(BfiPattern(1,1).blackFraction,BfiPattern(2,2).blackFraction,0.0)
        assertNotEquals(BfiPattern(1,1).cyclesPerSecond(120.0),BfiPattern(2,2).cyclesPerSecond(120.0))
    }
}
