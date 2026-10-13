package com.nsec.vpn.ui.components

import com.nsec.vpn.data.model.VpnState

object ConnectionPresentation {
    fun status(state: VpnState): String = when (state) {
        is VpnState.Connected -> "Connected"
        is VpnState.Connecting -> "Connecting"
        is VpnState.Disconnecting -> "Disconnecting"
        is VpnState.Disconnected -> "Disconnected"
        is VpnState.Error -> "Connection Error"
    }

    fun homeTitle(state: VpnState): String = when (state) {
        is VpnState.Connected -> "SECURE TUNNEL (ACTIVE)"
        is VpnState.Connecting -> "VPN CONNECTING"
        is VpnState.Disconnecting -> "VPN DISCONNECTING"
        is VpnState.Disconnected -> "VPN NOT CONNECTED"
        is VpnState.Error -> "VPN CONNECTION ERROR"
    }

    fun homeDetail(state: VpnState, location: String): String = when (state) {
        is VpnState.Connected -> "$location • WireGuard® Secured"
        is VpnState.Connecting -> "Establishing The Secure Tunnel"
        is VpnState.Disconnecting -> "Stopping The Tunnel • Please Wait"
        is VpnState.Disconnected -> "Direct ISP Connection • UNENCRYPTED"
        is VpnState.Error -> "Check Connection Status Before Retrying"
    }

    fun canToggle(state: VpnState): Boolean = state !is VpnState.Disconnecting
}
