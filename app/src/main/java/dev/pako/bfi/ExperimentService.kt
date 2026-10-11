package dev.pako.bfi

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.os.*
import android.view.*
import dev.pako.bfi.model.SessionMode
import dev.pako.bfi.rendering.OverlayRenderer
import dev.pako.bfi.rules.ForegroundAppMonitor
import dev.pako.bfi.state.*
import java.util.Locale
import kotlin.math.abs

/** Android resource adapter; all enabled intent and transitions belong to BfiController. */
class ExperimentService : Service(), DisplayManager.DisplayListener {
    companion object {
        const val ACTION_START = "dev.pako.bfi.START"
        const val ACTION_STOP = "dev.pako.bfi.STOP"
        const val EXTRA_SESSION = "session"
        const val CHANNEL = "bfi_experiment" // Preserve the installed user's channel choice.
        private const val NOTIFICATION_ID = 1
        fun prepareChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL,context.getString(R.string.notification_channel),NotificationManager.IMPORTANCE_LOW))
        }
        fun notificationsReady(context: Context): Boolean {
            val manager=context.getSystemService(NotificationManager::class.java)
            return manager.areNotificationsEnabled() && (manager.getNotificationChannel(CHANNEL)?.importance ?: 0) != NotificationManager.IMPORTANCE_NONE
        }
    }
    private val runtime get() = (application as BfiApplication).runtime
    private val main = Handler(Looper.getMainLooper())
    private lateinit var windows: WindowManager
    private lateinit var displays: DisplayManager
    private lateinit var monitor: ForegroundAppMonitor
    private var view: SurfaceView? = null
    private var surfaceCallback: SurfaceHolder.Callback? = null
    private var renderer: OverlayRenderer? = null
    private var renderEpoch: Epoch? = null
    private var pendingEpoch: Epoch? = null
    private var settle: Runnable? = null
    private var waitWorker: Runnable? = null
    private var settling=false
    private var requestedHz=0.0
    private var requestedModeId=0
    private var ending=false
    private var watching=false
    private var rate=0.0
    private var modeId=0
    private var rotation=0
    private var notificationKey=""
    private var epochStart=0L
    private var epochPattern=dev.pako.bfi.model.BfiPattern()
    private var epochOutcome=RequestOutcome.OFF
    private var boundEpoch: Epoch?=null
    private var lastEpoch: Epoch?=null
    private var lastSummary: TrialSummary?=null
    private var lastRecordedReason: Reason?=null
    private val emptySummary=TrialSummary(0,0,0,0,0.0,0.0,0.0,0)
    private val watchdog = object : Runnable {
        override fun run() {
            val epoch=renderEpoch ?: return
            if (!runtime.accepts(epoch)) return
            runtime.reconcile()
            if (!runtime.accepts(epoch) || view == null || (settling && settle != null)) return
            val last=renderer?.lastPostUptimeMillis ?: 0L
            val now=SystemClock.uptimeMillis()
            if (now-epochStart > 1500 && (last == 0L || now-last > 750)) runtime.failure(epoch,Reason.STALLED)
            else main.postDelayed(this,250)
        }
    }
    private val countdown = object : Runnable {
        override fun run() {
            if (!runtime.state.userEnabled || runtime.state.mode !is SessionMode.Timed) return
            stateChanged(); main.postDelayed(this,1000)
        }
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> { runtime.appChanged(null,true); updateMonitoring() }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> { runtime.appChanged(null,true); updateMonitoring() }
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        windows=getSystemService(WindowManager::class.java); displays=getSystemService(DisplayManager::class.java)
        monitor=ForegroundAppMonitor(this,main) { pkg,access -> runtime.appChanged(pkg,access) }
        prepareChannel(this)
    }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if (intent?.action == ACTION_STOP) { runtime.stop(); if (!runtime.state.userEnabled) endSession(); return START_NOT_STICKY }
        if (intent?.action != ACTION_START || !runtime.state.userEnabled || intent.getLongExtra(EXTRA_SESSION,-1) != runtime.state.session) {
            if (!runtime.state.userEnabled) stopSelf(); return START_NOT_STICKY
        }
        try {
            ending=false // A new eligible session may arrive before the previous service instance is destroyed.
            boundEpoch=runtime.state.epoch
            lastEpoch=null; lastSummary=null; lastRecordedReason=null
            epochPattern=runtime.state.effectivePattern
            epochOutcome=RequestOutcome.OFF
            val initialDisplay=runtime.display()
            rate=initialDisplay?.refreshRate?.toDouble() ?: 0.0; modeId=initialDisplay?.mode?.modeId ?: 0; rotation=initialDisplay?.rotation ?: 0
            val notification=makeNotification()
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(NOTIFICATION_ID,notification)
            if (!watching) {
                displays.registerDisplayListener(this,main)
                val filter=IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT) }
                if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver,filter,RECEIVER_NOT_EXPORTED) else registerReceiver(screenReceiver,filter)
                watching=true
            }
            if (!runtime.attach(this)) endSession()
            updateMonitoring()
            if (runtime.state.mode is SessionMode.Timed) { main.removeCallbacks(countdown); main.post(countdown) }
        } catch (_: RuntimeException) { runtime.failure(runtime.state.epoch,Reason.SERVICE,false); endSession() }
        return START_NOT_STICKY
    }
    fun updateMonitoring() {
        if (ending) return
        if (runtime.state.userEnabled && runtime.awake() && !runtime.controlsVisible && runtime.state.settings.needsAppIdentity) monitor.start() else monitor.stop()
    }
    fun requestRender(epoch: Epoch) {
        if (!runtime.accepts(epoch) || ending || epoch == renderEpoch || epoch == pendingEpoch) return
        removeRender()
        if (!runtime.accepts(epoch)) return
        pendingEpoch=epoch
        if (OverlayRenderer.workerIsBusy()) {
            val timeout=Runnable {
                if (pendingEpoch == epoch && runtime.accepts(epoch)) {
                    if (!OverlayRenderer.workerIsBusy()) addOverlay(epoch) else runtime.failure(epoch,Reason.BUSY)
                }
            }
            waitWorker=timeout; main.postDelayed(timeout,2000)
        } else addOverlay(epoch)
    }
    private fun workerFinished() {
        val epoch=pendingEpoch ?: return
        if (!runtime.accepts(epoch) || ending) return
        waitWorker?.let { main.removeCallbacks(it) }; waitWorker=null
        addOverlay(epoch)
    }
    private fun addOverlay(epoch: Epoch) {
        if (!runtime.accepts(epoch) || pendingEpoch != epoch || OverlayRenderer.workerIsBusy() || !runtime.awake()) return
        val display=runtime.display() ?: run { runtime.failure(epoch,Reason.NO_DISPLAY,false); return }
        val target=if (runtime.state.settings.requestRefresh) runtime.resolved(runtime.state.settings) else null
        if (runtime.state.settings.requestRefresh && target == null) { runtime.failure(epoch,Reason.TARGET_UNAVAILABLE,false); return }
        pendingEpoch=null; renderEpoch=epoch
        epochPattern=runtime.state.effectivePattern
        epochOutcome=if (target == null) RequestOutcome.OFF else RequestOutcome.SETTLING
        settling=target != null; requestedHz=target?.hz ?: 0.0; requestedModeId=target?.id ?: 0
        rate=display.refreshRate.toDouble(); modeId=display.mode.modeId; rotation=display.rotation
        val surface=SurfaceView(this)
        surface.setZOrderOnTop(true); surface.holder.setFormat(PixelFormat.TRANSLUCENT)
        val callback=object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (!runtime.accepts(epoch) || view !== surface) return
                if (target != null) {
                    if (Build.VERSION.SDK_INT >= 30) try { holder.surface.setFrameRate(target.hz.toFloat(),Surface.FRAME_RATE_COMPATIBILITY_DEFAULT) }
                    catch (_: RuntimeException) { /* A rejected vote is reported by actual observed rate. */ }
                    settling=true
                    runtime.observed(epoch,rate,RequestOutcome.SETTLING)
                    val finish=Runnable { beginRenderer(epoch,holder,target.hz) }
                    settle=finish; main.postDelayed(finish,(epochStart+2000-SystemClock.uptimeMillis()).coerceAtLeast(0))
                    val actual=runtime.display()
                    if (actual?.mode?.modeId == requestedModeId && abs(actual.refreshRate-requestedHz) <= 0.1) finish.run()
                } else beginRenderer(epoch,holder,null)
            }
            override fun surfaceChanged(holder: SurfaceHolder,format: Int,width: Int,height: Int) = Unit
            override fun surfaceDestroyed(holder: SurfaceHolder) { if (runtime.accepts(epoch) && view === surface) runtime.failure(epoch,Reason.SURFACE) }
        }
        surfaceCallback=callback; surface.holder.addCallback(callback)
        val params=WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT)
        params.gravity=Gravity.TOP or Gravity.START; params.alpha=1f
        params.preferredDisplayModeId=target?.id ?: 0
        view=surface
        epochStart=SystemClock.uptimeMillis()
        try { windows.addView(surface,params); main.removeCallbacks(watchdog); main.post(watchdog) }
        catch (_: RuntimeException) { runtime.failure(epoch,Reason.SURFACE) }
    }
    private fun beginRenderer(epoch: Epoch,holder: SurfaceHolder,targetHz: Double?) {
        if (!runtime.accepts(epoch) || renderEpoch != epoch || renderer != null || view?.holder !== holder) return
        settle?.let { main.removeCallbacks(it) }; settle=null; settling=false
        val display=runtime.display() ?: run { runtime.failure(epoch,Reason.NO_DISPLAY,false); return }
        rate=display.refreshRate.toDouble(); modeId=display.mode.modeId; rotation=display.rotation
        if (!rate.isFinite() || rate < 100 || !runtime.awake()) { runtime.reconcile(); if (runtime.accepts(epoch)) runtime.failure(epoch,Reason.LOW_RATE,false); return }
        val outcome=if (targetHz == null) RequestOutcome.OFF else if (abs(rate-targetHz) <= 0.1) RequestOutcome.APPLIED else RequestOutcome.OVERRIDDEN
        epochOutcome=outcome
        runtime.observed(epoch,rate,outcome)
        val next=OverlayRenderer(holder,(1_000_000_000.0/rate).toLong(),runtime.state.effectivePattern,main,
            firstPost={ runtime.firstFrame(epoch) },failed={ reason ->
                runtime.failure(epoch,when (reason) { R.string.reason_unstable -> Reason.TIMING; else -> Reason.SURFACE })
            },stopped={ workerFinished() })
        renderer=next; epochStart=SystemClock.uptimeMillis()
        if (!next.start()) runtime.failure(epoch,Reason.BUSY) else { main.removeCallbacks(watchdog); main.post(watchdog) }
    }
    /** Release resources immediately, without waiting on a potentially stuck rendering thread. */
    fun removeRender() {
        pendingEpoch=null
        waitWorker?.let { main.removeCallbacks(it) }; waitWorker=null
        settle?.let { main.removeCallbacks(it) }; settle=null; settling=false
        main.removeCallbacks(watchdog)
        val old=renderer; val oldView=view; val oldEpoch=renderEpoch
        renderer=null; view=null; renderEpoch=null
        old?.stop()
        if (oldView != null) {
            surfaceCallback?.let { oldView.holder.removeCallback(it) }
            if (Build.VERSION.SDK_INT >= 30 && runtime.state.settings.requestRefresh && oldView.holder.surface.isValid) try {
                oldView.holder.surface.setFrameRate(0f,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
            } catch (_: RuntimeException) { }
            try { windows.removeViewImmediate(oldView) }
            catch (_: IllegalArgumentException) { /* The window was already detached. */ }
            catch (_: RuntimeException) {
                oldView.visibility=View.GONE
                try { windows.removeView(oldView) } catch (_: RuntimeException) { }
                if (runtime.state.userEnabled) runtime.failure(runtime.state.epoch,Reason.SURFACE,false)
            }
        }
        surfaceCallback=null
        if (oldEpoch != null) record(old?.snapshot() ?: emptySummary,oldEpoch)
    }
    fun currentSummary() = renderer?.snapshot()
    fun currentReport(): String? = renderer?.snapshot()?.let { summary -> renderEpoch?.let { buildReport(summary,it) } }
    private fun buildReport(summary: TrialSummary,epoch: Epoch): String {
        val s=runtime.state
        val report=String.format(Locale.ROOT,
            "session=%d\ngeneration=%d\nmode=%s\nselectedPattern=%d/%d\neffectivePattern=%d/%d\nrequestedTarget=%s\nrequestEnabled=%s\nrequestOutcome=%s\nreportedHz=%.4f\nmodeId=%d\nactualSessionSeconds=%.3f\nlastEpochCallbacks=%d\nlastEpochCpuPosts=%d\nestimatedSkippedSlots=%d\ncpuPostsAfterPredictedDeadline=%d\nmeanCallbackIntervalMs=%.4f\nrecentCallbackCadenceHz=%.4f\nrecentMedianIntervalMs=%.4f\nreason=%s\nphysicalPresentation=UNMEASURED\n",
            epoch.session,epoch.generation,s.mode,s.settings.globalPattern.visibleSlots,s.settings.globalPattern.blackSlots,
            epochPattern.visibleSlots,epochPattern.blackSlots,s.settings.refreshTarget,s.settings.requestRefresh,epochOutcome,
            rate,modeId,runtime.elapsedSessionSeconds(),summary.callbacks,summary.posts,summary.skipped,summary.late,
            summary.meanIntervalMillis,summary.recentCallbackCadenceHz,summary.recentMedianMillis,s.reason)
        return report
    }
    private fun record(summary: TrialSummary,epoch: Epoch) {
        val report=buildReport(summary,epoch)
        lastEpoch=epoch; lastSummary=summary; lastRecordedReason=runtime.state.reason
        runtime.record(epoch.session,summary,report)
    }
    fun endSession() {
        if (ending) return
        ending=true; removeRender(); monitor.stop(); main.removeCallbacks(countdown)
        boundEpoch?.let { epoch ->
            if (epoch.session == runtime.state.session && lastRecordedReason != runtime.state.reason) record(lastSummary ?: emptySummary,lastEpoch ?: epoch)
        }
        if (watching) { displays.unregisterDisplayListener(this); unregisterReceiver(screenReceiver); watching=false }
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    fun stateChanged() {
        if (ending || !runtime.state.userEnabled) return
        val s=runtime.state
        val seconds=s.deadlineUptimeMillis?.let { ((it-SystemClock.uptimeMillis()+999)/1000).coerceAtLeast(0) }
        val key="${s.phase}|${s.reason}|${s.mode}|$seconds"
        if (key == notificationKey) return
        notificationKey=key
        try { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID,makeNotification()) }
        catch (_: RuntimeException) { runtime.failure(s.epoch,Reason.NOTIFICATION,false) }
    }
    private fun makeNotification(): Notification {
        val stop=PendingIntent.getService(this,1,Intent(this,ExperimentService::class.java).setAction(ACTION_STOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open=PendingIntent.getActivity(this,2,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val s=runtime.state
        val body=when (s.phase) {
            Phase.SUSPENDED -> if (s.mode is SessionMode.Timed) getString(R.string.notification_timed_paused,UiText.reason(this,s.reason),UiText.integer(((s.deadlineUptimeMillis ?: 0)-SystemClock.uptimeMillis()+999).coerceAtLeast(0)/1000))
                else getString(R.string.notification_paused,UiText.reason(this,s.reason))
            Phase.RETRY_WAIT -> getString(R.string.notification_retry)
            Phase.STARTING -> if (s.mode is SessionMode.Timed) getString(R.string.notification_timed_starting,UiText.integer(((s.deadlineUptimeMillis ?: 0)-SystemClock.uptimeMillis()+999).coerceAtLeast(0)/1000)) else getString(R.string.state_starting)
            else -> if (s.mode is SessionMode.Timed) getString(R.string.notification_timed,UiText.integer(((s.deadlineUptimeMillis ?: 0)-SystemClock.uptimeMillis()+999).coerceAtLeast(0)/1000)) else getString(R.string.notification_continuous)
        }
        return Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_bfi).setContentTitle(getString(R.string.app_name)).setContentText(body)
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(Icon.createWithResource(this,R.drawable.ic_bfi),getString(R.string.stop),stop).build()).build()
    }
    override fun onDisplayChanged(displayId: Int) {
        if (displayId != Display.DEFAULT_DISPLAY || ending || !runtime.state.userEnabled) return
        if (!runtime.awake()) { runtime.reconcile(); updateMonitoring(); return }
        if (settling) {
            val actual=runtime.display()
            if (actual?.mode?.modeId == requestedModeId && abs(actual.refreshRate-requestedHz) <= 0.1) settle?.run()
            return // Display events may finish early; the one-shot deadline bounds ignored votes.
        }
        val epoch=renderEpoch
        val display=runtime.display()
        if (epoch != null && display != null && (display.mode.modeId != modeId || display.rotation != rotation || abs(display.refreshRate-rate) > 0.1)) runtime.failure(epoch,Reason.DISPLAY)
        else runtime.reconcile()
    }
    override fun onDisplayAdded(displayId: Int) { if (displayId == Display.DEFAULT_DISPLAY) runtime.reconcile() }
    override fun onDisplayRemoved(displayId: Int) { if (displayId == Display.DEFAULT_DISPLAY) runtime.failure(runtime.state.epoch,Reason.NO_DISPLAY,false) }
    override fun onDestroy() {
        runtime.detached(this); endSession(); monitor.close(); super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
