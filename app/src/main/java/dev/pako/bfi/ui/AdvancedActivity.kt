package dev.pako.bfi.ui

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import dev.pako.bfi.*
import dev.pako.bfi.display.RefreshRequestResolver
import dev.pako.bfi.model.*
import dev.pako.bfi.rules.ForegroundAppMonitor
import dev.pako.bfi.state.*
import kotlinx.coroutines.launch

class AdvancedActivity : ScreenActivity(),DisplayManager.DisplayListener {
    private var initialized=false
    private var changing=false
    private var restored: Bundle?=null
    private var baseline=BfiSettings()
    private lateinit var target: Spinner
    private var targets=emptyList<RefreshTarget>()
    private lateinit var request: CheckBox
    private lateinit var force: CheckBox
    private lateinit var diagnostics: CheckBox
    private lateinit var duration: EditText
    private lateinit var reported: TextView
    private lateinit var countdown: TextView
    private lateinit var counters: TextView
    private lateinit var permissionStatus: TextView
    private lateinit var test: Button
    private lateinit var usageButton: Button
    private var pendingReport: String?=null
    private val controls=mutableListOf<View>()
    private val tick=object : Runnable {
        override fun run() {
            if (!screenStarted) return
            updateLive()
            if (runtime.state.userEnabled && (runtime.state.mode is SessionMode.Timed || repository.settings?.diagnosticsEnabled == true)) runtime.main.postDelayed(this,1000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); restored=savedInstanceState; pendingReport=savedInstanceState?.getString("pendingReport"); title(R.string.advanced_title)
        val panels=boundedCards(firstWideWeight=.5f,firstNarrowWeight=.5f)
        val display=column(panels.preview); section(R.string.display_section,display)
        text(R.string.refresh_target,display,true)
        target=Spinner(this).apply { minimumHeight=dp(48); contentDescription=getString(R.string.refresh_target) }; display.addView(target,LinearLayout.LayoutParams(-1,-2))
        request=check(R.string.request_refresh,display); text(R.string.refresh_help,display,true); reported=text(R.string.unknown_rate,display,true)
        val recovery=column(panels.preview); section(R.string.recovery_section,recovery)
        force=check(R.string.always_force,recovery); text(R.string.recovery_help,recovery,true)
        val tests=column(panels.controls); section(R.string.tests_section,tests)
        val label=text(R.string.duration_title,tests,true); duration=numberField(tests); label.labelFor=duration.id
        text(R.string.duration_help,tests,true); test=button(R.string.run_test,tests) { runTest() }
        countdown=text(R.string.until_off,tests,true)
        val diag=column(panels.controls); section(R.string.diagnostics_section,diag)
        diagnostics=check(R.string.enable_diagnostics,diag); counters=text(R.string.diagnostics_none,diag,true)
        text(R.string.measurement_notice,diag,true)
        button(R.string.copy_report,diag) {
            val report=reportText() ?: return@button
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(getString(R.string.report_title),report))
        }
        button(R.string.export_report,diag) {
            val report=reportText() ?: return@button; pendingReport=report
            try { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"bfi-report.txt"),74) }
            catch (_: RuntimeException) { Toast.makeText(this,R.string.report_failed,Toast.LENGTH_LONG).show() }
        }
        val permissions=column(panels.controls); section(R.string.permissions_section,permissions)
        permissionStatus=text(R.string.loading,permissions,true)
        button(R.string.overlay_permission,permissions) { openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName"))) }
        button(R.string.notification_permission,permissions) {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),1)
            else openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))
        }
        usageButton=button(R.string.usage_permission,permissions) { openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        button(R.string.display_settings,permissions) { openSettings(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
        text(R.string.permission_details,permissions,true); text(R.string.input_notice,permissions,true)
        controls.addAll(listOf(target,request,force,diagnostics,duration))
        initialize()
        for (box in listOf(request,force,diagnostics)) box.setOnCheckedChangeListener { _,_ -> if (initialized && !changing) saveDraft() }
        target.onItemSelectedListener=object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,id: Long) { if (initialized && !changing) saveDraft() }
            override fun onNothingSelected(parent: AdapterView<*>?)=Unit
        }
        duration.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int)=Unit
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int)=Unit
            override fun afterTextChanged(s: android.text.Editable?) { if (initialized && !changing) saveDraft() }
        })
    }
    private fun initialize() {
        if (initialized) return
        val s=repository.settings ?: return
        baseline=s; changing=true
        request.isChecked=restored?.getBoolean("request") ?: s.requestRefresh; force.isChecked=restored?.getBoolean("force") ?: s.alwaysForce
        diagnostics.isChecked=restored?.getBoolean("diagnostics") ?: s.diagnosticsEnabled
        duration.setText(restored?.getString("duration") ?: UiText.integer(s.lastTestSeconds))
        val selected=restored?.getString("target")?.let { parseTarget(it) } ?: s.refreshTarget
        populateTargets(selected); initialized=true; changing=false
        if (restored?.getBoolean("durationError") == true) duration.error=getString(R.string.duration_error,TrialConfig.MIN_SECONDS,TrialConfig.MAX_SECONDS)
    }
    private fun parseTarget(value: String): RefreshTarget = if (value == "maximum") RefreshTarget.Maximum else runCatching { RefreshTarget.Numeric(value.toDouble()) }.getOrDefault(RefreshTarget.Maximum)
    private fun targetString(t: RefreshTarget) = when(t) { RefreshTarget.Maximum -> "maximum"; is RefreshTarget.Numeric -> t.hz.toString() }
    private fun selectedTarget()=targets.getOrNull(target.selectedItemPosition) ?: baseline.refreshTarget
    private fun populateTargets(selected: RefreshTarget) {
        val previousChanging=changing; changing=true
        val display=runtime.display()
        val eligible=display?.mode?.let { RefreshRequestResolver.eligible(runtime.modes(),it.physicalWidth,it.physicalHeight) } ?: emptyList()
        val choices=mutableListOf<RefreshTarget>(RefreshTarget.Maximum)
        choices.addAll(eligible.map { it.hz }.distinct().sortedDescending().map { RefreshTarget.Numeric(it) })
        if (selected !in choices) choices.add(selected)
        targets=choices.toList()
        val labels=targets.map { t -> when(t) {
            RefreshTarget.Maximum -> getString(R.string.maximum_rate)
            is RefreshTarget.Numeric -> if (eligible.none { kotlin.math.abs(it.hz-t.hz) <= 0.1 }) getString(R.string.target_unavailable,UiText.decimal(t.hz)) else getString(R.string.rate_value,UiText.decimal(t.hz))
        } }
        target.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_item,labels).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        target.setSelection(targets.indexOf(selected).coerceAtLeast(0)); changing=previousChanging
    }
    private fun draft(): BfiSettings? {
        val seconds=TrialConfig.parseSeconds(duration.text.toString()) ?: return null
        val s=repository.settings ?: return null
        return s.copy(refreshTarget=selectedTarget(),requestRefresh=request.isChecked,alwaysForce=force.isChecked,diagnosticsEnabled=diagnostics.isChecked,lastTestSeconds=seconds)
    }
    override fun saveDraft() {
        if (!initialized || changing) return
        // Invalid transient duration text must not prevent saving unrelated switches.
        val s=repository.settings ?: return
        val seconds=TrialConfig.parseSeconds(duration.text.toString())
        duration.error=if (seconds == null) getString(R.string.duration_error,TrialConfig.MIN_SECONDS,TrialConfig.MAX_SECONDS) else null
        save(s.copy(refreshTarget=selectedTarget(),requestRefresh=request.isChecked,alwaysForce=force.isChecked,
            diagnosticsEnabled=diagnostics.isChecked,lastTestSeconds=seconds ?: s.lastTestSeconds))
    }
    override fun restoreDefaults() {
        val s=repository.settings ?: return; val defaults=BfiSettings()
        changing=true; request.isChecked=defaults.requestRefresh; force.isChecked=defaults.alwaysForce
        diagnostics.isChecked=defaults.diagnosticsEnabled
        duration.setText(UiText.integer(defaults.lastTestSeconds)); populateTargets(defaults.refreshTarget); changing=false; duration.error=null
        saveDraft()
    }
    private fun runTest() {
        if (runtime.state.userEnabled || repository.writing) return
        val s=draft() ?: run { duration.error=getString(R.string.duration_error,TrialConfig.MIN_SECONDS,TrialConfig.MAX_SECONDS); return }
        if (runtime.readiness() != null) { startActivity(Intent(this,MainActivity::class.java)); return }
        runtime.start(SessionMode.Timed(s.lastTestSeconds))
    }
    override fun refreshScreen() {
        initialize(); val editable=initialized && repository.settings != null
        controls.forEach { it.isEnabled=editable }; test.isEnabled=editable && !runtime.state.userEnabled && !repository.writing && TrialConfig.parseSeconds(duration.text.toString()) != null
        updateLive(); runtime.main.removeCallbacks(tick)
        if (screenStarted && runtime.state.userEnabled && (runtime.state.mode is SessionMode.Timed || repository.settings?.diagnosticsEnabled == true)) runtime.main.postDelayed(tick,1000)
    }
    private fun updateLive() {
        val s=runtime.state; val hz=runtime.display()?.refreshRate?.toDouble()
        usageButton.visibility=if (repository.settings?.needsAppIdentity == true) View.VISIBLE else View.GONE
        reported.text=getString(R.string.refresh_status,hz?.let { UiText.decimal(it) } ?: getString(R.string.unknown_rate),UiText.outcome(this,s.requestOutcome))
        countdown.text=if (s.userEnabled && s.mode is SessionMode.Timed) getString(R.string.remaining_seconds,UiText.integer(((s.deadlineUptimeMillis ?: 0)-SystemClock.uptimeMillis()+999).coerceAtLeast(0)/1000)) else UiText.phase(this,s.phase)
        val summary=if (repository.settings?.diagnosticsEnabled == true) runtime.liveSummary() else null
        counters.visibility=if (repository.settings?.diagnosticsEnabled == true) View.VISIBLE else View.GONE
        counters.text=summary?.let { formatSummary(it) } ?: getString(R.string.diagnostics_none)
        permissionStatus.text=getString(R.string.permission_summary,
            getString(if (Settings.canDrawOverlays(this)) R.string.overlay_ready else R.string.overlay_missing),
            getString(if (ExperimentService.notificationsReady(this)) R.string.notifications_ready else R.string.notifications_missing),
            getString(if (repository.settings?.needsAppIdentity != true) R.string.usage_not_required else if (ForegroundAppMonitor.hasAccess(this)) R.string.usage_ready else R.string.usage_missing))
    }
    private fun formatSummary(summary: TrialSummary)=getString(R.string.summary,UiText.integer(summary.callbacks),UiText.integer(summary.posts),UiText.integer(summary.skipped),UiText.integer(summary.late),
        if (summary.recentIntervalSamples >= 16 && summary.recentCallbackCadenceHz > 0) getString(R.string.rate_value,UiText.decimal(summary.recentCallbackCadenceHz)) else getString(R.string.unknown_rate))
    private fun reportText(): String? {
        val report=runtime.reportSnapshot()
        if (report == null) Toast.makeText(this,R.string.report_unavailable,Toast.LENGTH_LONG).show()
        return report
    }
    private fun openSettings(intent: Intent) { try { startActivity(intent) } catch (_: RuntimeException) { Toast.makeText(this,R.string.no_settings,Toast.LENGTH_LONG).show() } }
    override fun onStart() {
        super.onStart(); getSystemService(DisplayManager::class.java).registerDisplayListener(this,runtime.main)
        if (runtime.lastReport == null) {
            val generation=callbackGeneration
            repository.scope.launch {
                val report=runtime.readSavedReport()
                runtime.main.post { if (screenStarted && generation == callbackGeneration && report != null) runtime.loadReport(report) }
            }
        }
    }
    override fun onStop() { runtime.main.removeCallbacks(tick); getSystemService(DisplayManager::class.java).unregisterDisplayListener(this); super.onStop() }
    override fun onDisplayChanged(displayId: Int) { if (displayId == Display.DEFAULT_DISPLAY && initialized) { populateTargets(selectedTarget()); updateLive() } }
    override fun onDisplayAdded(displayId: Int) { onDisplayChanged(displayId) }
    override fun onDisplayRemoved(displayId: Int) { onDisplayChanged(displayId) }
    @Deprecated("Platform document-picker result bridge")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (requestCode != 74 || resultCode != RESULT_OK) return
        val uri=data?.data ?: return; val report=pendingReport ?: return; pendingReport=null
        repository.scope.launch {
            val success=try { contentResolver.openOutputStream(uri)?.use { it.write(report.toByteArray()) } != null } catch (_: Exception) { false }
            runtime.main.post { if (screenStarted) Toast.makeText(this@AdvancedActivity,if (success) R.string.report_written else R.string.report_failed,Toast.LENGTH_LONG).show() }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("request",request.isChecked); outState.putBoolean("force",force.isChecked)
        outState.putBoolean("diagnostics",diagnostics.isChecked); outState.putString("duration",duration.text.toString()); outState.putString("target",targetString(selectedTarget()))
        outState.putString("pendingReport",pendingReport); super.onSaveInstanceState(outState)
        outState.putBoolean("durationError",duration.error != null)
    }
}
