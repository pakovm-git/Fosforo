package dev.pako.bfi.ui

import android.app.*
import android.content.*
import android.os.*
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import dev.pako.bfi.*
import dev.pako.bfi.model.BfiSettings
import dev.pako.bfi.settings.SettingsStatus

abstract class ScreenActivity : Activity() {
    protected val runtime get() = (application as BfiApplication).runtime
    protected val repository get() = runtime.repository
    protected lateinit var content: LinearLayout
    protected lateinit var resetButton: Button
    protected var screenStarted=false
    protected var callbackGeneration=0L
    private lateinit var presentation: LocalBfiPresentation
    private var unsubscribe: (() -> Unit)? = null
    private var backCallback: android.window.OnBackInvokedCallback? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_settings)
        content=findViewById(R.id.settings_content); resetButton=findViewById(R.id.restore_action)
        findViewById<Button>(R.id.back_action).setOnClickListener { requestClose() }
        findViewById<Button>(R.id.editing_stop).setOnClickListener { runtime.stop() }
        resetButton.setOnClickListener {
            AlertDialog.Builder(this).setTitle(R.string.restore_defaults).setMessage(restoreMessage)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.restore_defaults) { _,_ -> restoreDefaults() }.show()
        }
        configureInsets(findViewById(R.id.root),window)
        presentation=LocalBfiPresentation(this,runtime)
        if (Build.VERSION.SDK_INT >= 33) {
            val callback=android.window.OnBackInvokedCallback { requestClose() }; backCallback=callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,callback)
        }
    }
    protected fun title(id: Int) { findViewById<TextView>(R.id.screen_title).setText(id) }
    protected fun title(value: String) { findViewById<TextView>(R.id.screen_title).text=value }
    internal fun boundedCards(firstWideWeight: Float=.36f,firstNarrowWeight: Float=.43f): ScreenCards {
        val scroll=findViewById<View>(R.id.settings_scroll); val root=scroll.parent as LinearLayout
        val position=root.indexOfChild(scroll); root.removeView(scroll)
        content=LinearLayout(this)
        root.addView(content,position,LinearLayout.LayoutParams(-1,0,1f))
        return ScreenCards(this,content,firstWideWeight,firstNarrowWeight)
    }
    protected fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    protected fun column(parent: LinearLayout=content,card: Boolean=false): LinearLayout = LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL
        if (card) { setBackgroundResource(R.drawable.card); setPadding(dp(16),dp(16),dp(16),dp(16)) }
        parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
    }
    protected fun text(id: Int,parent: LinearLayout=content,secondary: Boolean=false): TextView = TextView(this).apply {
        setText(id); textSize=if (secondary) 14f else 18f
        setTextColor(getColor(if (secondary) R.color.bfi_text_secondary else R.color.bfi_text_primary))
        parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
    }
    protected fun button(id: Int,parent: LinearLayout=content,action: () -> Unit): Button = Button(ContextThemeWrapper(this,R.style.Action),null,0).apply {
        setText(id); parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) }); setOnClickListener { action() }
    }
    protected fun numberField(parent: LinearLayout=content): EditText = EditText(this).apply {
        id=View.generateViewId(); inputType=android.text.InputType.TYPE_CLASS_NUMBER; minHeight=dp(48); setSingleLine(true)
        imeOptions=EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_DONE
        setTextColor(getColor(R.color.bfi_text_primary)); parent.addView(this,LinearLayout.LayoutParams(-1,-2))
    }
    protected fun check(id: Int,parent: LinearLayout=content): CheckBox = CheckBox(this).apply {
        setText(id); minHeight=dp(48); setTextColor(getColor(R.color.bfi_text_primary)); parent.addView(this,LinearLayout.LayoutParams(-1,-2))
    }
    protected fun section(id: Int,parent: LinearLayout=content) = text(id,parent).apply {
        typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)
        if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading=true
    }
    protected fun save(settings: BfiSettings) {
        if (settings == repository.settings) return
        val generation=callbackGeneration
        repository.save(settings) { success ->
            if (!screenStarted || generation != callbackGeneration || isDestroyed) return@save
            if (!success) Toast.makeText(this,R.string.save_failed,Toast.LENGTH_LONG).show()
        }
    }
    protected abstract fun saveDraft()
    protected open val restoreMessage=R.string.restore_page_confirm
    protected abstract fun restoreDefaults()
    protected open fun refreshScreen() { }
    override fun onStart() {
        super.onStart(); presentation.start(); screenStarted=true; callbackGeneration++
        unsubscribe=runtime.subscribe {
            val enabled=runtime.state.userEnabled
            findViewById<TextView>(R.id.editing_help).apply {
                visibility=View.VISIBLE
                setText(if (repository.writing) R.string.saving_changes else R.string.autosave_help)
            }
            findViewById<View>(R.id.editing_stop).visibility=if (enabled) View.VISIBLE else View.GONE
            resetButton.isEnabled=repository.status is SettingsStatus.Ready
            refreshScreen()
        }
    }
    override fun onStop() { presentation.stop(); screenStarted=false; callbackGeneration++; unsubscribe?.invoke(); unsubscribe=null; super.onStop() }
    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33) backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        super.onDestroy()
    }
    protected fun requestClose() {
        finish()
    }
    @Deprecated("Used only on API26–32; newer versions use OnBackInvokedDispatcher")
    @android.annotation.SuppressLint("GestureBackNavigation") // API33+ registers the platform dispatcher above; keep API26–32 fallback.
    override fun onBackPressed() { requestClose() }
    companion object {
        @Suppress("DEPRECATION") // Native edge-to-edge fallback for API26–29; colors on API26–34.
        fun configureInsets(root: View,window: Window) {
            if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
            else window.decorView.systemUiVisibility=window.decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            window.statusBarColor=android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 27) window.navigationBarColor=android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 29) {
                window.isStatusBarContrastEnforced=false
                window.isNavigationBarContrastEnforced=false
            }
            root.background=BlueprintDrawable(root.context)
            val left=root.paddingLeft; val top=root.paddingTop; val right=root.paddingRight; val bottom=root.paddingBottom
            root.setOnApplyWindowInsetsListener { view,insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    val ime=insets.getInsets(WindowInsets.Type.ime())
                    view.setPadding(left+bars.left,top+bars.top,right+bars.right,bottom+maxOf(bars.bottom,ime.bottom))
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(left+insets.systemWindowInsetLeft,top+insets.systemWindowInsetTop,right+insets.systemWindowInsetRight,bottom+insets.systemWindowInsetBottom)
                }
                insets
            }
            root.requestApplyInsets()
        }
    }
}
