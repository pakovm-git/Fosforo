package dev.pako.bfi.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import java.util.WeakHashMap
import dev.pako.bfi.R

/** Two native line planes. Cached paints/path; no images, capture or custom GPU program. */
class BlueprintDrawable(context:Context):Drawable() {
    private val density=context.resources.displayMetrics.density
    private val baseAlpha=if(context.resources.getBoolean(R.bool.light_system_bars)) .3f else .5f
    private val violet=context.getColor(R.color.bfi_action)
    private val lavender=context.getColor(R.color.bfi_logo_outline)
    private val page=Paint().apply{color=context.getColor(R.color.bfi_page)}
    private val rays=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=.75f*density}
    private val rows=Paint(Paint.ANTI_ALIAS_FLAG).apply{strokeWidth=.75f*density}
    private val bars=Paint().apply{color=context.getColor(R.color.bfi_header)}
    private val path=Path()
    private var scheduled=false
    private var blurred:GridBlur?=null
    private val glassViews=WeakHashMap<View,Unit>()
    internal var frameTime=SystemClock.uptimeMillis()
        private set
    internal fun observeGlass(view:View){glassViews[view]=Unit}
    @android.annotation.TargetApi(31)
    internal fun drawBlurred(canvas:Canvas){val cache=blurred?:GridBlur(this,density).also{blurred=it};cache.draw(canvas)}
    private val frame=Runnable {
        scheduled=false
        val view=callback as? View
        if(view?.isShown==true&&view.windowVisibility==View.VISIBLE){
            frameTime=SystemClock.uptimeMillis()
            // A parent background invalidation alone replays stale child display lists.
            glassViews.keys.forEach{if(it.isShown&&it.isAttachedToWindow)it.invalidate()}
            invalidateSelf()
        }
    }
    var statusBarHeight=0
        set(value){if(field!=value){field=value;invalidateSelf()}}
    var navigationBarHeight=0
        set(value){if(field!=value){field=value;invalidateSelf()}}
    override fun onBoundsChange(b:Rect){
        path.rewind();val w=b.width().toFloat();val h=b.height().toFloat();val cx=b.exactCenterX();val cy=b.exactCenterY()
        for(side in listOf(-1,1))for(i in -12..12){val x=w*.11f*i;path.moveTo(cx+x,cy+side*h/2);path.lineTo(cx+x/6,cy+side*h/12)}
        rays.shader=LinearGradient(0f,b.top.toFloat(),0f,b.bottom.toFloat(),intArrayOf(violet,violet and 0x00ffffff,violet and 0x00ffffff,violet),floatArrayOf(0f,.34f,.66f,1f),Shader.TileMode.CLAMP)
    }
    fun drawScene(canvas:Canvas,timeMillis:Long){
        canvas.drawRect(bounds,page);rays.alpha=(baseAlpha*255).toInt();canvas.drawPath(path,rays)
        val h=bounds.height().toFloat();val cy=bounds.exactCenterY();val phase=if(ValueAnimator.areAnimatorsEnabled())(timeMillis%20000)/20000f else 0f
        for(i in 0 until 28){val z=.7f+(i+1-phase)*.35f;if(z<1||z>6)continue
            val a=((1/z-.18f)/.82f).coerceIn(0f,1f);rows.color=if(a>.65f)lavender else violet;rows.alpha=(255*baseAlpha*a).toInt()
            val y=h/(2*z);canvas.drawLine(bounds.left.toFloat(),cy-y,bounds.right.toFloat(),cy-y,rows);canvas.drawLine(bounds.left.toFloat(),cy+y,bounds.right.toFloat(),cy+y,rows)
        }
    }
    override fun draw(canvas:Canvas){
        drawScene(canvas,frameTime)
        if(android.os.Build.VERSION.SDK_INT>=31&&canvas.isHardwareAccelerated&&(statusBarHeight>0||navigationBarHeight>0)){
            val checkpoint=canvas.save()
            canvas.clipRect(bounds.left,bounds.top,bounds.right,bounds.top+statusBarHeight)
            drawBlurred(canvas);canvas.restoreToCount(checkpoint)
            if(navigationBarHeight>0){
                val bottom=canvas.save();canvas.clipRect(bounds.left,bounds.bottom-navigationBarHeight,bounds.right,bounds.bottom)
                drawBlurred(canvas);canvas.restoreToCount(bottom)
            }
        }
        canvas.drawRect(bounds.left.toFloat(),bounds.top.toFloat(),bounds.right.toFloat(),(bounds.top+statusBarHeight).toFloat(),bars)
        canvas.drawRect(bounds.left.toFloat(),(bounds.bottom-navigationBarHeight).toFloat(),bounds.right.toFloat(),bounds.bottom.toFloat(),bars)
        val view=callback as? View
        if(!scheduled&&isVisible&&view?.isShown==true&&view.windowVisibility==View.VISIBLE&&ValueAnimator.areAnimatorsEnabled()){
            scheduled=true;scheduleSelf(frame,SystemClock.uptimeMillis()+32)
        }
    }
    override fun setVisible(visible:Boolean,restart:Boolean):Boolean{if(!visible){unscheduleSelf(frame);scheduled=false};return super.setVisible(visible,restart)}
    override fun setAlpha(alpha:Int){page.alpha=alpha;invalidateSelf()}
    override fun setColorFilter(filter:ColorFilter?){page.colorFilter=filter;rays.colorFilter=filter;rows.colorFilter=filter;invalidateSelf()}
    @Deprecated("Required by Drawable") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
