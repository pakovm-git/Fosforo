package dev.pako.bfi.rules

/** A bounded historical-event reconstruction. Ambiguity fails closed; an empty delta preserves a known resume. */
data class AppEvent(val at: Long, val pkg: String, val activity: String, val resumed: Boolean)
class ForegroundEventResolver {
    private val resumed = mutableSetOf<Pair<String, String>>()
    private var overflowed=false
    fun clear() { resumed.clear(); overflowed=false }
    fun consume(events: List<AppEvent>) {
        for (event in events.sortedBy { it.at }) {
            if (overflowed) break
            val key = event.pkg to event.activity
            if (event.resumed) resumed.add(key) else resumed.remove(key)
            if (resumed.size > 64) { resumed.clear(); overflowed=true }
        }
    }
    fun current(): String? = if (overflowed) null else resumed.map { it.first }.distinct().singleOrNull()
}
