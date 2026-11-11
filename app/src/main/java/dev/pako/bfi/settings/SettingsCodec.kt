package dev.pako.bfi.settings

import dev.pako.bfi.model.*

/** Android-free validation; enabled intent and resource identifiers have no persisted keys. */
object SettingsCodec {
    fun encode(s: BfiSettings): Map<String, Any> = mapOf(
        "useBfi" to s.bfiEnabled, "useCrt" to s.crtEnabled, "shaderId" to s.shaderId,
        "shaderValues" to s.shaderValues.map { (key,value) -> "$key|$value" }.toSet(), "multiStepPattern" to s.multiStepPattern,
        "schema" to s.schemaVersion, "visible" to s.globalPattern.visibleSlots, "black" to s.globalPattern.blackSlots,
        "activation" to s.activationMode.name, "included" to s.includedPackages.toSet(), "excluded" to s.excludedPackages.toSet(),
        "overrides" to s.overrides.map { (pkg, p) -> "$pkg|${p.visibleSlots}|${p.blackSlots}" }.toSet(),
        "target" to when (val target = s.refreshTarget) { RefreshTarget.Maximum -> "maximum"; is RefreshTarget.Numeric -> target.hz.toString() },
        "request" to s.requestRefresh, "force" to s.alwaysForce, "resume" to s.resumeAfterUnlock,
        "diagnostics" to s.diagnosticsEnabled, "testSeconds" to s.lastTestSeconds, "notice" to s.acceptedNoticeVersion
    )
    private inline fun <reified T> value(data: Map<String, Any>, key: String, default: T): T =
        if (key !in data) default else data[key] as? T ?: error("Invalid setting: $key")
    private fun packages(data: Map<String, Any>, key: String): Set<String> {
        val raw = value<Set<*>>(data, key, emptySet<Any>())
        return raw.map { require(it is String && packageValid(it)); it }.toSet()
    }
    fun packageValid(pkg: String) = pkg.isNotBlank() && pkg.length <= 255 && pkg.none { it == '|' || it.isWhitespace() }
    private fun decodeShaderValues(raw:Set<*>):Map<String,Float> {
        val pairs=raw.map { value -> require(value is String); val parts=value.split('|'); require(parts.size==2); parts[0] to parts[1].toFloat() }
        require(pairs.map{it.first}.distinct().size==pairs.size)
        return pairs.toMap()
    }
    fun decode(data: Map<String, Any>): BfiSettings {
        require(value(data, "schema", 1) == 1) { "Unsupported settings schema" }
        val overrides = value<Set<*>>(data, "overrides", emptySet<Any>()).map { raw ->
            require(raw is String)
            val parts = raw.split('|'); require(parts.size == 3 && packageValid(parts[0]))
            parts[0] to BfiPattern(parts[1].toInt(), parts[2].toInt())
        }
        require(overrides.map { it.first }.distinct().size == overrides.size)
        val target = value(data, "target", "maximum")
        return BfiSettings(
            bfiEnabled=value(data,"useBfi",true), crtEnabled=value(data,"useCrt",false),
            shaderId=value(data,"shaderId","lottes"), multiStepPattern=value(data,"multiStepPattern",""),
            shaderValues=decodeShaderValues(value<Set<*>>(data,"shaderValues",emptySet<Any>())),
            globalPattern = BfiPattern(value(data, "visible", 1), value(data, "black", 1)),
            activationMode = ActivationMode.valueOf(value(data, "activation", ActivationMode.ALL_APPS.name)),
            includedPackages = packages(data, "included"), excludedPackages = packages(data, "excluded"), overrides = overrides.toMap(),
            refreshTarget = if (target == "maximum") RefreshTarget.Maximum else RefreshTarget.Numeric(target.toDouble()),
            requestRefresh = value(data, "request", false), alwaysForce = value(data, "force", false), resumeAfterUnlock = value(data, "resume", true),
            diagnosticsEnabled = value(data, "diagnostics", false), lastTestSeconds = value(data, "testSeconds", 30), acceptedNoticeVersion = value(data, "notice", 0)
        )
    }
}
