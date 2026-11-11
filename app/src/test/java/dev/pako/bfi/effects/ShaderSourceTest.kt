package dev.pako.bfi.effects

import org.junit.Assert.*
import org.junit.Test

class ShaderSourceTest {
    private val slang = """
        #version 450
        layout(push_constant) uniform Push {
            vec4 OutputSize;
            uint FrameCount;
            float blur_x;
        } params;
        layout(std140, set = 0, binding = 0) uniform UBO { mat4 MVP; } global;
        #pragma parameter blur_x "Horizontal Blur" 1.0 0.0 5.0 0.25
        vec2 blur = vec2(params.blur_x, 0.0) * params.OutputSize.zw;
        #pragma stage vertex
        layout(location = 0) in vec4 Position;
        layout(location = 0) out vec2 vTexCoord;
        void main() { gl_Position = global.MVP * Position; vTexCoord = blur; }
        #pragma stage fragment
        #pragma format R8G8B8A8_SRGB
        layout(location = 0) in vec2 vTexCoord;
        layout(location = 0) out vec4 FragColor;
        layout(set = 0, binding = 2) uniform sampler2D Source;
        void main() { FragColor = texture(Source, vTexCoord + blur); }
    """.trimIndent()

    @Test fun combinedGlslUsesItsOwnStageGuardsAndParameterUniforms() {
        val source = """
            #version 130
            #pragma parameter MASK_DARK "Mask" 0.25 0.0 1.0 0.05
            #if defined(VERTEX)
            void main() { gl_Position = vec4(1.0); }
            #elif defined(FRAGMENT)
            precision mediump float;
            #ifdef PARAMETER_UNIFORM
            uniform float MASK_DARK;
            #endif
            out vec4 FragColor;
            void main() { FragColor = vec4(MASK_DARK); }
            #endif
        """.trimIndent()
        val vertex = ShaderSource.compile(source, vertex = true, slang = false)
        val fragment = ShaderSource.compile(source, vertex = false, slang = false)
        assertTrue(vertex.startsWith("#version 310 es\n"))
        assertTrue(vertex.contains("#define VERTEX\n"))
        assertFalse(vertex.contains("#define FRAGMENT\n"))
        assertTrue(fragment.contains("#define FRAGMENT\n"))
        assertTrue(fragment.contains("#define PARAMETER_UNIFORM\n"))
        assertTrue(fragment.contains("precision highp int;"))
        assertTrue(fragment.contains("precision mediump float;"))
        assertFalse(fragment.contains("#pragma"))
        assertEquals(1, Regex("#version").findAll(fragment).count())
    }

    @Test fun slangRetainsCommonUniformsAndOnlyTheRequestedStage() {
        val vertex = ShaderSource.compile(slang, vertex = true, slang = true)
        val fragment = ShaderSource.compile(slang, vertex = false, slang = true)
        for (result in listOf(vertex, fragment)) {
            assertTrue(result.contains("uniform Push params;"))
            assertTrue(result.contains("uniform UBO global;"))
            assertTrue(result.contains("uint FrameCount;"))
            assertFalse(result.contains("layout("))
            assertFalse(result.contains("#pragma"))
            assertEquals(1, Regex("void main").findAll(result).count())
        }
        assertTrue(vertex.contains("out vec2 vTexCoord;"))
        assertFalse(vertex.contains("uniform sampler2D Source;"))
        assertTrue(fragment.contains("in vec2 vTexCoord;"))
        assertTrue(fragment.contains("out vec4 FragColor;"))
        assertFalse(fragment.contains("in vec4 Position;"))
        assertTrue(fragment.contains("texture(Source, vTexCoord + blur)"))
    }

    @Test fun runtimeGlobalInitializersRunInOrderAtTheStartOfMain() {
        val source = slang.replace("#pragma stage vertex", """
            vec2 aspect = vec2(params.blur_x, 0.75);
            vec4 blurwidth = vec4(aspect, blur);
            const float gamma = 2.2;
            #pragma stage vertex
        """.trimIndent())
        val result = ShaderSource.compile(source, vertex = false, slang = true)
        assertTrue(result.contains("vec2 blur;"))
        assertTrue(result.contains("vec2 aspect;"))
        assertTrue(result.contains("const float gamma = 2.2;"))
        val body = result.substringAfter("void main() {")
        assertTrue(body.indexOf("blur = vec2(params.blur_x, 0.0) * params.OutputSize.zw;") >= 0)
        assertTrue(body.indexOf("aspect = ") > body.indexOf("blur = "))
        assertTrue(body.indexOf("blurwidth = ") > body.indexOf("aspect = "))
        assertTrue(body.indexOf("FragColor =") > body.indexOf("blurwidth = "))
    }

