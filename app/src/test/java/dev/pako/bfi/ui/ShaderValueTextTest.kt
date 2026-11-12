package dev.pako.bfi.ui

import dev.pako.bfi.effects.ShaderParameter
import org.junit.Assert.*
import org.junit.Test

class ShaderValueTextTest {
    @Test fun normalizedValuesDoNotExposeBinaryFloatNoise() {
        val p=ShaderParameter("maskLight","maskLight",1.5f,0f,2f,.1f)
        val text=ShaderValueText(p)
        assertEquals("1.8",text.format(p.normalize(1.8f)))
        assertEquals("1",text.format(1f))
        assertEquals(listOf("0","0.1","0.2","0.3","0.4","0.5","0.6","0.7","0.8","0.9","1","1.1","1.2","1.3","1.4","1.5","1.6","1.7","1.8","1.9","2"),p.sliderValues().map(text::format))
    }
    @Test fun offGridDefaultAndEndpointKeepTheirPrecision() {
        val text=ShaderValueText(ShaderParameter("warpX","warpX",.031f,0f,.125f,.01f))
        assertEquals("0.031",text.format(.031f))
        assertEquals("0.125",text.format(.125f))
        assertEquals("0.12",text.format(.120000005f))
    }
    @Test fun fineOffsetNegativeAndScientificValuesStayReadable() {
        val offset=ShaderParameter("curve","Curvature",2f,.0001f,4f,.25f)
        assertEquals("0.2501",ShaderValueText(offset).format(offset.normalize(.2501f)))
        val small=ShaderValueText(ShaderParameter("fine","Fine",1e-5f,-1f,1f,1e-5f))
        assertEquals("0.00001",small.format(1e-5f))
        assertEquals("-0.25",small.format(-.25f))
        assertEquals("0",small.format(-0f))
    }
}
