package dev.pako.bfi.effects

import dev.pako.bfi.model.*
import dev.pako.bfi.settings.SettingsCodec
import dev.pako.bfi.state.*
import org.junit.Assert.*
import org.junit.Test

class EffectsSettingsTest {
    @Test fun favouritesAreOptionalValidatedAndBundledChoicesStayDistinct() {
        assertTrue(SettingsCodec.decode(emptyMap()).favouriteShaders.isEmpty())
        assertThrows(IllegalArgumentException::class.java){BfiSettings(favouriteShaders=setOf("../bad"))}
        assertTrue(ShaderCatalog.choices.all{it.bundled})
        assertFalse(ShaderChoice("test","Test","library/${"a".repeat(40)}/crt/test.slangp").bundled)
        assertFalse(ShaderChoice("test","Test","imports/${"a".repeat(24)}/test.slangp").bundled)
    }
    @Test fun legacySettingsKeepBfiAndDoNotEnableCapture() {
        val old=SettingsCodec.decode(mapOf("visible" to 2,"black" to 1))
        assertTrue(old.bfiEnabled);assertFalse(old.crtEnabled)
        assertEquals(BfiPattern.legacy(2,1).encoded,BfiPattern.legacy(old.globalPattern.visibleSlots,old.globalPattern.blackSlots).encoded)
    }
    @Test fun fullSettingsRoundTripDoesNotLoseAppRulesOrEffectSelections() {
        val settings=BfiSettings(crtEnabled=true,shaderId="newpixie",favouriteShaders=setOf("newpixie","library_0123456789abcdef"),shaderValues=mapOf("curvature" to .75f),multiStepPattern="1, 1, 2, 1",
            includedPackages=setOf("test.game"),activationMode=ActivationMode.SELECTED_APPS,resumeAfterUnlock=false)
        assertEquals(settings,SettingsCodec.decode(SettingsCodec.encode(settings)))
    }
    @Test fun invalidPatternAndNonfiniteParametersNeverReachTheRenderer() {
        assertThrows(IllegalArgumentException::class.java){BfiSettings(multiStepPattern="1, 0")}
        assertThrows(IllegalArgumentException::class.java){BfiSettings(shaderValues=mapOf("bad" to Float.NaN))}
        assertThrows(IllegalArgumentException::class.java){BfiSettings(shaderValues=mapOf("../escape" to 1f))}
    }
    @Test fun effectEditsKeepTheTimedDeadlineAndSessionIdentity() {
        val controller=BfiController();controller.start(BfiSettings(),SessionMode.Timed(15),100)
        val before=controller.state
        controller.updateSettings(before.settings.copy(crtEnabled=true,bfiEnabled=false,shaderId="newpixie"))
        assertEquals(before.session,controller.state.session);assertEquals(before.deadlineUptimeMillis,controller.state.deadlineUptimeMillis)
        assertFalse(controller.state.settings.bfiEnabled)
        controller.timeout(before.session,15100);assertFalse(controller.state.userEnabled)
    }
    @Test fun curatedCatalogHasCompleteDependenciesAndHidesLegacyPassthrough() {
        val assets=java.io.File("src/main/assets")
        assertEquals(setOf("lottes","newpixie","zfast","geom","vhs_godot"),ShaderCatalog.choices.map{it.id}.toSet())
        assertFalse(ShaderCatalog.choices.any{it.id=="none"})
        assertFalse(java.io.File(assets,"crt-mattias.glsl").exists())
        assertFalse(java.io.File(assets,"shaders/upstream/slang/vhs/shaders/vhs_font.slang").exists())
        val vhs=ShaderCatalog.load("vhs_godot"){java.io.File(assets,it).readText()}
        assertEquals(1,vhs.passes.size)
        assertTrue(vhs.passes.single().source.contains("CC0 license"))
        assertEquals("none",ShaderCatalog.load("none"){java.io.File(assets,it).readText()}.choice.id)
        ShaderCatalog.choices.forEach { choice ->
            assertEquals(if(choice.id=="vhs_godot")"vhs" else "crt",choice.category)
            val definition=ShaderCatalog.load(choice.id){java.io.File(assets,it).readText()}
            assertTrue(definition.passes.isNotEmpty())
            definition.passes.forEach { pass ->
                ShaderSource.compile(pass.source,true,pass.path.endsWith(".slang"))
                ShaderSource.compile(pass.source,false,pass.path.endsWith(".slang"))
            }
            definition.lookups.forEach{assertTrue(java.io.File(assets,it.path).isFile)}
        }
        assertTrue(java.io.File(assets,"THIRD-PARTY-NOTICES.txt").readText().contains("Mattias Gustavsson"))
    }
}
