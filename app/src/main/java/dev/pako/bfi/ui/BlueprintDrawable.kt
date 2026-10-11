package dev.pako.bfi.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import dev.pako.bfi.R

/** Static page grid. Geometry is cached until the window bounds change; no animation or bitmap. */
class BlueprintDrawable(context: Context) : Drawable() {
    private val density=context.resources.displayMetrics.density
    private val page=Paint().apply { color=context.getColor(R.color.bfi_page) }
    private val lines=Paint().apply {
        color=context.getColor(R.color.bfi_grid)
        style=Paint.Style.STROKE
        strokeWidth=0.5f*density
    }
    private val grid=Path()
    override fun onBoundsChange(bounds: Rect) {
        grid.rewind()
        val step=16f*density
        var x=bounds.left.toFloat()
        while (x < bounds.right) {
            grid.moveTo(x,bounds.top.toFloat()); grid.lineTo(x,bounds.bottom.toFloat()); x+=step
        }
        var y=bounds.top.toFloat()
        while (y < bounds.bottom) {
            grid.moveTo(bounds.left.toFloat(),y); grid.lineTo(bounds.right.toFloat(),y); y+=step
        }
    }
    override fun draw(canvas: Canvas) { canvas.drawRect(bounds,page); canvas.drawPath(grid,lines) }
    override fun setAlpha(alpha: Int) { page.alpha=alpha; lines.alpha=alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { page.colorFilter=colorFilter; lines.colorFilter=colorFilter; invalidateSelf() }
    @Deprecated("Required by Drawable on supported Android versions")
    override fun getOpacity()=PixelFormat.TRANSLUCENT
}
