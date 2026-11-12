package dev.pako.bfi.ui

import dev.pako.bfi.effects.ShaderParameter
import java.math.BigDecimal
import java.math.RoundingMode

/** Display precision includes exact preset defaults and endpoints, as well as the step. */
internal class ShaderValueText(parameter:ShaderParameter) {
    private val places=listOf(parameter.step,parameter.defaultValue,parameter.min,parameter.max)
        .maxOf { BigDecimal(it.toString()).scale().coerceAtLeast(0) }
    fun format(value:Float):String=BigDecimal(value.toString()).setScale(places,RoundingMode.HALF_UP)
        .stripTrailingZeros().toPlainString()
}
