package dev.pako.bfi.ui

import android.app.Activity
import android.os.*
import android.content.Intent
import android.opengl.GLSurfaceView
import android.view.*
import android.widget.*
import dev.pako.bfi.*
import dev.pako.bfi.effects.*

/** Local synthetic test through the real shader pipeline; global sessions are untouched. */
class TestEffectsActivity:Activity(){
    private val runtime get()=(application as BfiApplication).runtime
    private lateinit var hints:ControllerHints
    private lateinit var source:GLSurfaceView
    private lateinit var renderer:ShaderPreviewRenderer
    private lateinit var action:Button
    private val handler=Handler(Looper.getMainLooper())
    private var enabled=true
    private var unsubscribe:(()->Unit)?=null
    private val timeout=Runnable{enabled=false;update()}
    private fun toggle(){enabled=!enabled;handler.removeCallbacks(timeout);if(enabled)handler.postDelayed(timeout,15000);update()}
    private fun update(){
        val settings=runtime.repository.settings?:return
        val mode=intent.getStringExtra("effect")?:"both"
        val shader=settings.shaderFor(intent.getStringExtra(PatternActivity.EXTRA_PACKAGE))
        renderer.state=ShaderState(shader.id,shader.values)
        val live=runtime.state.userEnabled
        renderer.effectsEnabled=enabled
        renderer.useShader=mode!="bfi"&&(mode=="crt"||settings.crtEnabled)&&!(live&&runtime.captureActive&&settings.crtEnabled&&!runtime.crtNeedsRestart)
        renderer.blackPattern=if(!live&&mode!="crt"&&(mode=="bfi"||settings.bfiEnabled))BfiPattern.parse(settings.multiStepPattern)?:BfiPattern.legacy(settings.globalPattern.visibleSlots,settings.globalPattern.blackSlots) else null
        renderer.hz=runtime.display()?.refreshRate?.toDouble()?:120.0
        action.setText(if(enabled)R.string.stop_effects else R.string.start_effects)
        action.setBackgroundResource(if(enabled)R.drawable.stop_action else R.drawable.primary_action)
        action.setTextColor(getColor(if(enabled)R.color.bfi_on_stop else R.color.bfi_on_action));hints.refresh()
    }
    override fun onCreate(state:Bundle?){
        super.onCreate(state);enabled=state?.getBoolean("previewEnabled")?:true
        val root=LinearLayout(this).apply{id=R.id.root;orientation=LinearLayout.VERTICAL}
        val header=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(16),dp(10),dp(16),dp(10));setBackgroundResource(R.drawable.group_header);tag="glass-bar"}
        val back=Button(ContextThemeWrapper(this,R.style.HeaderAction),null,0).apply{UiSkin.back(this);setOnClickListener{finish()}}
        header.addView(back,LinearLayout.LayoutParams(-2,dp(48)))
        header.addView(TextView(this).apply{setText(R.string.test_effects);tag="phosphor-brand";textSize=30f;setTextColor(getColor(R.color.bfi_text_primary))},LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=dp(12)})
        action=Button(ContextThemeWrapper(this,R.style.PrimaryAction),null,0).apply{UiSkin.controller(this,"X");UiSkin.power(this);setOnClickListener{toggle()}}
        header.addView(action,LinearLayout.LayoutParams(-2,dp(48)));root.addView(header)
        renderer=ShaderPreviewRenderer(applicationContext,ShaderState(runtime.repository.settings?.shaderId?:"lottes",runtime.repository.settings?.shaderValues.orEmpty())){message->runOnUiThread{Toast.makeText(this,message,Toast.LENGTH_LONG).show()}}
        source=GLSurfaceView(this).apply{setEGLContextClientVersion(3);setRenderer(renderer);contentDescription=getString(R.string.test_animation)}
        root.addView(source,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
        ScreenActivity.configureInsets(root,window);hints=ControllerHints(this,root,stop={toggle()}){finish()};UiSkin.typography(root);update()
    }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    override fun onResume(){super.onResume();source.onResume();unsubscribe=runtime.subscribe{update()};if(enabled)handler.postDelayed(timeout,15000)}
    override fun onPause(){handler.removeCallbacks(timeout);unsubscribe?.invoke();unsubscribe=null;source.onPause();super.onPause()}
    override fun onSaveInstanceState(out:Bundle){out.putBoolean("previewEnabled",enabled);super.onSaveInstanceState(out)}
    override fun dispatchKeyEvent(event:KeyEvent)=hints.key(event)||super.dispatchKeyEvent(event)
    override fun dispatchTouchEvent(event:MotionEvent):Boolean{hints.touch(event);return super.dispatchTouchEvent(event)}
}
