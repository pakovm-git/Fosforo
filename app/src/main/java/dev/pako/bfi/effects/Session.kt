package dev.pako.bfi.effects

// Render-only snapshot. Enabled intent and deadlines belong to BfiRuntime.
internal enum class TemporalEffect { OFF, BFI }
internal object Session {
    @Volatile var temporalEffect=TemporalEffect.OFF
    val bfi get()=temporalEffect==TemporalEffect.BFI
    @Volatile var pattern=BfiPattern.legacy(1,1)
    @Volatile var shader=ShaderState("lottes",emptyMap())
    @Volatile var crt=true
    @Volatile var previewBfi=false
    @Volatile var captureHz=0.0
}

internal fun effectivePattern(settings:dev.pako.bfi.model.BfiSettings, pattern:dev.pako.bfi.model.BfiPattern, foreground:String?):BfiPattern =
    if(settings.multiStepPattern.isNotEmpty() && foreground !in settings.overrides)
        BfiPattern.parse(settings.multiStepPattern)!! else BfiPattern.legacy(pattern.visibleSlots,pattern.blackSlots)
