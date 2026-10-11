package dev.pako.bfi.rendering

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Choreographer
import android.view.SurfaceHolder
import dev.pako.bfi.R
import dev.pako.bfi.TrialSummary
import dev.pako.bfi.model.BfiPattern
import dev.pako.bfi.timing.SlotClock
import dev.pako.bfi.timing.CallbackIntervals
import java.util.concurrent.atomic.AtomicBoolean

/** One display-driven worker; no UI work, busy loop, or per-frame logging. */
class OverlayRenderer(
    private val holder: SurfaceHolder,
    private val periodNanos: Long,
    private val pattern: BfiPattern,
    private val main: Handler,
    private val firstPost: () -> Unit,
    private val failed: (Int) -> Unit,
    private val stopped: () -> Unit
) {
    companion object {
        private val workerLease = AtomicBoolean(false)
        fun workerIsBusy() = workerLease.get()
    }

    private val active = AtomicBoolean(false)
    private lateinit var worker: HandlerThread
    private lateinit var handler: Handler
    private lateinit var choreographer: Choreographer
    private var api33: Api33Callbacks? = null
    private val clock = SlotClock(periodNanos)
    private val intervals = CallbackIntervals()
    @Volatile private var callbacks = 0L
    @Volatile private var posts = 0L
    @Volatile private var late = 0L
    @Volatile private var skipped = 0L
    @Volatile private var intervalTotal = 0L
    @Volatile var lastPostUptimeMillis = 0L
        private set
    private var previousFrameTime = 0L
    private var consecutiveGaps = 0
    private val frameCallback = Choreographer.FrameCallback { frameTime -> render(frameTime, 0L, frameTime) }

    fun start(): Boolean {
        if (!workerLease.compareAndSet(false, true)) return false
        active.set(true)
        worker = object : HandlerThread("BfiExperimentRender") {
            override fun run() {
                try { super.run() } finally { workerLease.set(false); main.post { stopped() } }
            }
        }
        worker.start()
        handler = Handler(worker.looper)
        handler.post {
            if (!active.get()) return@post
            choreographer = Choreographer.getInstance()
            if (Build.VERSION.SDK_INT >= 33) api33 = Api33Callbacks(choreographer, object : FrameConsumer {
                override fun consume(target: Long, deadline: Long, frameTime: Long) = render(target, deadline, frameTime)
            })
            schedule()
        }
        return true
    }

    fun stop() {
        active.set(false)
        if (!::handler.isInitialized) return
        handler.post {
            if (::choreographer.isInitialized) {
                if (Build.VERSION.SDK_INT >= 33) api33?.remove() else choreographer.removeFrameCallback(frameCallback)
            }
            worker.quitSafely()
        }
    }

    fun snapshot(): TrialSummary {
        val recent = intervals.snapshot()
        return TrialSummary(
            callbacks, posts, skipped, late,
            if (callbacks > 1) intervalTotal.toDouble() / (callbacks - 1) / 1_000_000 else 0.0,
            recent.estimatedHz, recent.medianMillis, recent.samples
        )
    }

    private fun schedule() {
        if (!active.get()) return
        if (Build.VERSION.SDK_INT >= 33) api33?.post() else choreographer.postFrameCallback(frameCallback)
    }

    private fun fail(reason: Int) {
        if (!active.getAndSet(false)) return
        main.post { failed(reason) }
    }

    private fun render(target: Long, deadline: Long, frameTime: Long) {
        if (!active.get()) return
        callbacks++
        intervals.record(frameTime)
        if (previousFrameTime != 0L) intervalTotal += (frameTime - previousFrameTime).coerceAtLeast(0)
        previousFrameTime = frameTime
        val slot = clock.next(target)
        if (slot < -1) {
            fail(R.string.reason_unstable)
            return
        }
        if (slot == -1L) { schedule(); return }
        consecutiveGaps = if (clock.skippedSlots > skipped) consecutiveGaps + 1 else 0
        skipped = clock.skippedSlots
        if (consecutiveGaps >= 8) {
            fail(R.string.reason_unstable)
            return
        }
        try {
            val canvas = holder.lockHardwareCanvas()
            try {
                // Full-buffer overwrite; do not assume preserved buffer contents.
                if (active.get() && SlotClock.isBlack(slot, pattern.visibleSlots, pattern.blackSlots)) {
                    canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
                } else {
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                }
            } finally { holder.unlockCanvasAndPost(canvas) }
            if (!active.get()) return
            posts++
            lastPostUptimeMillis = android.os.SystemClock.uptimeMillis()
            if (deadline > 0 && System.nanoTime() > deadline) late++
            if (posts == 1L) main.post { firstPost() }
        } catch (_: RuntimeException) {
            fail(R.string.reason_surface)
            return
        }
        schedule()
    }

    @androidx.annotation.RequiresApi(33)
    private class Api33Callbacks(
        private val choreographer: Choreographer,
        private val consumer: FrameConsumer
    ) {
        private val callback = Choreographer.VsyncCallback { data ->
            val timeline = data.preferredFrameTimeline
            consumer.consume(timeline.expectedPresentationTimeNanos, timeline.deadlineNanos, data.frameTimeNanos)
        }
        fun post() = choreographer.postVsyncCallback(callback)
        fun remove() = choreographer.removeVsyncCallback(callback)
    }

    private interface FrameConsumer {
        fun consume(target: Long, deadline: Long, frameTime: Long)
    }
}
