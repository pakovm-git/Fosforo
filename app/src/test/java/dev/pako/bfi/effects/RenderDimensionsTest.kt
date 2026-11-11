package dev.pako.bfi.effects

import org.junit.Assert.*
import org.junit.Test

class RenderDimensionsTest {
    @Test fun captureAndProcessingPreserveEveryDisplayPixelInEitherOrientation() {
        for ((w,h) in listOf(1280 to 960,960 to 1280,1920 to 1080)) {
            val size=renderDimensions(w,h,false)
            assertEquals(w to h,size.sourceW to size.sourceH)
            assertEquals(w to h,size.viewportW to size.viewportH)
        }
    }
    @Test fun previewUsesTheMatchingHalfWindowAsItsFinalViewport() {
        val size=renderDimensions(1281,360,true)
        assertEquals(640 to 360,size.sourceW to size.sourceH)
        assertEquals(641 to 360,size.viewportW to size.viewportH)
    }
    @Test fun invalidDimensionsAreRejected() {
        assertThrows(IllegalArgumentException::class.java){renderDimensions(0,960,false)}
        assertThrows(IllegalArgumentException::class.java){renderDimensions(1280,-1,false)}
    }
}
