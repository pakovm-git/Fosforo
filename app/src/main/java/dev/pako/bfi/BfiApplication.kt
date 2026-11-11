package dev.pako.bfi

import android.app.Application
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import dev.pako.bfi.settings.SettingsRepository
import dev.pako.bfi.state.BfiRuntime

class BfiApplication : Application() {
    lateinit var runtime: BfiRuntime
        private set
    override fun onCreate() {
        super.onCreate()
        ExperimentService.prepareChannel(this)
        val main = Handler(Looper.getMainLooper())
        runtime = BfiRuntime(this,main,SettingsRepository(this,main))
        val resumed=mutableSetOf<Activity>()
        val release=Runnable { if (resumed.isEmpty()) runtime.controlsVisibility(false) }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if(activity is dev.pako.bfi.ui.TestEffectsActivity){runtime.controlsVisibility(false);return}
                resumed.add(activity); main.removeCallbacks(release); runtime.controlsVisibility(true)
            }
            override fun onActivityPaused(activity: Activity) {
                resumed.remove(activity); main.removeCallbacks(release); main.postDelayed(release,150)
            }
            override fun onActivityDestroyed(activity: Activity) { resumed.remove(activity) }
            override fun onActivityCreated(activity: Activity,state: Bundle?)=Unit
            override fun onActivityStarted(activity: Activity)=Unit
            override fun onActivityStopped(activity: Activity)=Unit
            override fun onActivitySaveInstanceState(activity: Activity,state: Bundle)=Unit
        })
    }
}
