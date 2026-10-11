package dev.pako.bfi

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Display
import android.view.View
import android.widget.*
import dev.pako.bfi.model.*
import dev.pako.bfi.settings.*
import dev.pako.bfi.state.*
import dev.pako.bfi.ui.*

class MainActivity : Activity(),DisplayManager.DisplayListener {
    private val runtime get() = (application as BfiApplication).runtime
    private val main=Handler(Looper.getMainLooper())
    private var unsubscribe: (() -> Unit)? = null
    private var started=false
    private var updatingResume=false
    private lateinit var presentation: LocalBfiPresentation
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); ExperimentService.prepareChannel(this); setContentView(R.layout.activity_main)
        ScreenActivity.configureInsets(findViewById(R.id.root),window)
        if (Build.VERSION.SDK_INT >= 28) findViewById<View>(R.id.home_logo).isAccessibilityHeading=true
        presentation=LocalBfiPresentation(this,runtime)
        findViewById<Button>(R.id.main_action).setOnClickListener { if (runtime.state.userEnabled) runtime.stop() else begin() }
        findViewById<Button>(R.id.pattern_action).setOnClickListener { startActivity(Intent(this,PatternActivity::class.java)) }
        findViewById<Button>(R.id.apps_action).setOnClickListener { startActivity(Intent(this,AppsActivity::class.java)) }
        findViewById<CheckBox>(R.id.resume_unlock_action).apply {
            tooltipText=getString(R.string.sleep_help)
            setOnCheckedChangeListener { _,checked ->
                if (!updatingResume) {
                    val settings=runtime.repository.settings
                    if (settings != null && settings.resumeAfterUnlock != checked) {
                        runtime.repository.save(settings.copy(resumeAfterUnlock=checked)) { success ->
                            if (started && !isDestroyed && !success) Toast.makeText(this@MainActivity,R.string.save_failed,Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
    override fun onStart() {
        super.onStart(); presentation.start(); started=true; unsubscribe=runtime.subscribe { update() }
        getSystemService(DisplayManager::class.java).registerDisplayListener(this,main)
    }
    override fun onStop() { presentation.stop(); started=false; unsubscribe?.invoke(); unsubscribe=null; getSystemService(DisplayManager::class.java).unregisterDisplayListener(this); super.onStop() }
    private fun update() {
        val state=runtime.state; val settings=runtime.repository.settings
        updatingResume=true
        findViewById<CheckBox>(R.id.resume_unlock_action).apply {
            isEnabled=settings != null
            isChecked=settings?.resumeAfterUnlock ?: false
        }
        updatingResume=false
        val action=findViewById<Button>(R.id.main_action)
        action.setText(if (state.userEnabled) R.string.turn_off else R.string.turn_on)
        action.setBackgroundResource(if (state.userEnabled) R.drawable.stop_action else R.drawable.primary_action)
        action.setTextColor(getColorStateList(if (state.userEnabled) R.color.stop_action_text else R.color.primary_action_text))
        action.isEnabled=state.userEnabled || (!runtime.repository.writing && runtime.repository.status !is SettingsStatus.Loading)
        findViewById<TextView>(R.id.state_label).text=if (state.userEnabled && state.reason == Reason.CONTROLS) getString(R.string.local_on) else UiText.phase(this,state.phase)
        findViewById<TextView>(R.id.status_reason).text=when {
            runtime.repository.status is SettingsStatus.Loading -> getString(R.string.loading)
            runtime.repository.status is SettingsStatus.Error -> getString(R.string.settings_error)
            state.phase == Phase.RUNNING -> getString(R.string.reason_active)
            state.phase == Phase.STARTING -> getString(R.string.reason_preparing)
            !state.userEnabled && state.reason == Reason.READY -> UiText.reason(this,runtime.readiness() ?: Reason.READY)
            else -> UiText.reason(this,state.reason)
        }
        val reason=findViewById<TextView>(R.id.status_reason)
        val readiness=if (!state.userEnabled && state.reason in setOf(Reason.READY,Reason.MANUAL)) runtime.readiness() else null
        val routine=state.phase == Phase.RUNNING || state.reason == Reason.CONTROLS ||
            (!state.userEnabled && state.reason in setOf(Reason.READY,Reason.MANUAL) && readiness == null)
        reason.visibility=if (routine && runtime.repository.status is SettingsStatus.Ready) View.GONE else View.VISIBLE
        if (readiness != null) reason.text=UiText.reason(this,readiness)
        findViewById<TextView>(R.id.display_mode).text=runtime.display()?.let { getString(R.string.observed_mode,UiText.decimal(it.refreshRate.toDouble())) } ?: getString(R.string.unknown_rate)
        if (settings != null) {
            findViewById<MotionPreview>(R.id.motion_preview).apply {
                pattern=(runtime.repository.committedSettings ?: settings).globalPattern
                reportedHz=runtime.display()?.refreshRate?.toDouble() ?: 60.0
            }
            findViewById<TextView>(R.id.pattern_summary).text=UiText.pattern(this,settings.globalPattern)
            findViewById<TextView>(R.id.black_summary).text=getString(R.string.black_fraction,UiText.fraction(if (state.userEnabled) state.effectivePattern else settings.globalPattern))
            val effective=findViewById<TextView>(R.id.effective_summary)
            effective.visibility=if (state.userEnabled && state.effectivePattern != settings.globalPattern) View.VISIBLE else View.GONE
            effective.text=getString(R.string.effective_counts,UiText.integer(state.effectivePattern.visibleSlots),UiText.integer(state.effectivePattern.blackSlots))
            val target=when(val t=settings.refreshTarget) { RefreshTarget.Maximum -> getString(R.string.maximum_rate); is RefreshTarget.Numeric -> getString(R.string.rate_value,UiText.decimal(t.hz)) }
            findViewById<TextView>(R.id.target_summary).text=getString(R.string.target_label,target)
            val outcome=if (!state.userEnabled || state.reason == Reason.CONTROLS) (if (settings.requestRefresh) getString(R.string.on) else getString(R.string.off)) else UiText.outcome(this,state.requestOutcome)
            findViewById<TextView>(R.id.request_summary).text=getString(R.string.request_label,outcome)
            findViewById<Button>(R.id.pattern_action).text=getString(R.string.home_pattern_action)
            findViewById<Button>(R.id.apps_action).text=getString(R.string.home_apps_action,getString(if (settings.activationMode == ActivationMode.ALL_APPS) R.string.system_wide else R.string.home_scope_apps))
        }
    }
    private fun begin() {
        val repository=runtime.repository
        if (repository.status is SettingsStatus.Error) {
            AlertDialog.Builder(this).setTitle(R.string.setup_title).setMessage(R.string.settings_error)
                .setNeutralButton(R.string.reload_settings) { _,_ -> repository.reload() }
                .setPositiveButton(R.string.reset_settings) { _,_ ->
                    AlertDialog.Builder(this).setMessage(R.string.reset_confirm).setNegativeButton(R.string.cancel,null)
                        .setPositiveButton(R.string.reset) { _,_ -> repository.reset { success -> if (started && !success) Toast.makeText(this,R.string.save_failed,Toast.LENGTH_LONG).show() } }.show()
                }.setNegativeButton(R.string.cancel,null).show(); return
        }
        val settings=repository.settings ?: return
        if (settings.acceptedNoticeVersion < 1) {
            AlertDialog.Builder(this).setTitle(R.string.notice_title).setMessage(R.string.first_notice)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.acknowledge) { _,_ ->
                    if (!runtime.state.userEnabled) repository.save(settings.copy(acceptedNoticeVersion=1)) { success -> if (started && !success) Toast.makeText(this,R.string.save_failed,Toast.LENGTH_LONG).show() }
                }.show(); return
        }
        if (runtime.readiness() != null) { showSetup(); return }
        runtime.start()
    }
    private fun showSetup() {
        val choices=mutableListOf<Int>()
        if (!Settings.canDrawOverlays(this)) choices.add(R.string.overlay_permission)
        if (!ExperimentService.notificationsReady(this)) choices.add(R.string.notification_permission)
        if (runtime.repository.settings?.needsAppIdentity == true && !dev.pako.bfi.rules.ForegroundAppMonitor.hasAccess(this)) choices.add(R.string.usage_permission)
        if (runtime.readiness() == Reason.LOW_RATE) choices.add(R.string.display_settings)
        if (runtime.readiness() == Reason.EMPTY_SELECTED) choices.add(R.string.apps_title)
        choices.add(R.string.advanced_title)
        AlertDialog.Builder(this).setTitle(R.string.setup_title).setItems(choices.map { getString(it) }.toTypedArray()) { _,index ->
            when (choices[index]) {
                R.string.overlay_permission -> openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
                R.string.notification_permission -> if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),1)
                    else openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))
                R.string.usage_permission -> openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                R.string.display_settings -> openSettings(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                R.string.apps_title -> startActivity(Intent(this,AppsActivity::class.java))
                else -> startActivity(Intent(this,AdvancedActivity::class.java))
            }
        }.setNegativeButton(R.string.cancel,null).show()
    }
    private fun openSettings(intent: Intent) { try { startActivity(intent) } catch (_: RuntimeException) { Toast.makeText(this,R.string.no_settings,Toast.LENGTH_LONG).show() } }
    override fun onDisplayChanged(displayId: Int) { if (displayId == Display.DEFAULT_DISPLAY && started) update() }
    override fun onDisplayAdded(displayId: Int) { if (started) update() }
    override fun onDisplayRemoved(displayId: Int) { if (started) update() }
}
