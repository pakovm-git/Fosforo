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
    private lateinit var controllerHints:ControllerHints
    protected open val hasEffectsAction=false
    private var pendingEffects:dev.pako.bfi.state.StartRequest?=null
    private lateinit var savingStatus:TextView
    private var savingPlaced=false
    private var unsubscribe: (() -> Unit)? = null
    private var backCallback: android.window.OnBackInvokedCallback? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_settings)
        content=findViewById(R.id.settings_content); resetButton=findViewById(R.id.restore_action)
        savingStatus=findViewById<TextView>(R.id.editing_help).apply{visibility=View.INVISIBLE;textSize=12f;setSingleLine(true);gravity=Gravity.END or Gravity.CENTER_VERTICAL;setText(R.string.saving_changes)}
        (savingStatus.parent as ViewGroup).removeView(savingStatus)
        findViewById<Button>(R.id.back_action).apply{UiSkin.back(this);setOnClickListener { requestClose() }}
        UiSkin.controller(findViewById(R.id.restore_action),"Y");UiSkin.controller(findViewById(R.id.editing_stop),"X");UiSkin.power(findViewById(R.id.editing_stop))
        findViewById<Button>(R.id.editing_stop).setOnClickListener { stopEffects() }
        resetButton.setOnClickListener { confirmRestoreDefaults() }
        configureInsets(findViewById(R.id.root),window)
        controllerHints=ControllerHints(this,findViewById(R.id.root),stop={stopEffects()},reset={confirmRestoreDefaults()},resetEnabled={canRestore()},resetLabel=resetPrompt) { requestClose() }
        if (Build.VERSION.SDK_INT >= 33) {
            val callback=android.window.OnBackInvokedCallback { requestClose() }; backCallback=callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,callback)
        }
    }
    protected fun title(id: Int) { findViewById<TextView>(R.id.screen_title).setText(id) }
    protected fun title(value: String) { findViewById<TextView>(R.id.screen_title).text=value }
    internal fun boundedCards(firstWideWeight: Float=.5f,firstNarrowWeight: Float=.43f): ScreenCards {
        val scroll=findViewById<View>(R.id.settings_scroll); val root=scroll.parent as LinearLayout
        val position=root.indexOfChild(scroll); root.removeView(scroll)
        content=LinearLayout(this)
        root.addView(content,position,LinearLayout.LayoutParams(-1,0,1f))
        return ScreenCards(this,content,firstWideWeight,firstNarrowWeight)
    }
    protected fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    protected fun column(parent: LinearLayout=content,card: Boolean=false): LinearLayout = LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL
        if (card) { setBackgroundResource(R.drawable.card);tag="glass"; setPadding(dp(10),dp(10),dp(10),dp(10)) }
        parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
    }
    protected fun group(title:Int,parent:LinearLayout=content)=group(getString(title),parent)
    protected fun group(title:String,parent:LinearLayout=content):LinearLayout {
        val card=column(parent).apply{setBackgroundResource(R.drawable.card);tag="glass";clipToOutline=true;setPadding(dp(2),dp(2),dp(2),dp(2))}
        val heading=TextView(this).apply{
            text=title;textSize=14f;isAllCaps=true;setTextColor(getColor(R.color.bfi_logo_outline));setPadding(dp(10),dp(6),dp(4),dp(6))
            if(Build.VERSION.SDK_INT>=28)isAccessibilityHeading=true
        }
        if(!savingPlaced){
            savingPlaced=true
            val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setBackgroundResource(R.drawable.group_card_header)}
            row.addView(heading,LinearLayout.LayoutParams(0,-2,1f));row.addView(savingStatus,LinearLayout.LayoutParams(dp(96),-1).apply{marginEnd=dp(10)})
            card.addView(row,LinearLayout.LayoutParams(-1,-2))
        }else{heading.setBackgroundResource(R.drawable.group_card_header);card.addView(heading,LinearLayout.LayoutParams(-1,-2))}
        return LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(10),dp(10),dp(10));card.addView(this,LinearLayout.LayoutParams(-1,-2))}
    }
    protected fun text(id: Int,parent: LinearLayout=content,secondary: Boolean=false): TextView = TextView(this).apply {
        setText(id); textSize=if (secondary) 14f else 16f
        setTextColor(getColor(if (secondary) R.color.bfi_text_secondary else R.color.bfi_text_primary))
        parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
    }
    protected fun button(id: Int,parent: LinearLayout=content,action: () -> Unit): Button = Button(ContextThemeWrapper(this,R.style.PreferenceAction),null,0).apply {
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
        textSize=14f;isAllCaps=true;setTextColor(getColor(R.color.bfi_logo_outline));setBackgroundResource(R.drawable.group_header);setPadding(dp(2),dp(6),dp(2),dp(6))
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
    protected open val resetPrompt=R.string.controller_reset
    protected open val restoreTitle=R.string.restore_defaults
    protected open fun canRestore()=repository.status is SettingsStatus.Ready
    protected open fun restoreConfirmation()=getString(restoreMessage)
    protected open fun stopEffects(){toggleEffects()}
    protected open fun toggleEffects(){
        if(runtime.state.userEnabled||pendingEffects!=null){pendingEffects=null;runtime.stop();return}
        val problem=runtime.readiness();val request=runtime.prepareStart()
        if(problem==dev.pako.bfi.state.Reason.CAPTURE){pendingEffects=request;startActivityForResult(getSystemService(android.media.projection.MediaProjectionManager::class.java).createScreenCaptureIntent(),205)}
        else if(problem==null)runtime.start(request)
        else{Toast.makeText(this,UiText.reason(this,problem),Toast.LENGTH_LONG).show();startActivity(Intent(this,MainActivity::class.java))}
    }
    @Deprecated("Native Activity result callback")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==205){val request=pendingEffects;pendingEffects=null;if(resultCode==RESULT_OK&&data!=null&&request!=null)runtime.start(request,data)}
    }
    protected fun stopFromController(event:KeyEvent)=controllerHints.stopKey(event)
    protected fun updateControllerHints(){controllerHints.refresh()}
    protected fun confirmRestoreDefaults(){
        if(!canRestore())return
        AlertDialog.Builder(this).setTitle(restoreTitle).setMessage(restoreConfirmation())
            .setOnKeyListener{_,_,event->stopFromController(event)}
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.restore_defaults){_,_->restoreDefaults()}.show()
    }
    protected abstract fun saveDraft()
    protected open val restoreMessage=R.string.restore_page_confirm
    protected abstract fun restoreDefaults()
    protected open fun refreshScreen() { }
    override fun onStart() {
        super.onStart(); UiSkin.typography(findViewById(R.id.root));screenStarted=true; callbackGeneration++
        unsubscribe=runtime.subscribe {
            val enabled=runtime.state.userEnabled
            savingStatus.visibility=if(repository.writing)View.VISIBLE else View.INVISIBLE
            findViewById<Button>(R.id.editing_stop).apply{
                visibility=if(hasEffectsAction||enabled)View.VISIBLE else View.GONE
                setText(if(enabled)R.string.stop_effects else R.string.start_effects)
                setBackgroundResource(if(enabled)R.drawable.stop_action else R.drawable.primary_action)
                setTextColor(getColor(if(enabled)R.color.bfi_on_stop else R.color.bfi_on_action))
            }
            resetButton.isEnabled=repository.status is SettingsStatus.Ready
            refreshScreen();controllerHints.refresh()
        }
    }
    override fun onStop() { screenStarted=false; callbackGeneration++; unsubscribe?.invoke(); unsubscribe=null; super.onStop() }
    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33) backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        super.onDestroy()
    }
    override fun dispatchKeyEvent(event:KeyEvent):Boolean = controllerHints.key(event) || super.dispatchKeyEvent(event)
    override fun dispatchTouchEvent(event:MotionEvent):Boolean { controllerHints.touch(event);return super.dispatchTouchEvent(event) }
    protected open fun requestClose() {
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
            val backdrop=BlueprintDrawable(root.context)
            root.background=backdrop
            root.foreground=CrtFinishDrawable(root.context)
            val left=root.paddingLeft; val top=root.paddingTop; val right=root.paddingRight; val bottom=root.paddingBottom
            root.setOnApplyWindowInsetsListener { view,insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    val ime=insets.getInsets(WindowInsets.Type.ime())
                    backdrop.statusBarHeight=bars.top;backdrop.navigationBarHeight=bars.bottom
                    view.setPadding(left+bars.left,top+bars.top,right+bars.right,bottom+maxOf(bars.bottom,ime.bottom))
                } else {
                    @Suppress("DEPRECATION")
                    val statusTop=insets.systemWindowInsetTop
                    backdrop.statusBarHeight=statusTop;backdrop.navigationBarHeight=insets.systemWindowInsetBottom
                    @Suppress("DEPRECATION")
                    view.setPadding(left+insets.systemWindowInsetLeft,top+insets.systemWindowInsetTop,right+insets.systemWindowInsetRight,bottom+insets.systemWindowInsetBottom)
                }
                insets
            }
            root.requestApplyInsets()
        }
    }
}
