package dev.pako.bfi.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import dev.pako.bfi.R
import kotlin.math.PI

/** Fixed raster assets approximate the approved CRT look. No custom shader or runtime blur.
 * Shared immutable bitmaps decode once; the draw loop only composites cached images.
 */
internal object CrtPreviewAssets {
    data class Images(val background: Bitmap,val ball: Bitmap,val textures: Array<Bitmap>,val vignette: Bitmap)
    private var cached: Images?=null
    fun load(resources: Resources): Images = cached ?: run {
        val options=BitmapFactory.Options().apply { inScaled=false }
        fun decode(id: Int)=checkNotNull(BitmapFactory.decodeResource(resources,id,options))
        Images(decode(R.drawable.crt_background),decode(R.drawable.crt_ball),
            intArrayOf(R.drawable.crt_texture_0,R.drawable.crt_texture_1,R.drawable.crt_texture_2,R.drawable.crt_texture_3,
                R.drawable.crt_texture_4,R.drawable.crt_texture_5,R.drawable.crt_texture_6,R.drawable.crt_texture_7).map(::decode).toTypedArray(),
            decode(R.drawable.crt_vignette)).also { cached=it }
    }
}

internal class CrtPreview(resources: Resources) {
    private val images=CrtPreviewAssets.load(resources)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val pane=RectF()
    private val sprite=RectF()
    fun draw(canvas: Canvas,x: Float,width: Float,height: Float,y: Float,radius: Float,elapsedSeconds: Double) {
        pane.set(x,0f,x+width,height)
        canvas.drawBitmap(images.background,null,pane,paint)
        // The cached ball has a 21.6px radius on a 112px transparent sprite.
        val half=radius*(112f/43.2f)
        val curvedY=height/2f+(y-height/2f)/1.012f
        sprite.set(x+width/2f-half,curvedY-half,x+width/2f+half,curvedY+half)
        canvas.drawBitmap(images.ball,null,sprite,paint)
        val texture=((elapsedSeconds*3.5/(2.0*PI)*images.textures.size).toLong()%images.textures.size).toInt()
        canvas.drawBitmap(images.textures[texture],null,pane,paint)
        canvas.drawBitmap(images.vignette,null,pane,paint)
    }
}
