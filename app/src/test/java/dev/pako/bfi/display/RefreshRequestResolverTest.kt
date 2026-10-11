package dev.pako.bfi.display

import dev.pako.bfi.model.RefreshTarget
import org.junit.Assert.*
import org.junit.Test

class RefreshRequestResolverTest {
    private val modes=listOf(DisplayMode(1,1280,960,60.0),DisplayMode(2,1280,960,119.88),DisplayMode(3,1920,1080,240.0),DisplayMode(4,1280,960,120.0))
    @Test fun maximumNeverChangesResolution() { assertEquals(4,RefreshRequestResolver.resolve(modes,1280,960,RefreshTarget.Maximum)?.id) }
    @Test fun fractionalSavedRateMatchesNearestWithinTolerance() { assertEquals(2,RefreshRequestResolver.resolve(modes,1280,960,RefreshTarget.Numeric(119.9))?.id) }
    @Test fun missingTargetIsNotSilentlyReplaced() { assertNull(RefreshRequestResolver.resolve(modes,1280,960,RefreshTarget.Numeric(144.0))) }
    @Test fun tiesAreStableRegardlessOfEnumeration() {
        val tied=listOf(DisplayMode(8,10,10,120.0),DisplayMode(5,10,10,120.0))
        assertEquals(5,RefreshRequestResolver.resolve(tied.reversed(),10,10,RefreshTarget.Numeric(120.0))?.id)
    }
    @Test fun rejectsInvalidStoredRates() { for (rate in listOf(Double.NaN,Double.POSITIVE_INFINITY,0.0,-60.0)) assertTrue(runCatching { RefreshTarget.Numeric(rate) }.isFailure) }
    @Test fun unsupportedResolutionAndInvalidModesReturnNull() { assertNull(RefreshRequestResolver.resolve(listOf(DisplayMode(9,10,10,Double.NaN)),10,10,RefreshTarget.Maximum)) }
}
