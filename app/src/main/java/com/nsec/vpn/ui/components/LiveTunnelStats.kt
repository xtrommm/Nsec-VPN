package com.nsec.vpn.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nsec.vpn.data.model.VpnStats
import kotlinx.coroutines.flow.StateFlow

/** Keep per-second statistics outside the activity and inactive tabs. */
@Composable
fun LiveTunnelStats(stats: StateFlow<VpnStats>, content: @Composable (VpnStats) -> Unit) {
    val snapshot by stats.collectAsStateWithLifecycle()
    content(snapshot)
}