    @Test fun vhsGlobalInitializersKeepTheirTrailingCommentsOutOfMain() {
        val source = slang.replace("vec2 blur = vec2(params.blur_x, 0.0) * params.OutputSize.zw;", """
            vec2 blur = params.OutputSize.xy; // vec2(640.0, 480.0); Set resolution }
            float roll_speed = params.blur_x / 100.; // Positive values are down
            bool pixelate = false; // Sample each pixel
        """.trimIndent())
        val result = ShaderSource.compile(source, vertex = false, slang = true)
        val body = result.substringAfter("void main() {")
        assertTrue(result.contains("vec2 blur; // vec2(640.0, 480.0); Set resolution }"))
        assertTrue(body.contains("blur = params.OutputSize.xy;"))
        assertTrue(body.contains("roll_speed = params.blur_x / 100.;"))
        assertTrue(body.contains("pixelate = false;"))
        assertFalse(body.contains("Set resolution"))
    }

    @Test fun vhsFrameArithmeticUsesExplicitFloatingPointConversions() {
        val source = slang.replace("uint FrameCount;", "uint FrameCount; int FrameDirection;")
            .replace("#pragma stage vertex", "#define iTime (params.FrameCount / 60.)\n#define TIME float(params.FrameCount)\n#pragma stage vertex")
            .replace("FragColor = texture(Source, vTexCoord + blur);", """
                float timer = (params.FrameDirection > 0.5) ? float(params.FrameCount) : 0.0;
                if (params.FrameDirection < -0.5) timer = iTime;
                FragColor = vec4(fract(TIME / 100) + timer);
            """.trimIndent())
        val result = ShaderSource.compile(source, vertex = false, slang = true)
        assertTrue(result.contains("#define iTime (float(params.FrameCount) / 60.)"))
        assertTrue(result.contains("float(params.FrameDirection) > 0.5"))
        assertTrue(result.contains("float(params.FrameDirection) < -0.5"))
        assertTrue(result.contains("fract(TIME / 100.0)"))
        assertTrue(result.contains("uint FrameCount; int FrameDirection;"))
    }

    @Test fun lottesAndGeomMixedNumericLiteralsKeepTheSameValues() {
        val source = slang.replace("FragColor = texture(Source, vTexCoord + blur);", """
            if (params.scaleInLinearGamma == 0) FragColor = vec4(0.0);
            float height = params.OutputSize.y / (registers.interlace_detect > 0.5 ? 200.0 : 1000);
        """.trimIndent())
        val result = ShaderSource.compile(source, vertex = false, slang = true)
        assertTrue(result.contains("params.scaleInLinearGamma == 0.0"))
        assertTrue(result.contains("registers.interlace_detect > 0.5 ? 200.0 : 1000.0"))
    }

    @Test fun vhsFontIntegerArrayAndBitOperationsStayUntouched() {
        val font = "int[] font = int[](0x00000000, 0x1F803FC0, 0x70E06060);"
        val bit = "return (font[x] & (1 << (0xf - (x & 0xf)))) != 0 ? 1 : 0;"
        val source = slang.replace("#pragma stage vertex", "$font\nint glyph(int x) { $bit }\n#pragma stage vertex")
        val result = ShaderSource.compile(source, vertex = false, slang = true)
        assertTrue(result.contains(font))
        assertTrue(result.contains(bit))
        assertTrue(result.indexOf(font) < result.indexOf("void main()"))
    }

    @Test fun unsignedFrameCountModGetsExplicitEsConversions() {
        val source = slang.replace("FragColor = texture(Source, vTexCoord + blur);", """
            float time = mod(params.FrameCount, 849.0) * 36.;
            time = float(mod(params.FrameCount, 640) * 1);
            float phase = mod(global.FrameCount+1, 2.);
            FragColor = vec4(time + phase);
        """.trimIndent())
        val result = ShaderSource.compile(source, vertex = false, slang = true)
        assertTrue(result.contains("mod(float(params.FrameCount), float(849.0))"))
        assertTrue(result.contains("mod(float(params.FrameCount), float(640))"))
        assertTrue(result.contains("mod(float(params.FrameCount), float(640)) * float(1)"))
        assertTrue(result.contains("mod(float(global.FrameCount+1u), float(2.))"))
        assertTrue(result.contains("uint FrameCount;"))
    }

