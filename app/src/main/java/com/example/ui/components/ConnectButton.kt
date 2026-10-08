package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VpnState
import com.example.ui.theme.*

@Composable
fun ConnectButton(
    vpnState: VpnState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")

    // Pulse animation when connecting or connected
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (vpnState is VpnState.Connected || vpnState is VpnState.Connecting) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val buttonColor by animateColorAsState(
        targetValue = when (vpnState) {
            is VpnState.Connected -> NeonEmerald
            is VpnState.Connecting -> AmberWarning
            is VpnState.Disconnecting -> CrimsonError
            is VpnState.Error -> CrimsonError
            is VpnState.Disconnected -> ElectricBlue
        },
        animationSpec = tween(500),
        label = "buttonColor"
    )

    val labelText = when (vpnState) {
        is VpnState.Connected -> "PROTECTED"
        is VpnState.Connecting -> "CONNECTING..."
        is VpnState.Disconnecting -> "DISCONNECTING..."
        is VpnState.Error -> "TAP TO RETRY"
        is VpnState.Disconnected -> "TAP TO CONNECT"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(220.dp)
        ) {
            // Outer Glowing Halo
            if (vpnState is VpnState.Connected || vpnState is VpnState.Connecting) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(buttonColor.copy(alpha = 0.15f))
                )
            }

            // Secondary Ring
            Box(
                modifier = Modifier
                    .size(175.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                buttonColor.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Main Core Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(150.dp)
                    .shadow(elevation = 16.dp, shape = CircleShape, spotColor = buttonColor)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                ObsidianCardHighlight,
                                ObsidianSurface
                            )
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggle
                    )
                    .testTag("vpn_connect_button")
            ) {
                if (vpnState is VpnState.Connecting) {
                    CircularProgressIndicator(
                        color = buttonColor,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(72.dp)
                    )
                }

                Icon(
                    imageVector = when (vpnState) {
                        is VpnState.Connected -> Icons.Default.Security
                        is VpnState.Error -> Icons.Default.Refresh
                        else -> Icons.Default.PowerSettingsNew
                    },
                    contentDescription = labelText,
                    tint = buttonColor,
                    modifier = Modifier.size(54.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = labelText,
            color = buttonColor,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 1.5.sp
        )
    }
}
