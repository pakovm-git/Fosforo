package dev.pako.bfi.ui

import android.annotation.TargetApi
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import dev.pako.bfi.R

/** Only the generated page grid is blurred; text/controls and other apps never enter this node. */
@TargetApi(31)
internal class GridBlur(private val grid:BlueprintDrawable,density:Float){
    private val node=RenderNode("BFI grid glass").apply{setRenderEffect(RenderEffect.createBlurEffect(8*density,8*density,Shader.TileMode.CLAMP))}
    private var frame=-1L
    fun draw(canvas:Canvas){
        if(!canvas.isHardwareAccelerated)return
        val tick=grid.frameTime;val key=if(android.animation.ValueAnimator.areAnimatorsEnabled())tick else 0L
        val b=grid.bounds
        if(b.isEmpty)return
        if(frame!=key||node.width!=b.width()||node.height!=b.height()){
            node.setPosition(0,0,b.width(),b.height())
            val recording=node.beginRecording(b.width(),b.height())
            try{grid.drawScene(recording,tick)}finally{node.endRecording()}
            frame=key
        }
        canvas.drawRenderNode(node)
    }
}

internal class GlassBackground(private val owner:View,private val surface:Drawable,private val radius:Float):Drawable(),Drawable.Callback{
    private val path=Path()
    private val here=IntArray(2)
    private val origin=IntArray(2)
    init{surface.callback=this}
    override fun onBoundsChange(b:Rect){
        surface.bounds=b;val outline=Outline();surface.getOutline(outline);val shape=Rect()
        val outlined=outline.getRect(shape);path.rewind();path.addRoundRect(RectF(if(outlined)shape else b),if(outlined)outline.radius else radius,if(outlined)outline.radius else radius,Path.Direction.CW)
    }
    override fun draw(canvas:Canvas){
        val root=owner.rootView.findViewById<View>(R.id.root)
        val grid=root?.background as? BlueprintDrawable
        if(Build.VERSION.SDK_INT>=31&&canvas.isHardwareAccelerated&&grid!=null){
            grid.observeGlass(owner)
            owner.getLocationInWindow(here);root.getLocationInWindow(origin)
            val checkpoint=canvas.save();canvas.clipPath(path);canvas.translate((origin[0]-here[0]).toFloat(),(origin[1]-here[1]).toFloat())
            grid.drawBlurred(canvas);canvas.restoreToCount(checkpoint)
        }
        surface.draw(canvas)
    }
    override fun getOutline(outline:Outline)=surface.getOutline(outline)
    override fun getPadding(padding:Rect)=surface.getPadding(padding)
    override fun isStateful()=surface.isStateful
    override fun onStateChange(state:IntArray)=surface.setState(state)
    override fun setAlpha(alpha:Int){surface.alpha=alpha}
    override fun setColorFilter(filter:ColorFilter?){surface.colorFilter=filter}
    override fun invalidateDrawable(who:Drawable)=invalidateSelf()
    override fun scheduleDrawable(who:Drawable,what:Runnable,whenMillis:Long)=scheduleSelf(what,whenMillis)
    override fun unscheduleDrawable(who:Drawable,what:Runnable)=unscheduleSelf(what)
    @Deprecated("Required by Drawable")override fun getOpacity()=PixelFormat.TRANSLUCENT
}
