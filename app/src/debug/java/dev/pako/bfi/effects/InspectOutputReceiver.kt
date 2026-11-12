package dev.pako.bfi.effects

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.io.File

/** Shell-only debug evidence of our TextureView, since skipped layers are absent from screenshots. */
class InspectOutputReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val runtime=(context.applicationContext as dev.pako.bfi.BfiApplication).runtime
        if(intent.getBooleanExtra("check_library",false)) {runtime.stop();ShaderLibrary.scan(context);return}
        intent.getStringExtra("check_shader")?.let { id ->
            val pending=goAsync()
            Thread {
                val result=try {ShaderProbe(context).use{it.check(ShaderCatalog.load(context,id))};"PASS: $id"}
                    catch(error:Exception){"FAIL: $id: ${error.message}"}
                File(context.filesDir,"shader-check.txt").writeText(result)
                pending.finish()
            }.start()
            return
        }
        if(intent.getBooleanExtra("state",false)) {
            val s=runtime.state
            File(context.filesDir,"effects-state.txt").writeText("libraryBusy=${ShaderLibrary.status.busy}\nlibraryStatus=${ShaderLibrary.status.message.replace('\n',' ')}\naccessibility=${EffectAccessibilityService.instance!=null}\nnotifications=${dev.pako.bfi.ExperimentService.notificationsReady(context)}\nreadiness=${runtime.readiness()}\nenabled=${s.userEnabled}\nphase=${s.phase}\nreason=${s.reason}\nsession=${s.session}\ncapture=${runtime.captureActive}\ncrtNeedsRestart=${runtime.crtNeedsRestart}\nbfi=${s.settings.bfiEnabled}\ncrt=${s.settings.crtEnabled}\nshader=${s.settings.shaderId}\nwindowAlpha=${EffectAccessibilityService.instance?.windowAlphaForInspection()}\nwindowAttached=${EffectAccessibilityService.instance?.windowAttachedForInspection()}\npattern=${Session.pattern.encoded}\n")
            return
        }
        val output=EffectAccessibilityService.instance?.outputForInspection()?:return
        when(intent.getStringExtra("cadence")) {
            "start"->{output.cadence.start();return}
            "stop"->{File(context.filesDir,"output-cadence.csv").writeText(output.cadence.stop());return}
        }
        val pending=goAsync()
        val main=Handler(Looper.getMainLooper())
        val folder=File(context.filesDir,"output-inspection").apply{mkdirs()}
        folder.listFiles()?.forEach{it.delete()}
        val nativeSize=intent.getBooleanExtra("native_size",false)
        fun sample(index:Int) {
            try {
                if(output.isAvailable && output.isShown) {
                    output.getBitmap(if(nativeSize)output.width else 640,if(nativeSize)output.height else 480)?.let { bitmap ->
                        try {
                            EffectAccessibilityService.instance?.blackLayerForInspection()?.let{layer->
                                val canvas=android.graphics.Canvas(bitmap)
                                canvas.scale(bitmap.width.toFloat()/layer.width.coerceAtLeast(1),bitmap.height.toFloat()/layer.height.coerceAtLeast(1))
                                layer.draw(canvas)
                            }
                            File(folder,"frame-$index.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                        }
                        finally { bitmap.recycle() }
                    }
                }
                if(index<11 && !nativeSize)main.postDelayed({sample(index+1)},37)
                else pending.finish()
            }catch(error:Exception) {
                android.util.Log.e("ShaderInspect","Output inspection failed",error)
                pending.finish()
            }
        }
        sample(0)
    }
}
