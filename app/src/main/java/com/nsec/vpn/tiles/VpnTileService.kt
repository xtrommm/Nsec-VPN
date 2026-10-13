package com.nsec.vpn.tiles

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nsec.vpn.MainActivity
import com.nsec.vpn.data.model.VpnState
import com.nsec.vpn.vpn.WireGuardTunnelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class VpnTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var listenJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listenJob?.cancel()
        listenJob = scope.launch {
            WireGuardTunnelManager.getInstance(this@VpnTileService).vpnState.collect { state ->
                updateTileState(state)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        listenJob?.cancel()
        listenJob = null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val tunnelManager = WireGuardTunnelManager.getInstance(this)
        when (tunnelManager.vpnState.value) {
            is VpnState.Connected -> {
                tunnelManager.disconnect()
            }
            is VpnState.Disconnected, is VpnState.Error -> {
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pendingIntent = PendingIntent.getActivity(
                        this, 0, intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    startActivityAndCollapse(pendingIntent)
                } else {
                    // Required compatibility API below Android 14; replacement is guarded above.
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
            }
            else -> {}
        }
    }

    private fun updateTileState(state: VpnState) {
        val tile = qsTile ?: return
        when (state) {
            is VpnState.Connected -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Nsec VPN: Protected"
            }
            is VpnState.Connecting -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Nsec VPN: Connecting"
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Nsec VPN: Off"
            }
        }
        tile.updateTile()
    }
}
