package dev.pako.bfi.ui

import android.graphics.*
import android.graphics.drawable.Drawable
import android.content.Context
import android.view.*
import android.widget.*
import dev.pako.bfi.R

/** Shared native typography and selection chrome; no rendering or session state. */
internal object UiSkin {
    fun typography(view:View) {
        if((view is ScrollView||view is AbsListView)&&view.isVerticalScrollBarEnabled&&view.getTag(R.id.scroll_gutter)!=true){
            view.setTag(R.id.scroll_gutter,true)
            val gap=(12*view.resources.displayMetrics.density).toInt();val rtl=view.layoutDirection==View.LAYOUT_DIRECTION_RTL
            val left=if(rtl)gap else 0;val right=if(rtl)0 else gap
            view.scrollBarStyle=View.SCROLLBARS_OUTSIDE_INSET
            view.setPadding(view.paddingLeft+left,view.paddingTop,view.paddingRight+right,view.paddingBottom)
        }
        if(view.tag in listOf("glass","glass-bar")&&view.background!=null&&view.background !is GlassBackground){
            val left=view.paddingLeft;val top=view.paddingTop;val right=view.paddingRight;val bottom=view.paddingBottom
            view.background=GlassBackground(view,view.background,if(view.tag=="glass-bar")0f else 18*view.resources.displayMetrics.density)
            view.setPadding(left,top,right,bottom)
        }
        if(view is TextView){
            view.typeface=view.resources.getFont(R.font.jersey15)
            view.setShadowLayer(.35f*view.resources.displayMetrics.density,0f,0f,(view.currentTextColor and 0x00ffffff) or 0x22000000)
            if(view.tag=="phosphor-brand"||view.id==R.id.screen_title){view.textSize=30f;activeStatus(view,true)}
        }
        if(view is ViewGroup)for(i in 0 until view.childCount)typography(view.getChildAt(i))
    }
    fun activeStatus(view:TextView,active:Boolean){
        view.setTextColor(view.context.getColor(if(active)R.color.bfi_active else R.color.bfi_text_primary))
        val glow=active&&!view.resources.getBoolean(R.bool.light_system_bars)
        view.setShadowLayer(if(glow)3*view.resources.displayMetrics.density else 0f,0f,0f,if(glow)0x66FFE04A else Color.TRANSPARENT)
    }
    fun controller(button:Button,key:String){button.setTag(R.id.controller_key,key)}
    fun badges(view:View,visible:Boolean){
        if(view is Button){val key=view.getTag(R.id.controller_key) as? String
            if(key!=null){val start=view.compoundDrawablesRelative[0];val badge=if(visible)ControllerBadge(view,key).apply{val size=(22*view.resources.displayMetrics.density).toInt();setBounds(0,0,size,size)}else null;view.compoundDrawablePadding=(6*view.resources.displayMetrics.density).toInt();view.setCompoundDrawablesRelative(start,null,badge,null)}
        }
        if(view is ViewGroup)for(i in 0 until view.childCount)badges(view.getChildAt(i),visible)
    }
    fun back(button:Button){
        button.text="";button.contentDescription=button.context.getString(R.string.back);button.gravity=Gravity.CENTER;button.minimumWidth=(56*button.resources.displayMetrics.density).toInt();button.minHeight=(56*button.resources.displayMetrics.density).toInt();val arrow=button.context.getDrawable(R.drawable.ic_back_chevron)!!.mutate();arrow.setTint(button.currentTextColor);button.setCompoundDrawablesRelativeWithIntrinsicBounds(arrow,null,null,null);controller(button,"B")
    }
    fun segmentTrack(group:RadioGroup){
        val density=group.resources.displayMetrics.density
        group.orientation=RadioGroup.HORIZONTAL;group.isBaselineAligned=false
        group.setBackgroundResource(R.drawable.segment_track);group.setPadding((2*density).toInt(),0,(2*density).toInt(),0)
        group.minimumHeight=(48*density).toInt()
    }
    fun power(button:Button){
        val icon=button.context.getDrawable(R.drawable.ic_power)!!.mutate();icon.setTintList(button.textColors)
        val end=button.compoundDrawablesRelative[2]
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon,null,end,null);button.compoundDrawablePadding=(8*button.resources.displayMetrics.density).toInt()
    }
    fun segment(button:RadioButton) {
        button.setTextColor(button.context.getColorStateList(R.color.segment_text))
        button.minWidth=0;button.minimumWidth=0;button.setMinHeight((48*button.resources.displayMetrics.density).toInt());button.includeFontPadding=false;button.isSingleLine=true;button.maxLines=1;button.ellipsize=android.text.TextUtils.TruncateAt.END
        button.buttonDrawable=null;button.gravity=Gravity.CENTER;button.minHeight=(48*button.resources.displayMetrics.density).toInt()
        button.setBackgroundResource(R.drawable.segment_action);button.setPadding(4,0,4,0);button.textSize=14f
    }
}

/** A cached, static finish above native views. Never schedules frames or captures pixels. */
internal class CrtFinishDrawable(context:Context):Drawable() {
    private val scan=Paint()
    private val edge=Paint()
    init {
        val tile=Bitmap.createBitmap(1,4,Bitmap.Config.ARGB_8888)
        tile.setPixel(0,0,context.getColor(R.color.bfi_crt_line))
        scan.shader=BitmapShader(tile,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT)
        edge.color=context.getColor(R.color.bfi_crt_edge)
    }
    override fun onBoundsChange(bounds:Rect) {
        val radius=maxOf(bounds.width(),bounds.height()).coerceAtLeast(1)*.72f
        edge.shader=RadialGradient(bounds.exactCenterX(),bounds.exactCenterY(),radius,intArrayOf(Color.TRANSPARENT,Color.TRANSPARENT,edge.color),floatArrayOf(0f,.65f,1f),Shader.TileMode.CLAMP)
    }
    override fun draw(canvas:Canvas){canvas.drawRect(bounds,scan);canvas.drawRect(bounds,edge)}
    override fun setAlpha(alpha:Int){scan.alpha=alpha;edge.alpha=alpha;invalidateSelf()}
    override fun setColorFilter(filter:ColorFilter?){scan.colorFilter=filter;edge.colorFilter=filter;invalidateSelf()}
    @Deprecated("Required by Drawable") override fun getOpacity()=PixelFormat.TRANSLUCENT
}

/** Button badges share the footer's circular shape and do not change the button label. */
internal class ControllerBadge(private val button:Button,private val letter:String):Drawable(){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=button.resources.getFont(R.font.jersey15);textAlign=Paint.Align.CENTER}
    override fun draw(canvas:Canvas){val r=bounds.height()/2f;paint.color=button.currentTextColor;paint.style=Paint.Style.STROKE;paint.strokeWidth=button.resources.displayMetrics.density*1.5f;canvas.drawCircle(bounds.exactCenterX(),bounds.exactCenterY(),r-paint.strokeWidth,paint);paint.style=Paint.Style.FILL;paint.textSize=button.textSize*.9f;canvas.drawText(letter,bounds.exactCenterX(),bounds.exactCenterY()-(paint.ascent()+paint.descent())/2,paint)}
    override fun setAlpha(alpha:Int){paint.alpha=alpha}
    override fun setColorFilter(filter:ColorFilter?){paint.colorFilter=filter}
    @Deprecated("Required by Drawable") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
