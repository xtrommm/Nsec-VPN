package com.nsec.vpn.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.nsec.vpn.data.model.VpnState
import com.nsec.vpn.ui.theme.*

@Composable
fun ConnectButton(
    vpnState: VpnState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    animationsEnabled: Boolean = true
) {
    val pulseScale = if (animationsEnabled && (vpnState is VpnState.Connected || vpnState is VpnState.Connecting)) {
        val transition = rememberInfiniteTransition(label = "pulseTransition")
        val scale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulseScale"
        )
        scale
    } else 1f

    // State feedback is immediate; a 500ms colour tween looked like delayed input.
    val buttonColor = when (vpnState) {
        is VpnState.Connected -> EmeraldGreen
        is VpnState.Connecting -> AmberWarning
        is VpnState.Disconnecting, is VpnState.Error -> CrimsonError
        is VpnState.Disconnected -> PrimaryBlue
    }

    val labelText = when (vpnState) {
        is VpnState.Connected -> "PROTECTED"
        is VpnState.Connecting -> "CONNECTING..."
        is VpnState.Disconnecting -> "DISCONNECTING..."
        is VpnState.Error -> "TAP TO RETRY"
        is VpnState.Disconnected -> "TAP TO CONNECT"
    }

    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(if (compact) 170.dp else 220.dp)
        ) {
            if (vpnState is VpnState.Connected || vpnState is VpnState.Connecting) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 148.dp else 200.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(buttonColor.copy(alpha = 0.15f))
                )
            }

            Box(
                modifier = Modifier
                    .size(if (compact) 144.dp else 175.dp)
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

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (compact) 124.dp else 150.dp)
                    .shadow(
                        elevation = 14.dp,
                        shape = CircleShape,
                        spotColor = buttonColor.copy(alpha = 0.35f),
                        ambientColor = Color.Black.copy(alpha = 0.08f)
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                surfaceColor,
                                surfaceColor
                            )
                        )
                    )
                    .border(2.dp, buttonColor.copy(alpha = 0.35f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = ConnectionPresentation.canToggle(vpnState),
                        onClick = onToggle
                    )
                    .testTag("vpn_connect_button")
            ) {
                if (vpnState is VpnState.Connecting || vpnState is VpnState.Disconnecting) {
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
                    modifier = Modifier.size(if (compact) 46.dp else 54.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(if (compact) 6.dp else 16.dp))

        Text(
            text = labelText,
            color = buttonColor,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 1.5.sp
        )
    }
}
