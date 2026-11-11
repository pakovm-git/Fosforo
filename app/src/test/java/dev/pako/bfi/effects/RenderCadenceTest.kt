package dev.pako.bfi.effects

import org.junit.Assert.*
import org.junit.Test

class RenderCadenceTest {
    @Test fun optInTraceIsBoundedAndStopsRecording() {
        val trace=RenderCadence()
        trace.nativeDraw(1,true)
        assertFalse(trace.stop().contains("native,"))
        trace.start();trace.dropped()
        repeat(5000){trace.nativeDraw(it.toLong(),it%2==1)}
        val report=trace.stop()
        assertTrue(report.startsWith("# coalesced=1"))
        assertEquals(4096,report.lineSequence().count{it.startsWith("native,")})
        trace.record(1,2,3,4,5,true)
        assertEquals(report,trace.stop())
    }
}
