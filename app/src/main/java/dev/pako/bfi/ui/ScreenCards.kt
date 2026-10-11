package dev.pako.bfi.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ScrollView
import dev.pako.bfi.R

/** Bounded panels: the card belongs to the viewport, never to its scrolling child. */
internal class ScreenCards(context: Context,parent: LinearLayout,firstWideWeight: Float=.36f,firstNarrowWeight: Float=.43f) {
    val preview: LinearLayout
    val controls: LinearLayout
    private val density=context.resources.displayMetrics.density
    private fun dp(value: Int)=(value*density).toInt()
    init {
        val wide=context.resources.configuration.screenWidthDp >= 600
        parent.orientation=if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        fun panel(id: Int,weight: Float,first: Boolean): LinearLayout {
            val scroll=ScrollView(context).apply {
                this.id=id; setBackgroundResource(R.drawable.card); setPadding(dp(12),dp(12),dp(12),dp(12))
                isFillViewport=true; clipToPadding=true; scrollBarStyle=View.SCROLLBARS_INSIDE_OVERLAY
            }
            parent.addView(scroll,LinearLayout.LayoutParams(if (wide) 0 else -1,if (wide) -1 else 0,weight).apply {
                if (first) { if (wide) marginEnd=dp(12) else bottomMargin=dp(12) }
            })
            return LinearLayout(context).apply {
                orientation=LinearLayout.VERTICAL
                scroll.addView(this,FrameLayout.LayoutParams(-1,-2))
            }
        }
        val firstWeight=if(wide) firstWideWeight else firstNarrowWeight
        preview=panel(if(wide) R.id.screen_first_card_scroll_wide else R.id.screen_first_card_scroll_narrow,firstWeight,true)
        controls=panel(if(wide) R.id.screen_second_card_scroll_wide else R.id.screen_second_card_scroll_narrow,1f-firstWeight,false)
    }
}
