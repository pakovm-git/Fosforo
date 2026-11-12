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
    private var changingEffects=false
    private var changingScope=false
    private lateinit var controllerHints:ControllerHints
    private var showPatternTiles=false
    private lateinit var patternTiles:PatternPreview
    private var pendingCapture:StartRequest?=null
    private var returningFromSetup=false
    private var notificationRequestInFlight=false
    private var setupDialog:AlertDialog?=null
    private val recheckSetup=Runnable {
        if(started && returningFromSetup && !notificationRequestInFlight && !isFinishing){
            returningFromSetup=false
            update()
            if(!runtime.state.userEnabled && runtime.outputAccessProblem()!=null)showSetup()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); ExperimentService.prepareChannel(this); setContentView(R.layout.activity_main)
        returningFromSetup=savedInstanceState?.getBoolean("setupReturn")?:false
        notificationRequestInFlight=savedInstanceState?.getBoolean("notificationRequest")?:false
        ScreenActivity.configureInsets(findViewById(R.id.root),window)
        controllerHints=ControllerHints(this,findViewById(R.id.root),stop={if(runtime.state.userEnabled)runtime.stop() else begin()}) { finish() }
        UiSkin.controller(findViewById(R.id.main_action),"X");UiSkin.power(findViewById(R.id.main_action))
        showPatternTiles=savedInstanceState?.getBoolean("patternTiles")?:false
        val summary=findViewById<TextView>(R.id.pattern_summary)
        patternTiles=PatternPreview(this).apply{singleRow=true;compactHeight=summary.lineHeight;visibility=if(showPatternTiles)View.VISIBLE else View.GONE}
        (summary.parent as FrameLayout).apply{
            layoutParams=layoutParams.apply{height=summary.lineHeight}
            addView(patternTiles,FrameLayout.LayoutParams(-2,-1,android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL))
        }
        fun togglePattern(){showPatternTiles=!showPatternTiles;patternTiles.visibility=if(showPatternTiles)View.VISIBLE else View.GONE;summary.visibility=if(showPatternTiles)View.GONE else View.VISIBLE}
        findViewById<View>(R.id.home_pattern_row).setOnClickListener{togglePattern()};summary.setOnClickListener{togglePattern()};patternTiles.setOnClickListener{togglePattern()}
        summary.visibility=if(showPatternTiles)View.GONE else View.VISIBLE
        UiSkin.typography(findViewById(R.id.root))
        findViewById<View>(R.id.bfi_switch).tooltipText=getString(R.string.bfi_flash_hint)
        UiSkin.segmentTrack(findViewById(R.id.home_scope))
        for(id in listOf(R.id.home_scope_system,R.id.home_scope_apps))UiSkin.segment(findViewById(id))
        findViewById<Button>(R.id.settings_action).setOnClickListener { startActivity(Intent(this,AdvancedActivity::class.java)) }
        if (Build.VERSION.SDK_INT >= 28) findViewById<View>(R.id.home_logo).isAccessibilityHeading=true
        findViewById<Button>(R.id.crt_action).setOnClickListener { startActivity(Intent(this,ShaderActivity::class.java)) }
        findViewById<Button>(R.id.test_action).setOnClickListener { startActivity(Intent(this,TestEffectsActivity::class.java)) }
        findViewById<Button>(R.id.restart_crt).setOnClickListener {
            val state=runtime.state;runtime.stop();begin(runtime.prepareStart(state.mode,state.deadlineUptimeMillis))
        }
        for ((id,crt) in listOf(R.id.bfi_switch to false,R.id.crt_switch to true)) {
            findViewById<Switch>(id).setOnCheckedChangeListener { _,value ->
                if(!changingEffects){
                    val settings=runtime.repository.settings
                    if(settings!=null){
                        val restart=crt && runtime.state.userEnabled
                        val state=runtime.state
                        if(restart)runtime.stop()
                        val request=runtime.prepareStart(state.mode,state.deadlineUptimeMillis)
                        runtime.repository.save(if(crt)settings.copy(crtEnabled=value) else settings.copy(bfiEnabled=value)){success ->
                            if(success && restart && started)begin(request)
                        }
                    }
                }
            }
        }
        findViewById<Button>(R.id.main_action).setOnClickListener { if (runtime.state.userEnabled) runtime.stop() else begin() }
        findViewById<Button>(R.id.pattern_action).setOnClickListener { startActivity(Intent(this,PatternActivity::class.java)) }
        findViewById<Button>(R.id.apps_action).setOnClickListener { startActivity(Intent(this,AppsActivity::class.java)) }
        findViewById<RadioGroup>(R.id.home_scope).setOnCheckedChangeListener { _,id ->
            if(!changingScope) runtime.repository.settings?.let { settings ->
                val mode=if(id==R.id.home_scope_system) ActivationMode.ALL_APPS else ActivationMode.SELECTED_APPS
                if(settings.activationMode!=mode) runtime.repository.save(settings.copy(activationMode=mode)) { success ->
                    if(started && !success) Toast.makeText(this,R.string.save_failed,Toast.LENGTH_LONG).show()
                }
            }
        }

    }
    override fun onSaveInstanceState(outState:Bundle){
        outState.putBoolean("patternTiles",showPatternTiles)
        outState.putBoolean("setupReturn",returningFromSetup)
        outState.putBoolean("notificationRequest",notificationRequestInFlight)
        super.onSaveInstanceState(outState)
    }
    override fun onStart() {
        super.onStart(); started=true; unsubscribe=runtime.subscribe { update() }
        getSystemService(DisplayManager::class.java).registerDisplayListener(this,main)
    }
    override fun onResume(){super.onResume();main.postDelayed(recheckSetup,250)}
    override fun onStop() { started=false;main.removeCallbacks(recheckSetup);setupDialog?.dismiss(); unsubscribe?.invoke(); unsubscribe=null; getSystemService(DisplayManager::class.java).unregisterDisplayListener(this); super.onStop() }
    private fun update() {
        val state=runtime.state; val settings=runtime.repository.settings
        findViewById<TextView>(R.id.state_badge).setText(if(settings?.bfiEnabled==true && settings.crtEnabled)R.string.effects_both else if(settings?.crtEnabled==true)R.string.crt_effect else R.string.bfi_effect)
        changingEffects=true
        findViewById<Switch>(R.id.bfi_switch).apply{isChecked=settings?.bfiEnabled?:true;isEnabled=settings!=null}
        findViewById<Switch>(R.id.crt_switch).apply{isChecked=settings?.crtEnabled?:false;isEnabled=settings!=null && runtime.usesModernOutput}
        changingEffects=false
        findViewById<View>(R.id.restart_crt).visibility=if(runtime.crtNeedsRestart)View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.crt_summary).text=if(runtime.crtNeedsRestart)getString(R.string.crt_unlock_notice)
            else if(settings?.crtEnabled==true)dev.pako.bfi.effects.ShaderCatalog.choices(this).firstOrNull{it.id==settings.shaderId}?.label?:getString(R.string.shader_missing)
            else getString(R.string.crt_off)
        for(id in listOf(R.id.display_mode,R.id.black_summary,R.id.target_summary,R.id.request_summary,R.id.input_notice)) findViewById<View>(id).visibility=View.GONE
        UiSkin.activeStatus(findViewById(R.id.state_label),state.userEnabled)
        findViewById<Button>(R.id.test_action).isEnabled=!state.userEnabled && settings!=null && !runtime.repository.writing
        findViewById<TextView>(R.id.input_notice).setText(if(runtime.usesModernOutput)R.string.touch_passes else R.string.input_notice)
        val action=findViewById<Button>(R.id.main_action)
        action.setText(if (state.userEnabled) R.string.stop_effects else R.string.start_effects)
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
        reason.visibility=if ((routine || readiness==Reason.CAPTURE || (!state.userEnabled && state.reason==Reason.CAPTURE)) && runtime.repository.status is SettingsStatus.Ready) View.GONE else View.VISIBLE
        if (readiness != null) reason.text=UiText.reason(this,readiness)
        findViewById<TextView>(R.id.display_mode).text=runtime.display()?.let { getString(R.string.observed_mode,UiText.decimal(it.refreshRate.toDouble())) } ?: getString(R.string.unknown_rate)
        if (settings != null) {
            val runs=dev.pako.bfi.effects.BfiPattern.parse(settings.multiStepPattern)?.runs?:listOf(settings.globalPattern.visibleSlots,settings.globalPattern.blackSlots)
            findViewById<TextView>(R.id.pattern_summary).text=runs.chunked(2).joinToString(" - "){"V${it[0]}-B${it[1]}"}
            if(patternTiles.runs!=runs)patternTiles.runs=runs
            findViewById<TextView>(R.id.black_summary).text=getString(R.string.black_fraction,UiText.fraction(if (state.userEnabled) state.effectivePattern else settings.globalPattern))
            val effective=findViewById<TextView>(R.id.effective_summary)
            effective.visibility=if (state.userEnabled && state.effectivePattern != settings.globalPattern) View.VISIBLE else View.GONE
            effective.text=getString(R.string.effective_counts,UiText.integer(state.effectivePattern.visibleSlots),UiText.integer(state.effectivePattern.blackSlots))
            val target=when(val t=settings.refreshTarget) { RefreshTarget.Maximum -> getString(R.string.maximum_rate); is RefreshTarget.Numeric -> getString(R.string.rate_value,UiText.decimal(t.hz)) }
            findViewById<TextView>(R.id.target_summary).text=getString(R.string.target_label,target)
            val outcome=if (!state.userEnabled || state.reason == Reason.CONTROLS) (if (settings.requestRefresh) getString(R.string.on) else getString(R.string.off)) else UiText.outcome(this,state.requestOutcome)
            findViewById<TextView>(R.id.request_summary).text=getString(R.string.request_label,outcome)
            findViewById<Button>(R.id.pattern_action).setText(R.string.configure_bfi)
            changingScope=true
            findViewById<RadioGroup>(R.id.home_scope).check(if(settings.activationMode==ActivationMode.ALL_APPS)R.id.home_scope_system else R.id.home_scope_apps)
            changingScope=false
            findViewById<TextView>(R.id.home_scope_summary).setText(if(settings.activationMode==ActivationMode.ALL_APPS) R.string.system_wide else R.string.home_scope_apps)
        }
    }
    override fun dispatchKeyEvent(event:android.view.KeyEvent):Boolean = controllerHints.key(event) || super.dispatchKeyEvent(event)
    override fun dispatchTouchEvent(event:android.view.MotionEvent):Boolean { controllerHints.touch(event);return super.dispatchTouchEvent(event) }
    private fun begin(request:StartRequest=runtime.prepareStart()) {
        if(!runtime.accepts(request))return
        val repository=runtime.repository
        if (repository.status is SettingsStatus.Error) {
            AlertDialog.Builder(this).setOnKeyListener{_,_,event->controllerHints.stopKey(event)}.setTitle(R.string.setup_title).setMessage(R.string.settings_error)
                .setNeutralButton(R.string.reload_settings) { _,_ -> repository.reload() }
                .setPositiveButton(R.string.reset_settings) { _,_ ->
                    AlertDialog.Builder(this).setOnKeyListener{_,_,event->controllerHints.stopKey(event)}.setMessage(R.string.reset_confirm).setNegativeButton(R.string.cancel,null)
                        .setPositiveButton(R.string.reset) { _,_ -> repository.reset { success -> if (started && !success) Toast.makeText(this,R.string.save_failed,Toast.LENGTH_LONG).show() } }.show()
                }.setNegativeButton(R.string.cancel,null).show(); return
        }
        if(repository.settings==null)return
        if(runtime.readiness()==Reason.CAPTURE){
            if(pendingCapture==null){pendingCapture=request;startActivityForResult(getSystemService(android.media.projection.MediaProjectionManager::class.java).createScreenCaptureIntent(),200)}
            return
        }
        if (runtime.readiness() != null) { showSetup(); return }
        runtime.start(request)
    }
    private fun showSetup() {
        if(setupDialog?.isShowing==true)return
        val choices=mutableListOf<Int>()
        if (runtime.usesModernOutput && dev.pako.bfi.effects.EffectAccessibilityService.instance==null)choices.add(R.string.accessibility_permission)
        if (!runtime.usesModernOutput && !Settings.canDrawOverlays(this)) choices.add(R.string.overlay_permission)
        if (!ExperimentService.notificationsReady(this)) choices.add(R.string.notification_permission)
        if (runtime.repository.settings?.needsAppIdentity == true && !dev.pako.bfi.rules.ForegroundAppMonitor.hasAccess(this)) choices.add(R.string.usage_permission)
        if (runtime.readiness() == Reason.LOW_RATE) choices.add(R.string.display_settings)
        if (runtime.readiness() == Reason.EMPTY_SELECTED) choices.add(R.string.apps_title)
        choices.add(R.string.advanced_title)
        val dialog=AlertDialog.Builder(this).setOnKeyListener{_,_,event->controllerHints.stopKey(event)}.setTitle(R.string.setup_title).setItems(choices.map { getString(it) }.toTypedArray()) { _,index ->
            when (choices[index]) {
                R.string.accessibility_permission -> openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),recheck=true)
                R.string.overlay_permission -> openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")),recheck=true)
                R.string.notification_permission -> {
                    returningFromSetup=true
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notificationRequestInFlight=true
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),1)
                    }else openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName),recheck=true)
                }
                R.string.usage_permission -> openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                R.string.display_settings -> openSettings(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                R.string.apps_title -> startActivity(Intent(this,AppsActivity::class.java))
                else -> startActivity(Intent(this,AdvancedActivity::class.java))
            }
        }.setNegativeButton(R.string.cancel,null).create()
        setupDialog=dialog
        dialog.setOnDismissListener{if(setupDialog===dialog)setupDialog=null}
        dialog.show()
    }
    @Deprecated("Platform result callback retained for native Activity")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==200){val request=pendingCapture;pendingCapture=null;if(resultCode==RESULT_OK && data!=null && request!=null)runtime.start(request,data)}
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==1){notificationRequestInFlight=false;returningFromSetup=true;main.post(recheckSetup)}
    }
    private fun openSettings(intent: Intent,recheck:Boolean=false) {
        if(recheck)returningFromSetup=true
        try { startActivity(intent) } catch (_: RuntimeException) { returningFromSetup=false;Toast.makeText(this,R.string.no_settings,Toast.LENGTH_LONG).show() }
    }
    override fun onDisplayChanged(displayId: Int) { if (displayId == Display.DEFAULT_DISPLAY && started) update() }
    override fun onDisplayAdded(displayId: Int) { if (started) update() }
    override fun onDisplayRemoved(displayId: Int) { if (started) update() }
}
