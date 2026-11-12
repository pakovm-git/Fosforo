package dev.pako.bfi.state

/** Shared activation barrier; granting one permission never substitutes for the other. */
internal fun outputPermissionProblem(modern:Boolean,accessibility:Boolean,overlay:Boolean,notifications:Boolean):Reason? = when {
    modern && !accessibility -> Reason.ACCESSIBILITY
    !modern && !overlay -> Reason.OVERLAY
    !notifications -> Reason.NOTIFICATION
    else -> null
}
