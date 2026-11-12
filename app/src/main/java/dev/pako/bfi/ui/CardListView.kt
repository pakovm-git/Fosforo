package dev.pako.bfi.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.widget.ListView

/** Clip scrolling rows, while the host card and the scrollbar retain their own drawing. */
internal class CardListView(context:Context):ListView(context){
    private val contentClip=Path()
    private val radius=16*resources.displayMetrics.density
    override fun dispatchDraw(canvas:Canvas){
        contentClip.rewind()
        contentClip.addRoundRect(paddingLeft.toFloat(),paddingTop.toFloat(),(width-paddingRight).toFloat(),(height-paddingBottom).toFloat(),radius,radius,Path.Direction.CW)
        val checkpoint=canvas.save()
        canvas.clipPath(contentClip)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(checkpoint)
    }
}
