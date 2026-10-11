package dev.pako.bfi.model

enum class ActivationMode { ALL_APPS, SELECTED_APPS }
sealed interface RefreshTarget {
    data object Maximum : RefreshTarget
    data class Numeric(val hz: Double) : RefreshTarget { init { require(hz.isFinite() && hz > 0) } }
}
data class BfiSettings(
    val schemaVersion: Int = 1,
    val globalPattern: BfiPattern = BfiPattern(),
    val activationMode: ActivationMode = ActivationMode.ALL_APPS,
    val includedPackages: Set<String> = emptySet(),
    val excludedPackages: Set<String> = emptySet(),
    val overrides: Map<String, BfiPattern> = emptyMap(),
    val refreshTarget: RefreshTarget = RefreshTarget.Maximum,
    val requestRefresh: Boolean = false,
    val alwaysForce: Boolean = false,
    val resumeAfterUnlock: Boolean = true,
    val diagnosticsEnabled: Boolean = false,
    val lastTestSeconds: Int = 30,
    val acceptedNoticeVersion: Int = 0
) {
    init { require(schemaVersion == 1 && lastTestSeconds in 1..600 && acceptedNoticeVersion >= 0) }
    val needsAppIdentity get() = activationMode == ActivationMode.SELECTED_APPS || excludedPackages.isNotEmpty() || overrides.isNotEmpty()
}