    @Test fun geomDeluxeScalarSwizzlesBecomeScalarReads() {
        val source = slang.replace("float blur_x;", "float blur_x; float width;")
            .replace("#pragma stage vertex", "const float gamma = 2.2;\n#define width params.width\n#pragma stage vertex")
            .replace("FragColor = texture(Source, vTexCoord + blur);", "FragColor = vec4(width.x * gamma.x);")
        val result = ShaderSource.compile(source, vertex = false, slang = true)
        assertTrue(result.contains("FragColor = vec4(width * gamma);"))
    }

    @Test fun scalarFieldsAndAliasesKeepTheSameReplicationAndMath() {
        val source = slang.replace("float blur_x;", "float blur_x; float R; float rasterbloom; float H_MaskGamma;")
            .replace("#pragma stage vertex", "#define rasterbloom (params.rasterbloom / 10.0)\n#pragma stage vertex")
            .replace("FragColor = texture(Source, vTexCoord + blur);", "FragColor = vec4(params.H_MaskGamma.xxx * params.R.x + rasterbloom.x, 1.0);")
        val result = ShaderSource.compile(source, vertex = false, slang = true)
        assertTrue(result.contains("vec3(params.H_MaskGamma) * params.R + rasterbloom"))
        assertTrue(result.contains("#define rasterbloom (params.rasterbloom / 10.0)"))
    }

    @Test fun nestedMaskArraysKeepTheirShapeAndIndexing() {
        val source = slang.replace("FragColor = texture(Source, vTexCoord + blur);", """
            const vec3 red = vec3(1.0, 0.0, 0.0), blue = vec3(0.0, 0.0, 1.0);
            const vec3 delta[2][2] = { {red, blue}, {blue, red}, };
            FragColor = vec4(delta[0][1], 1.0);
        """.trimIndent())
        val result = ShaderSource.compile(source, vertex = false, slang = true)
        assertTrue(result.contains("const vec3 delta[2][2] = vec3[2][2](vec3[2](red, blue), vec3[2](blue, red));"))
        assertTrue(result.contains("FragColor = vec4(delta[0][1], 1.0);"))
    }

    @Test fun instanceLessUniformBlocksExposeTheirOriginalMemberNames() {
        val source = slang.replace("} params;", "};")
        val result = ShaderSource.compile(source, vertex = true, slang = true)
        assertTrue(result.contains("uniform vec4 OutputSize;"))
        assertTrue(result.contains("uniform uint FrameCount;"))
        assertTrue(result.contains("uniform float blur_x;"))
        assertFalse(result.contains("uniform Push"))
    }

    @Test fun stageOrderDoesNotLeakTheOtherMain() {
        val source = """
            #version 450
            uniform float shared;
            #pragma stage fragment
            void main() { float fragmentOnly = shared; }
            #pragma stage vertex
            void main() { float vertexOnly = shared; }
        """.trimIndent()
        val result = ShaderSource.compile(source, vertex = false, slang = true)
        assertTrue(result.contains("uniform float shared;"))
        assertTrue(result.contains("fragmentOnly"))
        assertFalse(result.contains("vertexOnly"))
    }

    @Test fun missingOrUnsupportedStagesFailClearly() {
        assertRejected("requires one vertex and one fragment stage") {
            ShaderSource.compile(slang.replace("#pragma stage fragment", ""), true, true)
        }
        assertRejected("Unsupported Slang stage") {
            ShaderSource.compile(slang.replace("stage fragment", "stage compute"), true, true)
        }
    }

    @Test fun unexpandedIncludesAndUnknownLayoutsFailClearly() {
        assertRejected("Expand shader includes") {
            ShaderSource.compile("#include \"missing.inc\"\n$slang", false, true)
        }
        assertRejected("Unsupported shader layout") {
            ShaderSource.compile(slang.replace("push_constant", "rgba32f"), false, true)
        }
    }

    private fun assertRejected(message: String, block: () -> Unit) {
        try {
            block()
            fail("Expected rejection: $message")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message, error.message.orEmpty().contains(message))
        }
    }
}
