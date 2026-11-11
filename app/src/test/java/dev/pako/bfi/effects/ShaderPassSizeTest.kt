package dev.pako.bfi.effects
import org.junit.Assert.*
import org.junit.Test
class ShaderPassSizeTest {
    @Test fun onlyActuallySampledFeedbackPassesNeedPreviousFrameTargets() {
        val aliases=mapOf("phosphor" to 1,"blur" to 3)
        assertEquals(setOf(0,1,2),shaderFeedbackTargets(listOf("Source","Original","Feedback","PassFeedback2","phosphorFeedback","blur"),aliases,0,5))
        assertEquals(emptySet<Int>(),shaderFeedbackTargets(listOf("Source","Original","PassOutput0","phosphor"),aliases,0,5))
        assertThrows(IllegalArgumentException::class.java){shaderFeedbackTargets(listOf("PassFeedback5"),aliases,0,5)}
    }
    @Test fun defaultsKeepIntermediateSourceAndFinishAtViewport() {
        assertEquals(640 to 480,shaderPassSize(emptyMap(),0,false,640,480,480,360))
        assertEquals(480 to 360,shaderPassSize(emptyMap(),0,true,640,480,480,360))
    }
    @Test fun ntscUsesFourTimesSourceWidthAndMixedViewportScale() {
        assertEquals(2560 to 480,shaderPassSize(mapOf("scale_type_x1" to "source","scale_x1" to "4","scale_type_y1" to "source"),1,false,640,480,480,360))
        assertEquals(480 to 480,shaderPassSize(mapOf("scale_type_x3" to "viewport","scale_type_y3" to "source"),3,false,2560,480,480,360))
    }
    @Test fun rejectsNonfiniteAndInvalidScales() {
        for(value in listOf("NaN","Infinity","-1","0")) {
            try { shaderPassSize(mapOf("scale0" to value),0,true,640,480,480,360);fail(value) }catch(expected:IllegalArgumentException){}
        }
    }
    @org.junit.Test fun samplingFollowsResourceInsteadOfConsumer() {
        val aliases=mapOf("internal1" to 0,"blur_texture" to 3)
        org.junit.Assert.assertEquals(1,shaderSamplingPass("internal1",4,aliases,0))
        org.junit.Assert.assertEquals(0,shaderSamplingPass("internal1Feedback",4,aliases,0))
        org.junit.Assert.assertEquals(4,shaderSamplingPass("blur_texture",4,aliases,0))
        org.junit.Assert.assertEquals(2,shaderSamplingPass("PassFeedback2",4,aliases,0))
        org.junit.Assert.assertEquals(3,shaderSamplingPass("PassOutput2",4,aliases,0))
        org.junit.Assert.assertEquals(4,shaderSamplingPass("Source",4,aliases,0))
    }
    @org.junit.Test fun targetFormatHonorsPresetOverrideAndRejectsUnsupportedFlags() {
        org.junit.Assert.assertEquals(0x8C43,shaderTargetFormat(mapOf("srgb_framebuffer0" to "true"),0,"#pragma format R16G16B16A16_SFLOAT"))
        org.junit.Assert.assertEquals(0x881A,shaderTargetFormat(mapOf("float_framebuffer0" to "true"),0,"#pragma format R8G8B8A8_SRGB"))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java){shaderTargetFormat(mapOf("rgb10_framebuffer0" to "true"),0,"")}
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java){shaderTargetFormat(mapOf("float_framebuffer0" to "true","srgb_framebuffer0" to "true"),0,"")}
    }
}
