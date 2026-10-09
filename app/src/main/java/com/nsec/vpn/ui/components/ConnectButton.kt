package com.nsec.vpn.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
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
    modifier: Modifier = Modifier
) {
    val isConnecting = vpnState is VpnState.Connecting
    val isConnected = vpnState is VpnState.Connected

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnecting || isConnected) 1.14f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isConnecting) 700 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val buttonColor by animateColorAsState(
        targetValue = when (vpnState) {
            is VpnState.Connected -> EmeraldGreen
            is VpnState.Connecting -> AmberWarning
            is VpnState.Disconnecting -> CrimsonError
            is VpnState.Error -> CrimsonError
            is VpnState.Disconnected -> PrimaryBlue
        },
        animationSpec = tween(500),
        label = "buttonColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(220.dp)
        ) {
            // Outermost Ripple Ring
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(buttonColor.copy(alpha = if (isConnected) 0.08f else 0.04f))
            )

            // Middle Ripple Ring
            Box(
                modifier = Modifier
                    .size(175.dp)
                    .scale(pulseScale * 0.96f)
                    .clip(CircleShape)
                    .background(buttonColor.copy(alpha = if (isConnected) 0.16f else 0.08f))
            )

            // Main Core Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(150.dp)
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
                                Color.White,
                                Color(0xFFF8FAFC)
                            )
                        )
                    )
                    .border(2.dp, buttonColor.copy(alpha = 0.25f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggle
                    )
                    .testTag("vpn_toggle_button")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "VPN Power Switch",
                        tint = buttonColor,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (vpnState) {
                            is VpnState.Connected -> "PROTECTED"
                            is VpnState.Connecting -> "CONNECTING"
                            is VpnState.Disconnecting -> "STOPPING"
                            is VpnState.Error -> "RETRY"
                            is VpnState.Disconnected -> "TAP TO CONNECT"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = buttonColor,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }
    }
}
