package dev.pako.bfi.effects

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class CapturedPixelsTest {
    @Test fun reusableBufferRetainsPaddingAndOrientationRulesAcrossFrames() {
        val raw=ByteBuffer.allocate(24)
        val reusable=ByteBuffer.allocateDirect(32)
        val pixels=CapturedPixels(raw,2,2,12,4)
        for(value in listOf(12,87)) {
            raw.put(0,value.toByte());raw.put(12,(value+1).toByte())
            reusable.clear();reusable.position(20)
            val output=pixels.bottomUp(reusable)
            assertSame(reusable,output);assertEquals(16,output.remaining())
            assertEquals(value+1,output.get(0).toInt());assertEquals(value,output.get(8).toInt())
        }
        assertThrows(IllegalArgumentException::class.java){pixels.bottomUp(ByteBuffer.allocate(15))}
        assertThrows(IllegalArgumentException::class.java){pixels.bottomUp(ByteBuffer.allocate(16).asReadOnlyBuffer())}
    }
    @Test fun paddingAndOrientationAreHandled() {
        val raw=ByteBuffer.allocate(24)
        for(i in 0..7)raw.put(i,(i+1).toByte())
        for(i in 12..19)raw.put(i,(i+1).toByte())
        val output=CapturedPixels(raw,2,2,12,4).bottomUp()
        assertEquals(16,output.remaining())
        assertEquals(13,output.get(0).toInt());assertEquals(20,output.get(7).toInt())
        assertEquals(1,output.get(8).toInt());assertEquals(8,output.get(15).toInt())
    }
    @Test fun cornerColorsMustIncludeBothMarkers() {
        val width=100;val height=100;val raw=ByteBuffer.allocate(width*height*4)
        fun color(x:Int,y:Int,r:Int,g:Int,b:Int){val p=(y*width+x)*4;raw.put(p,r.toByte());raw.put(p+1,g.toByte());raw.put(p+2,b.toByte())}
        val pixels=CapturedPixels(raw,width,height,width*4,4)
        assertFalse(pixels.tagged());assertTrue(pixels.black())
        color(6,8,255,0,255);assertFalse(pixels.tagged())
        color(94,8,0,255,255);assertTrue(pixels.tagged())
        color(50,50,60,50,40);assertFalse(pixels.black())
    }
    @Test fun nonzeroBufferPositionIsPreservedAndExcludedFromPixels() {
        val raw=ByteBuffer.allocate(31)
        raw.position(7)
        for(i in 0..7)raw.put(7+i,(i+1).toByte())
        for(i in 0..7)raw.put(19+i,(i+11).toByte())
        val output=CapturedPixels(raw,2,2,12,4).bottomUp()
        val actual=ByteArray(output.remaining());output.get(actual)
        assertArrayEquals(byteArrayOf(11,12,13,14,15,16,17,18,1,2,3,4,5,6,7,8),actual)
        assertEquals(7,raw.position())
    }
    @Test fun fixtureCounterDecodesBothOrientationsAndRequiresComplementBits() {
        val width=320;val height=200
        for(flipped in listOf(false,true))for(counter in listOf(0,0xa35c,65535)) {
            val raw=ByteBuffer.allocate(width*height*4)
            fun pixel(x:Int,y:Int,r:Int,g:Int,b:Int) {
                val p=(y*width+x)*4
                raw.put(p,r.toByte());raw.put(p+1,g.toByte());raw.put(p+2,b.toByte())
            }
            val headerY=if(flipped)21 else 179
            val bitsY=if(flipped)10 else 190
            for(x in 0 until width)pixel(x,headerY,255,128,0)
            for(cell in 0..31) {
                val bit=(counter shr (cell%16)) and 1
                val value=255*(if(cell<16)bit else 1-bit)
                for(x in cell*10 until (cell+1)*10)pixel(x,bitsY,value,value,value)
            }
            val pixels=CapturedPixels(raw,width,height,width*4,4)
            assertEquals(counter,pixels.fixtureCounter())
            val badValue=255*(counter and 1)
            for(x in 160 until 170)pixel(x,bitsY,badValue,badValue,badValue)
            assertNull(pixels.fixtureCounter())
        }
    }
    @Test fun sparseDimSourceIsNotMistakenForBlackBfi() {
        val raw=ByteBuffer.allocate(24)
        raw.put(8,99.toByte());raw.put(9,99.toByte()) // Padding is not image content.
        raw.put(3,255.toByte());raw.put(7,255.toByte());raw.put(15,255.toByte());raw.put(19,255.toByte())
        val pixels=CapturedPixels(raw,2,2,12,4)
        assertTrue(pixels.black())
        raw.put(18,1.toByte())
        assertFalse(pixels.black())
    }
    @Test(expected=IllegalArgumentException::class) fun truncatedRowsAreRejected(){CapturedPixels(ByteBuffer.allocate(15),2,2,8,4)}
    @Test(expected=IllegalArgumentException::class) fun unsupportedPixelStrideIsRejected(){CapturedPixels(ByteBuffer.allocate(32),2,2,16,8)}
}
