package com.nsec.vpn.tiles

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nsec.vpn.core.network.ServiceLocator
import com.nsec.vpn.data.model.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class VpnTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var stateJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val locator = ServiceLocator.getInstance(this)
        stateJob?.cancel()
        stateJob = scope.launch {
            locator.tunnelManager.vpnState.collect { state ->
                updateTileState(state)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        stateJob?.cancel()
    }

    override fun onClick() {
        super.onClick()
        val locator = ServiceLocator.getInstance(this)
        val currentState = locator.tunnelManager.vpnState.value
        val repo = locator.vpnRepository

        if (currentState is VpnState.Connected || currentState is VpnState.Connecting) {
            locator.tunnelManager.disconnect()
        } else {
            val server = repo.selectedServer.value ?: repo.servers.value.firstOrNull()
            if (server != null) {
                locator.tunnelManager.startConnect(server = server, onNeedsPermission = {})
            }
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
