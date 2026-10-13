package com.nsec.vpn.ui.components

import com.nsec.vpn.data.model.VpnState

object DnsFeedback {
    fun saved(state: VpnState): String =
        if (state is VpnState.Connected || state is VpnState.Connecting)
            "DNS Saved. Applying Automatically."
        else "DNS Saved For Your Next Connection."

    fun status(state: VpnState, active: List<String>): String = when {
        state is VpnState.Connecting -> "Applying Connection Settings…"
        state is VpnState.Connected && active.isNotEmpty() -> "Active DNS: ${active.joinToString()}"
        state is VpnState.Connected -> "Waiting For Active DNS Settings."
        else -> "Applies On Your Next Connection."
    }
}
