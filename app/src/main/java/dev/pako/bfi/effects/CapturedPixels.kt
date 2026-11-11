package dev.pako.bfi.effects

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** RGBA ImageReader plane; padding is excluded and rows are reversed for GLES textures. */
class CapturedPixels(private val data: ByteBuffer, val width:Int, val height:Int, private val stride:Int, pixelStride:Int) {
    private val base=data.position()
    init {
        require(width>0 && height>0 && pixelStride==4 && stride>=width*4)
        require(base.toLong()+(height-1L)*stride+width*4<=data.limit())
    }
    private fun rgb(x:Float,y:Float):IntArray {
        val p=base+(y*height).toInt().coerceIn(0,height-1)*stride+(x*width).toInt().coerceIn(0,width-1)*4
        return IntArray(3){data.get(p+it).toInt() and 255}
    }
    fun tagged():Boolean {
        val corners=listOf(.06f to .08f,.94f to .08f,.06f to .92f,.94f to .92f).map{rgb(it.first,it.second)}
        return corners.any{it[0]>220 && it[1]<35 && it[2]>220} && corners.any{it[0]<35 && it[1]>220 && it[2]>220}
    }
    fun black():Boolean {
        val rgba=data.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        for(y in 0 until height)for(x in 0 until width) {
            if(rgba.getInt(base+y*stride+x*4) and 0x00ffffff != 0)return false
        }
        return true
    }
    fun fixtureCounter():Int? {
        for(flip in listOf(false,true)) {
            fun row(y:Float)=if(flip)1-y else y
            val header=rgb(.5f,row(.895f))
            if(header[0]<220 || header[1] !in 85..175 || header[2]>40)continue
            val bits=IntArray(32)
            for(i in 0..31) {
                val c=rgb((i+.5f)/32,row(.95f))
                bits[i]=if(c.all{it>210})1 else if(c.all{it<40})0 else -1
            }
            if(bits.none{it<0} && (0..15).all{bits[it]!=bits[it+16]})return (0..15).sumOf{bits[it] shl it}
        }
        return null
    }
    fun bottomUp(result:ByteBuffer=ByteBuffer.allocateDirect(width*height*4)):ByteBuffer {
        require(!result.isReadOnly && result.capacity()>=width*height*4){"Capture buffer too small or read-only"}
        result.clear();result.limit(width*height*4)
        val row=data.duplicate()
        for(y in height-1 downTo 0) {
            row.limit(data.limit());row.position(base+y*stride);row.limit(row.position()+width*4);result.put(row)
        }
        result.flip();return result
    }
}
