package dev.pako.bfi.display

import dev.pako.bfi.model.RefreshTarget
import kotlin.math.abs

data class DisplayMode(val id: Int, val width: Int, val height: Int, val hz: Double)
object RefreshRequestResolver {
    fun eligible(modes: List<DisplayMode>, width: Int, height: Int) = modes.filter { it.width == width && it.height == height && it.hz.isFinite() && it.hz > 0 }
    fun resolve(modes: List<DisplayMode>, width: Int, height: Int, target: RefreshTarget): DisplayMode? {
        val candidates = eligible(modes, width, height)
        return when (target) {
            RefreshTarget.Maximum -> candidates.sortedWith(compareByDescending<DisplayMode> { it.hz }.thenBy { it.id }).firstOrNull()
            is RefreshTarget.Numeric -> candidates.filter { abs(it.hz - target.hz) <= 0.1 + 1e-9 }
                .sortedWith(compareBy<DisplayMode> { abs(it.hz - target.hz) }.thenBy { it.id }).firstOrNull()
        }
    }
}
