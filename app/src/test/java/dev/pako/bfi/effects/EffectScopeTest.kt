package dev.pako.bfi.effects

import org.junit.Assert.*
import org.junit.Test

class EffectScopeTest {
    @Test fun perAppOverrideWinsEvenIfItMatchesTheSimpleGlobalPattern() {
        val global=dev.pako.bfi.model.BfiPattern(1,1)
        val settings=dev.pako.bfi.model.BfiSettings(globalPattern=global,multiStepPattern="1,1,2,1",overrides=mapOf("game" to global))
        assertEquals("1, 1",effectivePattern(settings,global,"game").encoded)
        assertEquals("1, 1, 2, 1",effectivePattern(settings,global,"other").encoded)
    }
}
