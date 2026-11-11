package dev.pako.bfi.effects

import android.view.SurfaceControl
import android.view.View
import org.lsposed.hiddenapibypass.HiddenApiBypass

/** Only accepts our attached output View; no global API exemptions or other apps' layers. */
@android.annotation.TargetApi(33)
internal object CaptureExclusion {
    fun apply(view:View,enabled:Boolean=true) {
        try {
            val root=checkNotNull(HiddenApiBypass.invoke(View::class.java,view,"getViewRootImpl")){"Output is not attached"}
            val surface=HiddenApiBypass.invoke(root.javaClass,root,"getSurfaceControl") as SurfaceControl
            check(surface.isValid){"Output layer unavailable"}
            // shortcut: private Android API tested on Nova/API33; use app-window capture on compatible newer OSs.
            SurfaceControl.Transaction().use { transaction ->
                HiddenApiBypass.invoke(SurfaceControl.Transaction::class.java,transaction,"setSkipScreenshot",surface,enabled)
                transaction.apply()
            }
        } catch(error:ReflectiveOperationException) {
            throw IllegalStateException("Capture exclusion API unavailable",error)
        } catch(error:LinkageError) {
            throw IllegalStateException("Capture exclusion runtime unsupported",error)
        }
    }
}
