package com.example.tiles

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.MainActivity
import com.example.data.model.VpnState
import com.example.vpn.WireGuardTunnelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class VpnTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val tunnelManager = WireGuardTunnelManager.getInstance(this)
        when (tunnelManager.vpnState.value) {
            is VpnState.Connected -> {
                tunnelManager.disconnect()
            }
            is VpnState.Disconnected -> {
                // Launch MainActivity to initiate connection with server & permissions
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivityAndCollapse(intent)
            }
            else -> {}
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val tunnelManager = WireGuardTunnelManager.getInstance(this)
        val state = tunnelManager.vpnState.value

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
