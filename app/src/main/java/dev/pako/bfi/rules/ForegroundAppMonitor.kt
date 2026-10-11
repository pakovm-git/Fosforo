package dev.pako.bfi.rules

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.Process
import android.os.SystemClock
import java.util.concurrent.Executors
import kotlin.math.abs

class ForegroundAppMonitor(private val context: Context, private val main: Handler, private val result: (String?, Boolean) -> Unit) {
    companion object {
        fun hasAccess(context: Context): Boolean = context.getSystemService(AppOpsManager::class.java)
            .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }
    private val executor = Executors.newSingleThreadExecutor()
    private var generation = 0L
    private var running = false
    private var future: java.util.concurrent.Future<*>? = null
    // Resolver/cursor live exclusively on the executor.
    private var resolver = ForegroundEventResolver()
    private var cursor = 0L
    private var lastWall = 0L
    private var lastElapsed = 0L
    private var lastKeys = emptySet<String>()
    private val tick = Runnable { query(generation) }
    fun start() {
        if (running) return
        running = true; val token = ++generation
        executor.execute { resolver = ForegroundEventResolver(); cursor = 0; lastWall = 0; lastElapsed = 0; lastKeys = emptySet() }
        query(token)
    }
    fun stop() { running = false; generation++; main.removeCallbacks(tick); future?.cancel(false); future = null }
    fun close() { stop(); executor.shutdownNow() }
    private fun query(token: Long) {
        if (!running || token != generation) return
        future = executor.submit {
            val access = hasAccess(context)
            val wall = System.currentTimeMillis(); val elapsed = SystemClock.elapsedRealtime()
            var pkg: String? = null
            if (access) try {
                if (lastWall != 0L && abs((wall-lastWall) - (elapsed-lastElapsed)) > 5000) { resolver.clear(); cursor=0; lastKeys=emptySet() }
                val begin = if (cursor == 0L) wall - 300_000 else cursor
                val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(begin,wall)
                val item = UsageEvents.Event(); val parsed = ArrayList<AppEvent>(); val endingKeys=mutableSetOf<String>()
                while (events != null && events.hasNextEvent()) {
                    events.getNextEvent(item)
                    val key = "${item.timeStamp}|${item.packageName}|${item.className}|${item.eventType}"
                    if (item.timeStamp == wall) endingKeys.add(key)
                    if (item.timeStamp == cursor && key in lastKeys) continue
                    when (item.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> parsed.add(AppEvent(item.timeStamp,item.packageName ?: continue,item.className ?: "",true))
                        UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> parsed.add(AppEvent(item.timeStamp,item.packageName ?: continue,item.className ?: "",false))
                    }
                }
                resolver.consume(parsed); pkg = resolver.current(); cursor = wall; lastKeys = endingKeys
                lastWall=wall; lastElapsed=elapsed
            } catch (_: RuntimeException) { resolver.clear(); cursor=0; pkg=null }
            main.post {
                if (!running || token != generation) return@post
                result(pkg, access)
                if (running && token == generation) main.postDelayed(tick,500)
            }
        }
    }
}
