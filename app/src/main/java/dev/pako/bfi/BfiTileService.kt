package dev.pako.bfi

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class BfiTileService : TileService() {
    private val runtime get() = (application as BfiApplication).runtime
    private var unsubscribe: (() -> Unit)? = null
    override fun onStartListening() { unsubscribe?.invoke(); unsubscribe=runtime.subscribe { updateTile() } }
    override fun onStopListening() { unsubscribe?.invoke(); unsubscribe=null }
    override fun onDestroy() { unsubscribe?.invoke(); super.onDestroy() }
    private fun updateTile() {
        val tile=qsTile ?: return; val state=runtime.state
        tile.state=if (state.userEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        val label=if (!state.userEnabled) getString(R.string.off)
            else if (state.settings.activationMode == dev.pako.bfi.model.ActivationMode.SELECTED_APPS) getString(R.string.tile_on_apps)
            else getString(R.string.on)
        val selected=state.userEnabled && state.settings.activationMode == dev.pako.bfi.model.ActivationMode.SELECTED_APPS
        tile.label=if (selected && Build.VERSION.SDK_INT >= 29) getString(R.string.tile_on_apps_prefix) else label
        tile.contentDescription=getString(R.string.app_name)+": "+label
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle=getString(if (selected) R.string.tile_apps_scope else R.string.app_name)
        tile.updateTile()
    }
    override fun onClick() {
        super.onClick()
        if (runtime.state.userEnabled) { runtime.stop(); return }
        if (isLocked || runtime.readiness() != null) { openDashboard(); return }
        runtime.start()
        if (!runtime.state.userEnabled) openDashboard()
    }
    private fun openDashboard() {
        val intent=Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this,30,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else {
            openLegacy(intent)
        }
    }
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // Only called on API26–33, where PendingIntent overload is unavailable.
    @Suppress("DEPRECATION")
    private fun openLegacy(intent: Intent) { startActivityAndCollapse(intent) }
}
