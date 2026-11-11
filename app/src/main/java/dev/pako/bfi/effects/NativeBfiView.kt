package dev.pako.bfi.effects

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.Choreographer
import android.view.View

/** A display-list blackout above CRT in the same trusted accessibility window. */
@android.annotation.TargetApi(33)
internal class NativeBfiView(context: Context, private val output: OverlayTextureView) : View(context) {
    private val choreographer = Choreographer.getInstance()
    private var clock: PresentationClock? = null
    private var clockHz=0.0
    private var running = false
    private var enabled = false
    private var black = false
    private var targetNanos = 0L
    private val callback = object : Choreographer.VsyncCallback {
        override fun onVsync(data: Choreographer.FrameData) {
            if (!running) return
            if(clockHz!=output.engine.hz){clockHz=output.engine.hz;clock=PresentationClock(1e9/clockHz)}
            targetNanos = data.preferredFrameTimeline.expectedPresentationTimeNanos
            val slot = clock!!.slot(targetNanos)
            val blackout = Session.bfi
            val nextEnabled = blackout
            black = blackout && Session.pattern.blackAt(slot)
            output.requestRender(data.frameTimeNanos,targetNanos,slot)
            if (enabled || nextEnabled) invalidate()
            enabled = nextEnabled
            choreographer.postVsyncCallback(this)
        }
    }
    init { isClickable = false; isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        clockHz=output.engine.hz;clock = PresentationClock(1e9 / clockHz)
        running = true
        choreographer.postVsyncCallback(callback)
    }
    override fun onDetachedFromWindow() { close(); super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (black) canvas.drawColor(Color.BLACK)
        output.cadence.nativeDraw(targetNanos, black)
    }
    fun resume() { if(!running){running=true;choreographer.postVsyncCallback(callback)} }
    fun close() { running = false; choreographer.removeVsyncCallback(callback) }
}
