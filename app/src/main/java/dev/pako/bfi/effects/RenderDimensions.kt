package dev.pako.bfi.effects

internal data class RenderDimensions(val sourceW:Int,val sourceH:Int,val viewportW:Int,val viewportH:Int)

/** Capture and final shader pixels match the window; the preview retains its synthetic source. */
internal fun renderDimensions(width:Int,height:Int,preview:Boolean):RenderDimensions {
    require(width>0 && height>0){"Invalid render dimensions"}
    return if(preview)RenderDimensions(640,360,(width-width/2).coerceAtLeast(1),height)
        else RenderDimensions(width,height,width,height)
}
