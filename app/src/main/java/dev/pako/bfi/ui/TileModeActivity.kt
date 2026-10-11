package dev.pako.bfi.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import dev.pako.bfi.*
import dev.pako.bfi.model.ActivationMode

/** Opening or rotating this menu never activates output; only explicit button clicks do. */
class TileModeActivity : Activity() {
    private val runtime get()=(application as BfiApplication).runtime
    private var unsubscribe: (() -> Unit)?=null
    private lateinit var options: RadioGroup
    private val modes=listOf<ActivationMode?>(null,ActivationMode.ALL_APPS,ActivationMode.SELECTED_APPS)
    private var choosing=false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density=resources.displayMetrics.density
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; val pad=(24*density).toInt(); setPadding(pad,pad,pad,pad) }
        body.addView(TextView(this).apply { setText(R.string.tile_modes); textSize=24f; setTextColor(getColor(R.color.bfi_text_primary)) })
        options=RadioGroup(this).apply { isSaveEnabled=false }
        val labels=listOf(R.string.off,R.string.on,R.string.tile_on_apps)
        for (index in modes.indices) {
            val choice=RadioButton(this).apply {
                id=View.generateViewId(); setText(labels[index]); minHeight=(48*density).toInt(); setTextColor(getColor(R.color.bfi_text_primary))
                isSaveEnabled=false
            }
            options.addView(choice,RadioGroup.LayoutParams(-1,-2))
            choice.setOnClickListener {
                val mode=modes[index]
                if (mode == null) { runtime.stop(); finish() }
                else {
                    choosing=true; update()
                    runtime.chooseMode(mode) { enabled ->
                        if (isDestroyed || isFinishing) return@chooseMode
                        choosing=false
                        if (enabled) finish() else {
                            startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)); finish()
                        }
                    }
                }
            }
        }
        body.addView(options)
        body.addView(TextView(this).apply { setText(R.string.tile_mode_help); textSize=14f; setTextColor(getColor(R.color.bfi_text_secondary)) })
        body.addView(Button(android.view.ContextThemeWrapper(this,R.style.Action),null,0).apply {
            setText(R.string.customize); setOnClickListener { startActivity(Intent(this@TileModeActivity,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)); finish() }
        },LinearLayout.LayoutParams(-1,-2))
        setContentView(body)
        window.setLayout((resources.displayMetrics.widthPixels*0.9).toInt().coerceAtMost((480*density).toInt()),LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    private fun update() {
        val current=if (!runtime.state.userEnabled) 0 else if (runtime.state.settings.activationMode == ActivationMode.ALL_APPS) 1 else 2
        options.check(options.getChildAt(current).id)
        for (index in modes.indices) options.getChildAt(index).isEnabled=index == 0 || (!choosing && !runtime.repository.writing && runtime.repository.settings != null)
    }
    override fun onStart() { super.onStart(); unsubscribe=runtime.subscribe { update() } }
    override fun onStop() { unsubscribe?.invoke(); unsubscribe=null; super.onStop() }
}
