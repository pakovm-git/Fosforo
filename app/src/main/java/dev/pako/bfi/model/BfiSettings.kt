package dev.pako.bfi.model

enum class ActivationMode { ALL_APPS, SELECTED_APPS }
sealed interface RefreshTarget {
    data object Maximum : RefreshTarget
    data class Numeric(val hz: Double) : RefreshTarget { init { require(hz.isFinite() && hz > 0) } }
}
data class BfiSettings(
    val bfiEnabled: Boolean = true,
    val crtEnabled: Boolean = false,
    val shaderId: String = "lottes",
    val favouriteShaders: Set<String> = emptySet(),
    val shaderValues: Map<String, Float> = emptyMap(),
    val multiStepPattern: String = "",
    val schemaVersion: Int = 1,
    val globalPattern: BfiPattern = BfiPattern(),
    val activationMode: ActivationMode = ActivationMode.ALL_APPS,
    val includedPackages: Set<String> = emptySet(),
    val excludedPackages: Set<String> = emptySet(),
    val overrides: Map<String, BfiPattern> = emptyMap(),
    val shaderOverrides: Map<String, AppShader> = emptyMap(),
    val refreshTarget: RefreshTarget = RefreshTarget.Maximum,
    val requestRefresh: Boolean = false,
    val alwaysForce: Boolean = false,
    val resumeAfterUnlock: Boolean = true,
    val diagnosticsEnabled: Boolean = false,
    val lastTestSeconds: Int = 30,
    val acceptedNoticeVersion: Int = 0
) {
    init { require(schemaVersion == 1 && lastTestSeconds in 1..600 && acceptedNoticeVersion >= 0) }
    init {
        require(favouriteShaders.size<=4096 && favouriteShaders.all{it.length in 1..160 && it.matches(Regex("[A-Za-z0-9_-]+"))})
        AppShader(shaderId,shaderValues)
        require(shaderOverrides.size<=4096 && shaderOverrides.keys.all { it.isNotBlank() && it.length<=255 && it.none { c -> c=='|' || c.isWhitespace() } })
        require(multiStepPattern.isEmpty() || dev.pako.bfi.effects.BfiPattern.parse(multiStepPattern)!=null)
    }
    fun shaderFor(pkg:String?) = shaderOverrides[pkg] ?: AppShader(shaderId,shaderValues)
    fun withShader(pkg:String?,shader:AppShader) = if(pkg==null) copy(shaderId=shader.id,shaderValues=shader.values)
        else copy(shaderOverrides=shaderOverrides+(pkg to shader))
    val needsAppIdentity get() = shaderOverrides.isNotEmpty() || activationMode == ActivationMode.SELECTED_APPS || excludedPackages.isNotEmpty() || overrides.isNotEmpty()
}

/** A per-app selection inherits global settings when no override is stored. */
data class AppShader(val id:String,val values:Map<String,Float> = emptyMap()) {
    init {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,160}")))
        require(values.size<=512 && values.all { (key,value) -> key.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")) && value.isFinite() })
    }
}
