package dev.pako.bfi.settings

import dev.pako.bfi.model.BfiSettings

/** Main-thread queue: edits copy the newest draft; activation reads only committed settings. */
class SettingsCommitQueue {
    data class Write(val settings: BfiSettings,val completed: (Boolean) -> Unit,val reset: Boolean)
    private val pending=java.util.ArrayDeque<Write>()
    private var active: Write?=null
    var committed: BfiSettings?=null
        private set
    var latest: BfiSettings?=null
        private set
    val hasPending get()=pending.isNotEmpty()
    fun load(settings: BfiSettings?) {
        check(active == null && pending.isEmpty())
        committed=settings; latest=settings
    }
    fun enqueue(settings: BfiSettings,completed: (Boolean) -> Unit,reset: Boolean=false) {
        pending.addLast(Write(settings,completed,reset)); latest=settings
    }
    fun begin(): Write {
        check(active == null)
        return pending.removeFirst().also { active=it }
    }
    fun complete(success: Boolean): Write {
        val item=checkNotNull(active)
        if (success) committed=item.settings
        active=null
        latest=pending.peekLast()?.settings ?: committed
        return item
    }
}
