package dev.pako.bfi.ui

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import dev.pako.bfi.R

/** Shortcuts are local to the foreground BFI activity; no game input is intercepted. */
internal class ControllerHints(private val activity:Activity,private val root:ViewGroup,private val stop:(()->Unit)?=null,
                               private val reset:(()->Unit)?=null,private val resetEnabled:()->Boolean={true},
                               private val resetLabel:Int=R.string.controller_reset,private val back:()->Unit) {
    private fun dp(value:Int)=(value*activity.resources.displayMetrics.density).toInt()
    private val footer=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE;isFocusable=false;setPadding(dp(12),dp(6),dp(12),dp(6));setBackgroundResource(R.drawable.footer_bar);tag="glass-bar"}
    private var resetViews:List<View> = emptyList()
    init {
        val wide=activity.resources.configuration.screenWidthDp>=600
        val first=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        footer.addView(first,LinearLayout.LayoutParams(-1,-2))
        val second=if(wide)first else LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;footer.addView(this,LinearLayout.LayoutParams(-1,-2))}
        fun prompt(row:LinearLayout,letter:String,label:Int):List<View> {
            val badge=TextView(activity).apply{text=letter;gravity=Gravity.CENTER;setTextColor(activity.getColor(R.color.bfi_text_primary));textSize=14f;background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(activity.getColor(R.color.bfi_surface_secondary));setStroke(dp(2),activity.getColor(R.color.bfi_focus))}}
            val text=TextView(activity).apply{setText(label);textSize=14f;setTextColor(activity.getColor(R.color.bfi_text_secondary))}
            row.addView(badge,LinearLayout.LayoutParams(dp(24),dp(24)))
            row.addView(text,LinearLayout.LayoutParams(-2,-2).apply{marginStart=dp(5);marginEnd=dp(12)})
            return listOf(badge,text)
        }
        prompt(first,"A",R.string.controller_select);prompt(first,"B",R.string.controller_back)
        if(stop!=null)prompt(second,"X",R.string.toggle_effects)
        if(reset!=null)resetViews=prompt(second,"Y",resetLabel)
        if(root is LinearLayout)root.addView(footer,LinearLayout.LayoutParams(-1,-2))
        UiSkin.typography(footer);refresh()
    }
    fun refresh(){resetViews.forEach{it.visibility=if(resetEnabled())View.VISIBLE else View.GONE};UiSkin.badges(root,footer.visibility==View.VISIBLE)}
    private fun controller(event:KeyEvent)=event.isFromSource(InputDevice.SOURCE_GAMEPAD)||event.isFromSource(InputDevice.SOURCE_JOYSTICK)
    fun stopKey(event:KeyEvent):Boolean {
        if(event.keyCode!=KeyEvent.KEYCODE_BUTTON_X || !controller(event) || stop==null)return false
        footer.visibility=View.VISIBLE;refresh();if(event.action==KeyEvent.ACTION_UP)stop.invoke();return true
    }
    fun key(event:KeyEvent):Boolean {
        if(stopKey(event))return true
        val directional=event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_CENTER)
        if(!controller(event)&&!directional)return false
        footer.visibility=View.VISIBLE;refresh()
        if(event.keyCode==KeyEvent.KEYCODE_BUTTON_Y && reset!=null && resetEnabled()){if(event.action==KeyEvent.ACTION_UP)reset.invoke();return true}
        if(event.keyCode==KeyEvent.KEYCODE_BUTTON_B){if(event.action==KeyEvent.ACTION_UP)back();return true}
        if(event.keyCode==KeyEvent.KEYCODE_BUTTON_A){
            if(activity.currentFocus==null)activity.window.decorView.focusSearch(View.FOCUS_DOWN)?.requestFocus()
            return activity.dispatchKeyEvent(KeyEvent(event.downTime,event.eventTime,event.action,KeyEvent.KEYCODE_DPAD_CENTER,event.repeatCount,event.metaState,event.deviceId,event.scanCode,event.flags,event.source))
        }
        return false
    }
    fun touch(event:MotionEvent){if(event.actionMasked==MotionEvent.ACTION_DOWN){footer.visibility=View.GONE;refresh()}}
}
