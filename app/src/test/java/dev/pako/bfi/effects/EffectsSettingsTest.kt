package dev.pako.bfi.effects

import dev.pako.bfi.model.*
import dev.pako.bfi.settings.SettingsCodec
import dev.pako.bfi.state.*
import org.junit.Assert.*
import org.junit.Test

class EffectsSettingsTest {
    @Test fun legacySettingsKeepBfiAndDoNotEnableCapture() {
        val old=SettingsCodec.decode(mapOf("visible" to 2,"black" to 1))
        assertTrue(old.bfiEnabled);assertFalse(old.crtEnabled)
        assertEquals(BfiPattern.legacy(2,1).encoded,BfiPattern.legacy(old.globalPattern.visibleSlots,old.globalPattern.blackSlots).encoded)
    }
    @Test fun fullSettingsRoundTripDoesNotLoseAppRulesOrEffectSelections() {
        val settings=BfiSettings(crtEnabled=true,shaderId="newpixie",shaderValues=mapOf("curvature" to .75f),multiStepPattern="1, 1, 2, 1",
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
    @Test fun onlyConfirmedLicensedCatalogAndCompleteDependenciesAreBundled() {
        val assets=java.io.File("src/main/assets")
        assertEquals(setOf("lottes","newpixie","zfast","geom","none"),ShaderCatalog.choices.map{it.id}.toSet())
        ShaderCatalog.choices.forEach { choice ->
            if(choice.id!="none")assertEquals("crt",choice.category)
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
