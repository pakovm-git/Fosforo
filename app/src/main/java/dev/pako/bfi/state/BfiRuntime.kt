package dev.pako.bfi.state

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.service.quicksettings.TileService
import android.view.Display
import dev.pako.bfi.*
import dev.pako.bfi.display.*
import dev.pako.bfi.model.*
import dev.pako.bfi.rules.*
import dev.pako.bfi.settings.*
import android.util.AtomicFile
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File

class BfiRuntime(private val context: Context, val main: Handler, val repository: SettingsRepository) {
    private val controller = BfiController()
    val state get() = controller.state
    private var service: ExperimentService? = null
    private val scheduled = mutableListOf<Runnable>()
    private class Observer(val action: () -> Unit) { var active=true }
    private val observers = linkedSetOf<Observer>()
    private var lastPublishedState: BfiState?=null
    private var lastPublishedSettings: SettingsStatus?=null
    private var lastPublishedWriting=false
    private var reportVersion=0L
    private var publishedReportVersion=-1L
    private var reportSession=-1L
    private val reportWrites=Channel<String>(Channel.CONFLATED)
    var summary: TrialSummary? = null
        private set
    var lastReport: String? = null
        private set
    var foregroundPackage: String? = null
        private set
    private var startedElapsed=0L
    var controlsVisible=false
        private set
    private var userCommand=0L
    fun controlsVisibility(visible: Boolean) {
        if (controlsVisible == visible) return
        controlsVisible=visible
        foregroundPackage=null // Re-baseline identity after returning to another app.
        reconcile()
    }
    /** An explicit menu choice updates the preferred mode, then activates only if not superseded. */
    fun chooseMode(mode: ActivationMode,completed: (Boolean) -> Unit) {
        val token=++userCommand
        val settings=repository.settings ?: run { completed(false); return }
        val wasEnabled=state.userEnabled
        if (wasEnabled) dispatch(controller.stop())
        repository.save(settings.copy(activationMode=mode)) { success ->
            if (token != userCommand) return@save
            if (success && !repository.writing) start()
            completed(success && state.userEnabled)
        }
    }
    fun elapsedSessionSeconds() = (SystemClock.elapsedRealtime()-startedElapsed).coerceAtLeast(0)/1000.0
    fun accepts(epoch: Epoch) = controller.accepts(epoch)
    fun display(): Display? = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
    fun modes(): List<DisplayMode> = display()?.supportedModes?.map { DisplayMode(it.modeId,it.physicalWidth,it.physicalHeight,it.refreshRate.toDouble()) } ?: emptyList()
    fun resolved(s: BfiSettings): DisplayMode? = display()?.mode?.let { RefreshRequestResolver.resolve(modes(),it.physicalWidth,it.physicalHeight,s.refreshTarget) }
    fun awake() = context.getSystemService(PowerManager::class.java).isInteractive && !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
    init {
        repository.scope.launch {
            for (report in reportWrites) {
                val file=AtomicFile(File(context.filesDir,"last-trial.txt"))
                var stream: java.io.FileOutputStream?=null
                try { stream=file.startWrite(); stream.write(report.toByteArray()); file.finishWrite(stream) }
                catch (_: java.io.IOException) { file.failWrite(stream) }
            }
        }
        repository.subscribe {
            if (state.userEnabled && repository.status !is SettingsStatus.Ready) dispatch(controller.stop(Reason.SETTINGS,Phase.BLOCKED))
            else if (state.userEnabled) repository.committedSettings?.let { settings ->
                if (settings != state.settings) { dispatch(controller.updateSettings(settings)); reconcile() }
            }
            publish()
        }
    }
    fun subscribe(action: () -> Unit): () -> Unit {
        val observer=Observer(action); observers.add(observer); action()
        return { observer.active=false; observers.remove(observer) }
    }
    fun readiness(s: BfiSettings? = repository.settings, requireSaved: Boolean = true): Reason? {
        if (s == null || (requireSaved && repository.writing)) return Reason.SETTINGS
        if (s.acceptedNoticeVersion < 1) return Reason.NOTICE
        if (!Settings.canDrawOverlays(context)) return Reason.OVERLAY
        if (!ExperimentService.notificationsReady(context)) return Reason.NOTIFICATION
        if (s.needsAppIdentity && !ForegroundAppMonitor.hasAccess(context)) return Reason.USAGE
        if (s.activationMode == ActivationMode.SELECTED_APPS && s.includedPackages.isEmpty()) return Reason.EMPTY_SELECTED
        val display = display() ?: return Reason.NO_DISPLAY
        if (s.requestRefresh && resolved(s) == null) return Reason.TARGET_UNAVAILABLE
        val rate = if (s.requestRefresh && (!state.userEnabled || state.phase != Phase.RUNNING)) resolved(s)?.hz ?: 0.0 else display.refreshRate.toDouble()
        if (!rate.isFinite() || rate < 100.0) return Reason.LOW_RATE
        return null
    }
    fun start(mode: SessionMode = SessionMode.Continuous) {
        userCommand++
        if (state.userEnabled) return
        val s=repository.settings ?: BfiSettings()
        val problem=readiness() ?: if (!awake()) Reason.SLEEP else null
        val effects=controller.start(s,mode,SystemClock.uptimeMillis(),problem)
        publish()
        if (!state.userEnabled) { dispatch(effects); return }
        startedElapsed=SystemClock.elapsedRealtime()
        foregroundPackage=null
        try {
            context.startForegroundService(Intent(context,ExperimentService::class.java).setAction(ExperimentService.ACTION_START).putExtra(ExperimentService.EXTRA_SESSION,state.session))
            dispatch(effects)
        } catch (_: RuntimeException) { dispatch(controller.stop(Reason.SERVICE,Phase.BLOCKED)) }
    }
    fun stop(reason: Reason = Reason.MANUAL) { userCommand++; dispatch(controller.stop(reason)) }
    fun attach(adapter: ExperimentService): Boolean {
        if (!state.userEnabled) return false
        service=adapter
        reconcile()
        if (state.userEnabled && state.phase == Phase.STARTING) adapter.requestRender(state.epoch)
        adapter.stateChanged()
        return state.userEnabled
    }
    fun detached(adapter: ExperimentService) {
        if (service !== adapter) return
        service=null
        if (state.userEnabled) dispatch(controller.stop(Reason.SERVICE,Phase.FAILED))
    }
    fun appChanged(pkg: String?, hasAccess: Boolean) {
        foregroundPackage=pkg
        if (!hasAccess && state.userEnabled) dispatch(controller.stop(Reason.USAGE,Phase.BLOCKED)) else reconcile()
    }
    fun reconcile(retry: Boolean = false) {
        if (!state.userEnabled) return
        val eligibility=if (controlsVisible) Eligibility(null,Reason.CONTROLS) else AppRuleResolver.resolve(state.settings,foregroundPackage)
        dispatch(controller.reconcile(awake(),readiness(state.settings,requireSaved=false),eligibility.pattern,eligibility.reason,retry))
        service?.updateMonitoring()
    }
    fun firstFrame(epoch: Epoch) { dispatch(controller.firstFrame(epoch)) }
    fun failure(epoch: Epoch, reason: Reason, recoverable: Boolean = true) { dispatch(controller.fail(epoch,reason,recoverable)) }
    fun observed(epoch: Epoch,hz: Double,outcome: RequestOutcome) { controller.observed(epoch,hz,outcome); publish() }
    fun liveSummary() = if (state.userEnabled) service?.currentSummary() ?: summary?.takeIf { reportSession == state.session } else summary
    fun reportSnapshot() = if (state.userEnabled) service?.currentReport() ?: lastReport?.takeIf { reportSession == state.session } else lastReport
    fun readSavedReport(): String? = try { String(AtomicFile(File(context.filesDir,"last-trial.txt")).readFully()) } catch (_: java.io.IOException) { null }
    fun loadReport(report: String) { if (lastReport == null) { lastReport=report; reportVersion++; publish() } }
    fun record(session: Long,summary: TrialSummary?,report: String) {
        this.summary=summary; lastReport=report; reportSession=session; reportVersion++
        reportWrites.trySend(report); publish()
    }
    private fun publish() {
        val stateChanged=state != lastPublishedState
        val settingsChanged=repository.status != lastPublishedSettings || repository.writing != lastPublishedWriting
        if (!stateChanged && !settingsChanged && reportVersion == publishedReportVersion) return
        lastPublishedState=state; lastPublishedSettings=repository.status; lastPublishedWriting=repository.writing; publishedReportVersion=reportVersion
        observers.toList().forEach { if (it.active) it.action() }
        service?.stateChanged()
        if (stateChanged || settingsChanged) TileService.requestListeningState(context,ComponentName(context,BfiTileService::class.java))
    }
    private fun schedule(delay: Long, action: () -> Unit) {
        lateinit var runnable: Runnable
        runnable=Runnable { scheduled.remove(runnable); action() }
        scheduled.add(runnable); main.postDelayed(runnable,delay.coerceAtLeast(0))
    }
    private fun dispatch(effects: List<Effect>) {
        publish()
        for (effect in effects) when (effect) {
            is Effect.Render -> if (accepts(effect.epoch)) service?.requestRender(effect.epoch)
            Effect.RemoveRender -> service?.removeRender()
            Effect.EndService -> {
                scheduled.forEach { main.removeCallbacks(it) }; scheduled.clear()
                val old=service; service=null; old?.endSession()
                if (old == null && state.session > 0 && reportSession != state.session) {
                    val s=state
                    record(s.session,null,"session=${s.session}\nmode=${s.mode}\nselectedPattern=${s.settings.globalPattern}\neffectivePattern=${s.effectivePattern}\nrequestedTarget=${s.settings.refreshTarget}\nrequestEnabled=${s.settings.requestRefresh}\nreportedHz=${display()?.refreshRate ?: 0}\nactualSessionSeconds=${elapsedSessionSeconds()}\ncpuPosts=0\nreason=${s.reason}\nphysicalPresentation=UNMEASURED\n")
                }
            }
            is Effect.Timeout -> if (state.userEnabled && state.session == effect.session && state.mode is SessionMode.Timed)
                schedule(effect.deadline-SystemClock.uptimeMillis()) { dispatch(controller.timeout(effect.session,SystemClock.uptimeMillis())) }
            is Effect.Retry -> schedule(effect.delayMillis) { if (accepts(effect.epoch) && state.phase == Phase.RETRY_WAIT) reconcile(retry=true) }
            is Effect.Stable -> schedule(30_000) { controller.stable(effect.epoch); publish() }
        }
    }
}
