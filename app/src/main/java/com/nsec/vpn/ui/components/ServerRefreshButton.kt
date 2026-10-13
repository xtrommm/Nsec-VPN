package com.nsec.vpn.ui.components

import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

@Composable
fun ServerRefreshButton(refreshing: Boolean, onClick: () -> Unit) {
    val resumed = isUiResumed()
    val angle = if (refreshing && resumed) {
        rememberInfiniteTransition(label = "server_refresh").animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
            label = "server_refresh_rotation"
        )
    } else remember { mutableFloatStateOf(0f) }
    IconButton(onClick = onClick, enabled = !refreshing,
        modifier = Modifier.testTag("refresh_servers_button").semantics {
            stateDescription = if (refreshing) "Refreshing Servers" else "Ready To Refresh"
        }) {
        Icon(Icons.Default.Refresh, contentDescription = "Refresh Servers & Latencies",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.graphicsLayer { rotationZ = angle.value })
    }
}
