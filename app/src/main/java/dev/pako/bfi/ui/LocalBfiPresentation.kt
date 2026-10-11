package dev.pako.bfi.ui

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.Choreographer
import android.view.View
import android.widget.FrameLayout
import dev.pako.bfi.R
import dev.pako.bfi.model.BfiPattern
import dev.pako.bfi.state.BfiRuntime

/** Normal in-window decoration, so Android overlay security never blocks these controls. */
class LocalBfiPresentation(private val activity: Activity,private val runtime: BfiRuntime) {
    private val host=activity.findViewById<FrameLayout>(android.R.id.content)
    private val layer=Layer(activity)
    private val originalMode=activity.window.attributes.preferredDisplayModeId
    private var unsubscribe: (() -> Unit)?=null
    init { host.addView(layer,FrameLayout.LayoutParams(-1,-1)) }
    fun start() {
        unsubscribe=runtime.subscribe {
            val settings=runtime.repository.committedSettings ?: runtime.repository.settings
            layer.pattern=settings?.globalPattern ?: BfiPattern()
            layer.rate=runtime.display()?.refreshRate?.toDouble() ?: 60.0
            layer.preview=activity.findViewById(R.id.motion_preview)
            layer.animate=runtime.state.userEnabled
            val mode=if (runtime.state.userEnabled && settings?.requestRefresh == true) runtime.resolved(settings)?.id ?: originalMode else originalMode
            val attrs=activity.window.attributes
            if (attrs.preferredDisplayModeId != mode) { attrs.preferredDisplayModeId=mode; activity.window.attributes=attrs }
        }
    }
    fun stop() {
        unsubscribe?.invoke(); unsubscribe=null; layer.animate=false
        val attrs=activity.window.attributes
        if (attrs.preferredDisplayModeId != originalMode) { attrs.preferredDisplayModeId=originalMode; activity.window.attributes=attrs }
    }
    private class Layer(activity: Activity): View(activity),Choreographer.FrameCallback {
        private val choreographer=Choreographer.getInstance()
        private val paint=Paint().apply { color=Color.BLACK }
        private val location=IntArray(2)
        private val previewLocation=IntArray(2)
        private val visible=Rect()
        private val hole=Rect()
        private var pending=false
        private var black=false
        var pattern=BfiPattern()
        var rate=60.0
        var preview: MotionPreview?=null
        var animate=false
            set(value) {
                field=value
                if (value) schedule() else { choreographer.removeFrameCallback(this); pending=false; black=false; invalidate() }
            }
        init { isClickable=false; isFocusable=false; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
        private fun schedule() {
            if (animate && !pending && isAttachedToWindow && windowVisibility == VISIBLE) { pending=true; choreographer.postFrameCallback(this) }
        }
        override fun onAttachedToWindow() { super.onAttachedToWindow(); schedule() }
        override fun onDetachedFromWindow() { choreographer.removeFrameCallback(this); pending=false; super.onDetachedFromWindow() }
        override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); if (visibility == VISIBLE) schedule() else { choreographer.removeFrameCallback(this); pending=false } }
        override fun doFrame(frameTimeNanos: Long) {
            pending=false
            if (!animate || !isAttachedToWindow || windowVisibility != VISIBLE) return
            // Display information is refreshed for dynamic-rate changes; it is never treated as a physical measurement.
            rate=display?.refreshRate?.toDouble() ?: rate
            black=LocalBfiClock.isBlack(frameTimeNanos,rate,pattern)
            invalidate(); schedule()
        }
        override fun onDraw(canvas: Canvas) {
            if (!black) return
            val save=canvas.save()
            preview?.takeIf { it.isShown && it.getGlobalVisibleRect(visible) }?.let { sample ->
                getLocationOnScreen(location); sample.getLocationOnScreen(previewLocation)
                // Each comparison owns its filtered pane and clock. In Settings it may preview
                // a per-app pattern; a global mask must not add unrelated black slots to it.
                hole.set(previewLocation[0],previewLocation[1],previewLocation[0]+sample.width,previewLocation[1]+sample.height)
                if (hole.intersect(visible)) {
                    hole.offset(-location[0],-location[1]); canvas.clipOutRect(hole)
                }
            }
            canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint)
            canvas.restoreToCount(save)
        }
    }
}
