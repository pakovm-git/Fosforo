package dev.pako.bfi.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.ViewTreeObserver
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import dev.pako.bfi.R
import dev.pako.bfi.model.BfiPattern
import kotlin.math.min

/** One clock draws both examples. This is a local demo, not a measurement of panel presentation. */
class MotionPreview @JvmOverloads constructor(context: Context, attrs: AttributeSet?=null) : View(context,attrs), Choreographer.FrameCallback {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val choreographer=Choreographer.getInstance()
    private val visibleBounds=Rect()
    private val crt=CrtPreview(resources)
    private val visibilityListener=ViewTreeObserver.OnScrollChangedListener { updateRunning() }
    private val layoutListener=ViewTreeObserver.OnGlobalLayoutListener { updateRunning() }
    private var attached=false
    private var running=false
    private var origin=0L
    private var frameTime=0L
    var reportedHz=60.0
    var pattern=BfiPattern()
    override fun onAttachedToWindow() { super.onAttachedToWindow(); attached=true; viewTreeObserver.addOnScrollChangedListener(visibilityListener); viewTreeObserver.addOnGlobalLayoutListener(layoutListener); updateRunning() }
    override fun onDetachedFromWindow() { attached=false; viewTreeObserver.removeOnScrollChangedListener(visibilityListener); viewTreeObserver.removeOnGlobalLayoutListener(layoutListener); updateRunning(); super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); updateRunning() }
    override fun onVisibilityAggregated(isVisible: Boolean) { super.onVisibilityAggregated(isVisible); updateRunning() }
    private fun updateRunning() {
        val shouldRun=attached && isShown && windowVisibility == VISIBLE && getGlobalVisibleRect(visibleBounds)
        if (shouldRun == running) return
        running=shouldRun
        if (running) { origin=0; choreographer.postFrameCallback(this) } else choreographer.removeFrameCallback(this)
    }
    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (origin == 0L) origin=frameTimeNanos
        reportedHz=display?.refreshRate?.toDouble() ?: reportedHz
        frameTime=frameTimeNanos
        invalidate(); choreographer.postFrameCallback(this)
    }
    override fun onDraw(canvas: Canvas) {
        val density=resources.displayMetrics.density
        val gap=8*density; val pane=(width-gap)/2f
        if (pane <= 0 || height <= 0) return
        val elapsed=(frameTime-origin).coerceAtLeast(0)
        val black=LocalBfiClock.isBlack(frameTime,reportedHz,pattern)
        val motion=(elapsed%2_000_000_000L)/2_000_000_000.0
        val bounce=4.0*motion*(1.0-motion)
        val radius=min(pane,height.toFloat())*0.12f
        val y=(height-radius-4*density-(height-2*radius-8*density)*bounce).toFloat()
        for (index in 0..1) {
            val x=index*(pane+gap)
            val save=canvas.save(); canvas.clipRect(x,0f,x+pane,height.toFloat())
            val filtered=index == if (layoutDirection == LAYOUT_DIRECTION_RTL) 0 else 1
            if (filtered && black) {
                paint.style=Paint.Style.FILL; paint.color=Color.BLACK
                canvas.drawRect(x,0f,x+pane,height.toFloat(),paint)
            } else crt.draw(canvas,x,pane,height.toFloat(),y,radius,elapsed/1_000_000_000.0)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=density; paint.color=context.getColor(R.color.bfi_focus)
            canvas.drawRect(x+density, density,x+pane-density,height-density,paint)
            canvas.restoreToCount(save)
        }
    }
}
