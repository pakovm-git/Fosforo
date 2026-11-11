package dev.pako.bfi.rules

import dev.pako.bfi.model.*
import dev.pako.bfi.state.Reason

data class Eligibility(val pattern: BfiPattern?, val reason: Reason = Reason.READY)
object AppRuleResolver {
    fun resolveOutput(settings:BfiSettings,pkg:String?,controlsVisible:Boolean,testing:Boolean,modern:Boolean):Eligibility = when {
        testing || (controlsVisible && modern) -> Eligibility(settings.globalPattern)
        controlsVisible -> Eligibility(null,Reason.CONTROLS)
        else -> resolve(settings,pkg)
    }
    fun resolve(settings: BfiSettings, pkg: String?): Eligibility = when {
        settings.needsAppIdentity && pkg == null -> Eligibility(null, Reason.UNKNOWN_APP)
        pkg in settings.excludedPackages -> Eligibility(null, Reason.EXCLUDED)
        settings.activationMode == ActivationMode.SELECTED_APPS && pkg !in settings.includedPackages -> Eligibility(null, Reason.EXCLUDED)
        else -> Eligibility(settings.overrides[pkg] ?: settings.globalPattern)
    }
}
