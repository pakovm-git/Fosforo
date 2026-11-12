package dev.pako.bfi.effects

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.SystemClock
import android.view.WindowManager
import dev.pako.bfi.state.*
import java.nio.ByteBuffer

/** Retains one capture/output pair across app-scope pauses; the runtime owns enabled intent. */
@android.annotation.TargetApi(33)
class EffectsSession(private val context:Context,private val runtime:BfiRuntime,private val main:Handler) {
    private var engine:EffectRenderer?=null
    private var projection:MediaProjection?=null
    private var virtual:VirtualDisplay?=null
    private var reader:ImageReader?=null
    private var gate=ContinuousGate()
    private var epoch:Epoch?=null
    private var excluded=false
    private var haveFrame=false
    private var closed=false
    private var lastTimestamp=0L
    private var sourceW=0;private var sourceH=0
    private var lastAccepted=0L
    private val watchdog=object:Runnable {
        override fun run(){
            val current=epoch?:return
            if(closed || !runtime.accepts(current) || projection==null)return
            if(gate.stalled(haveFrame,SystemClock.elapsedRealtime()-lastAccepted)){fail("Capture did not produce or process a frame · CRT stopped safely");return}
            if(EffectAccessibilityService.instance==null){fail("Accessibility disconnected · effects are off");return}
            main.postDelayed(this,100)
        }
    }
    var error:String?=null;private set
    private val callback=object:MediaProjection.Callback(){override fun onStop(){if(!closed)fail("Android ended screen capture")}}
    fun sync() {
        val settings=runtime.state.settings
        Session.temporalEffect=if(settings.bfiEnabled)TemporalEffect.BFI else TemporalEffect.OFF
        Session.pattern=effectivePattern(settings,runtime.state.effectivePattern,runtime.foregroundPackage)
        val shader=settings.shaderFor(runtime.foregroundPackage)
        Session.shader=ShaderState(shader.id,shader.values)
        engine?.hz=runtime.display()?.refreshRate?.toDouble()?:60.0
    }
    fun render(next:Epoch) {
        if(closed || !runtime.accepts(next))return
        epoch=next;sync()
        lastAccepted=SystemClock.elapsedRealtime()
        main.removeCallbacks(watchdog);main.postDelayed(watchdog,100)
        try {
            if(engine==null)create()
            virtual?.surface=reader?.surface
            EffectAccessibilityService.instance?.resumeClock()
            val target=if(runtime.state.settings.requestRefresh)runtime.resolved(runtime.state.settings) else null
            EffectAccessibilityService.instance?.visible(haveFrame,target?.id?:0)?:error("Accessibility disconnected")
            if(haveFrame)reportFrame(next)
        }catch(error:Exception){fail(error.message?:"Effects unavailable")}
    }
    private fun reportFrame(next:Epoch) {
        val hz=runtime.display()?.refreshRate?.toDouble()?:0.0
        val target=if(runtime.state.settings.requestRefresh)runtime.resolved(runtime.state.settings) else null
        runtime.observed(next,hz,if(target==null)RequestOutcome.OFF else if(kotlin.math.abs(hz-target.hz)<=.1)RequestOutcome.APPLIED else RequestOutcome.OVERRIDDEN)
        runtime.firstFrame(next)
    }
    private fun create() {
        val overlay=checkNotNull(EffectAccessibilityService.instance){"Enable BFI accessibility"}
        val crt=runtime.state.settings.crtEnabled && !runtime.crtNeedsRestart
        gate=ContinuousGate();excluded=false;haveFrame=!crt;lastTimestamp=0L
        val renderer=EffectRenderer(context,externalFrames=crt,processed={main.post{processed()}},failure={message->main.post{fail(message)}}).apply {
            phase=if(crt)"crt" else "bfi";hz=runtime.display()?.refreshRate?.toDouble()?:60.0
        }
        engine=renderer
        if(crt) {
            val consent=checkNotNull(runtime.captureConsent){"Screen capture consent required"}
            runtime.captureConsent=null
            projection=context.getSystemService(MediaProjectionManager::class.java).getMediaProjection(Activity.RESULT_OK,consent)
            checkNotNull(projection){"Screen capture unavailable"}.registerCallback(callback,main)
            runtime.captureActive=true
            val bounds=overlay.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            sourceW=bounds.width();sourceH=bounds.height()
            val pixels=ByteBuffer.allocateDirect(sourceW*sourceH*4)
            reader=ImageReader.newInstance(sourceW,sourceH,PixelFormat.RGBA_8888,3).apply {
                setOnImageAvailableListener({ input ->
                    if(closed)return@setOnImageAvailableListener
                    try { input.acquireLatestImage()?.use { image ->
                        if(!excluded || image.timestamp<=lastTimestamp)return@use
                        lastTimestamp=image.timestamp
                        if(gate.observe(image.timestamp,false)) {
                            lastAccepted=SystemClock.elapsedRealtime()
                            val plane=image.planes[0]
                            renderer.submitFrame(CapturedPixels(plane.buffer,image.width,image.height,plane.rowStride,plane.pixelStride).bottomUp(pixels),image.width,image.height)
                        }
                    } }catch(error:Exception){fail("Capture stopped: ${error.message}")}
                },main)
            }
        }
        overlay.show(renderer) { view ->
            if(crt){CaptureExclusion.apply(view);excluded=true}
        }
        if(crt)virtual=projection!!.createVirtualDisplay("BFI CRT capture",sourceW,sourceH,context.resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,main)
    }
    private fun processed() {
        if(closed || !gate.processed())return
        haveFrame=true
        val current=epoch?:return
        if(runtime.accepts(current) && runtime.state.phase==Phase.STARTING) {
            try { EffectAccessibilityService.instance?.visible(true,if(runtime.state.settings.requestRefresh)runtime.resolved(runtime.state.settings)?.id?:0 else 0)
                ?:error("Accessibility disconnected"); reportFrame(current) }
            catch(error:Exception){fail(error.message?:"Output unavailable")}
        }
    }
    fun pause() {
        epoch=null;main.removeCallbacks(watchdog)
        virtual?.surface=null
        EffectAccessibilityService.instance?.visible(false);EffectAccessibilityService.instance?.pauseClock()
    }
    fun locked() {
        if(projection==null)return
        releaseOutput()
        runtime.captureLostForLock()
    }
    fun displayChanged(rotationChanged:Boolean) {
        if(rotationChanged && projection!=null){fail("Screen rotated: start CRT again");return}
        sync()
    }
    private fun fail(message:String) {
        if(closed)return
        error=message;android.util.Log.e("BfiEffects",message)
        runtime.stop(Reason.CAPTURE)
    }
    private fun releaseOutput() {
        epoch=null;main.removeCallbacks(watchdog);gate.stop();reader?.setOnImageAvailableListener(null,null)
        virtual?.release();virtual=null;reader?.close();reader=null
        val old=projection;projection=null;old?.unregisterCallback(callback);old?.stop()
        runtime.captureActive=false
        EffectAccessibilityService.instance?.remove();engine=null;haveFrame=false;excluded=false
    }
    fun close(){if(closed)return;closed=true;releaseOutput()}
}
