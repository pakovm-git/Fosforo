package dev.pako.bfi.settings

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import dev.pako.bfi.model.BfiSettings
import kotlinx.coroutines.*

sealed interface SettingsStatus {
    data object Loading : SettingsStatus
    data class Ready(val settings: BfiSettings) : SettingsStatus
    data object Error : SettingsStatus
}

class SettingsRepository(context: Context, private val main: Handler) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var corruptionNeedsAcknowledgment=false
    private val store = PreferenceDataStoreFactory.create(scope = scope,corruptionHandler = ReplaceFileCorruptionHandler {
        corruptionNeedsAcknowledgment=true
        emptyPreferences() // Quarantine invalid bytes; explicit Reset is still required before readiness returns.
    }) { context.preferencesDataStoreFile("bfi-settings") }
    private var collection: Job? = null
    private var collectionGeneration=0L
    private val observers = linkedSetOf<Observer>()
    private class Observer(val action: () -> Unit) { var active = true }
    var status: SettingsStatus = SettingsStatus.Loading
        private set
    var writing = false
        private set
    private val commits=SettingsCommitQueue()
    val committedSettings get()=commits.committed
    val settings get() = (status as? SettingsStatus.Ready)?.settings

    init { reload() }
    private fun notifyChange() { observers.toList().forEach { if (it.active) it.action() } }
    fun subscribe(action: () -> Unit): () -> Unit {
        check(Looper.myLooper() == Looper.getMainLooper())
        val observer = Observer(action); observers.add(observer); action()
        return { observer.active = false; observers.remove(observer) }
    }
    fun reload() {
        if (writing) return
        status = SettingsStatus.Loading; notifyChange(); collection?.cancel()
        val generation=++collectionGeneration
        collection = scope.launch {
            try {
                store.data.collect { prefs ->
                    val decoded = try { if (corruptionNeedsAcknowledgment) SettingsStatus.Error else SettingsStatus.Ready(SettingsCodec.decode(prefs.asMap().mapKeys { it.key.name })) }
                    catch (_: IllegalArgumentException) { SettingsStatus.Error }
                    catch (_: IllegalStateException) { SettingsStatus.Error }
                    main.post { if (generation == collectionGeneration) { if (status is SettingsStatus.Loading) { status = decoded; commits.load((decoded as? SettingsStatus.Ready)?.settings); notifyChange() } } }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                main.post { if (generation == collectionGeneration) { status = SettingsStatus.Error; notifyChange() } }
            }
        }
    }
    /** All settings are committed in one transaction; no enabled intent is ever written. */
    fun save(settings: BfiSettings, completed: (Boolean) -> Unit) {
        if (status !is SettingsStatus.Ready) { completed(false); return }
        write(settings, completed)
    }
    fun reset(completed: (Boolean) -> Unit) {
        if (writing) { completed(false); return }
        write(BfiSettings(), completed, reset = true)
    }
    private fun write(settings: BfiSettings, completed: (Boolean) -> Unit, reset: Boolean = false) {
        try { SettingsCodec.decode(SettingsCodec.encode(settings)) } catch (_: Exception) { completed(false); return }
        commits.enqueue(settings,completed,reset)
        // Publish the latest valid draft so the next edit copies it, never an older disk snapshot.
        status=SettingsStatus.Ready(settings)
        if (writing) { notifyChange(); return }
        writing=true; notifyChange(); drain()
    }
    private fun drain() {
        val item=commits.begin()
        val settings=item.settings; val reset=item.reset
        scope.launch {
            val success = try {
                store.edit { prefs ->
                    if (!reset) SettingsCodec.decode(prefs.asMap().mapKeys { it.key.name })
                    prefs.clear()
                    for ((name, value) in SettingsCodec.encode(settings)) when (value) {
                        is Int -> prefs[intPreferencesKey(name)] = value
                        is Boolean -> prefs[booleanPreferencesKey(name)] = value
                        is String -> prefs[stringPreferencesKey(name)] = value
                        is Set<*> -> prefs[stringSetPreferencesKey(name)] = value.map { it as String }.toSet()
                    }
                }
                true
            } catch (e: Exception) { if (e is CancellationException) throw e; false }
            main.post {
                commits.complete(success)
                if (success && reset) corruptionNeedsAcknowledgment=false
                val more=commits.hasPending
                writing=more
                if (!more) status=committedSettings?.let { SettingsStatus.Ready(it) } ?: SettingsStatus.Error
                notifyChange(); item.completed(success)
                if (more) drain()
            }
        }
    }
}
