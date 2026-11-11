package dev.pako.bfi.effects

import org.junit.Assert.*
import org.junit.Test

class ContinuousGateTest {
    @Test fun staticScreenIsHealthyButMissingOrStuckFramesTimeOut() {
        val gate=ContinuousGate()
        assertTrue(gate.stalled(false,3001))
        assertTrue(gate.observe(1,false))
        assertTrue(gate.stalled(false,3001))
        assertTrue(gate.processed())
        assertFalse(gate.stalled(true,60000))
        assertTrue(gate.observe(2,false))
        assertFalse(gate.stalled(true,3000))
        assertTrue(gate.stalled(true,3001))
    }
    @Test fun processesConsecutiveFreshFramesWithoutHidingOrRecapturingOutput() {
        val gate=ContinuousGate()
        for(timestamp in 1L..100L) {
            assertTrue(gate.observe(timestamp,false))
            assertTrue(gate.processed())
        }
        assertFalse(gate.stopped)
    }
    @Test fun rejectsDuplicateAndOutOfOrderBuffers() {
        val gate=ContinuousGate()
        assertTrue(gate.observe(10,false));assertTrue(gate.processed())
        assertFalse(gate.observe(10,false));assertFalse(gate.observe(9,false))
        assertTrue(gate.observe(11,false))
    }
    @Test fun allowsOnlyOneFrameInFlight() {
        val gate=ContinuousGate()
        assertTrue(gate.observe(1,false));assertFalse(gate.observe(2,false))
        assertTrue(gate.processed());assertFalse(gate.processed())
        assertFalse(gate.observe(2,false));assertTrue(gate.observe(3,false))
    }
    @Test fun outputContaminationStopsEvenWhileProcessing() {
        val gate=ContinuousGate()
        assertTrue(gate.observe(1,false))
        assertFalse(gate.observe(2,true));assertTrue(gate.stopped)
        assertFalse(gate.processed());assertFalse(gate.observe(3,false))
    }
    @Test fun stopDiscardsPendingCompletionAndPreventsRestart() {
        val gate=ContinuousGate()
        assertTrue(gate.observe(1,false));gate.stop()
        assertFalse(gate.processed());assertFalse(gate.observe(2,false))
    }
}
