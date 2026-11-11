package dev.pako.bfi.state

import dev.pako.bfi.model.SessionMode

/** Pending saves and capture consent cannot outlive Stop or a timed session's deadline. */
data class StartRequest(val command:Long,val mode:SessionMode,val deadline:Long?=null) {
    fun accepts(currentCommand:Long,now:Long)=command==currentCommand && (deadline==null || now<deadline)
}
