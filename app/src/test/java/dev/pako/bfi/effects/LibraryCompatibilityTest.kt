package dev.pako.bfi.effects

import org.junit.Assert.*
import org.junit.Test

class LibraryCompatibilityTest {
    @Test fun presetCommentsDoNotBecomeSettingsOrBreakQuotedPaths() {
        val values=ShaderCatalog.parseSettings("""
            /* Libretro preset
               documentation = ignored
            */
            // shader options
            shaders = "1" // pass count
            shader0 = "shaders/test.slang" # relative source
            texture = "textures/a//b.png"
        """.trimIndent())
        assertEquals(setOf("shaders","shader0","texture"),values.keys)
        assertEquals("textures/a//b.png",values["texture"])
    }
    @Test fun wideParameterRangesUseBoundedSlidersWithoutRejectingThePreset() {
        val parameter=ShaderCatalog.parseParameters("#pragma parameter H_FREQ \"Frequency\" 15.734 0.0 4000.0 0.01").single()
        assertTrue(parameter.sliderValues().size<=1026)
        assertTrue(parameter.sliderValues().contains(parameter.defaultValue))
        assertEquals(4000f,parameter.sliderValues().last())
    }
    @Test fun upstreamSeparatorLabelsAreNotEditableUniforms() {
        val parameter=ShaderCatalog.parseParameters("#pragma parameter ntsc-row0 \"NTSC controls\" 0.0 0.0 0.0 1.0").single()
        assertFalse(parameter.editable)
    }
    @Test fun layoutWordsInsideCommentsAreNotVulkanDeclarations() {
        val source="""
            #version 450
            // layout (bilinear weights)
            #pragma stage vertex
            void main(){gl_Position=vec4(0.);}
            #pragma stage fragment
            layout(location=0) out vec4 FragColor;
            void main(){FragColor=vec4(1.);}
        """.trimIndent()
        assertTrue(ShaderSource.compile(source,false,true).contains("void main"))
    }
}
