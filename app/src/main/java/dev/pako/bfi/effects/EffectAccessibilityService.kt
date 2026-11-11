package dev.pako.bfi.effects

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import dev.pako.bfi.BfiApplication
import dev.pako.bfi.state.Reason

/** Trusted output window only: no node inspection, gesture injection or input interception. */
@android.annotation.TargetApi(33)
class EffectAccessibilityService : AccessibilityService() {
    private var output:OverlayTextureView?=null
    private var black:NativeBfiView?=null
    private var root:FrameLayout?=null
    private var layout:WindowManager.LayoutParams?=null
    private val runtime get()=(application as BfiApplication).runtime
    override fun onServiceConnected(){instance=this}
    override fun onAccessibilityEvent(event:AccessibilityEvent?)=Unit
    override fun onInterrupt(){runtime.stop(Reason.ACCESSIBILITY)}
    override fun onUnbind(intent:Intent?):Boolean { disconnect();return super.onUnbind(intent) }
    override fun onDestroy(){disconnect();super.onDestroy()}
    private fun disconnect(){runtime.stop(Reason.ACCESSIBILITY);remove();if(instance===this)instance=null}
    fun show(engine:EffectRenderer,ready:(OverlayTextureView)->Unit) {
        remove()
        engine.bfiInOutput=false
        val texture=OverlayTextureView(this,engine,ready)
        val blackout=NativeBfiView(this,texture)
        val window=FrameLayout(this).apply {
            isClickable=false;isFocusable=false
            addView(texture,FrameLayout.LayoutParams(-1,-1));addView(blackout,FrameLayout.LayoutParams(-1,-1))
        }
        val params=WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            alpha=0f;gravity=Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            setFitInsetsTypes(0)
        }
        output=texture;black=blackout;root=window;layout=params
        try { getSystemService(WindowManager::class.java).addView(window,params) }
        catch(error:Exception){remove();throw error}
    }
    fun visible(value:Boolean,preferredMode:Int=0) {
        val window=root?:return;val params=layout?:return
        val alpha=if(value)1f else 0f
        if(params.alpha!=alpha || params.preferredDisplayModeId!=preferredMode){
            params.alpha=alpha;params.preferredDisplayModeId=preferredMode
            getSystemService(WindowManager::class.java).updateViewLayout(window,params)
        }
    }
    internal fun outputForInspection()=output
    internal fun blackLayerForInspection()=black
    internal fun windowAlphaForInspection()=layout?.alpha
    internal fun windowAttachedForInspection()=root?.isAttachedToWindow==true
    fun pauseClock(){black?.close()}
    fun resumeClock(){black?.resume()}
    fun remove() {
        black?.close();output?.close()
        root?.let { runCatching { getSystemService(WindowManager::class.java).removeViewImmediate(it) } }
        black=null;output=null;root=null;layout=null
    }
    companion object { var instance:EffectAccessibilityService?=null;private set }
}
