package com.nsec.vpn.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nsec.vpn.data.model.Server
import com.nsec.vpn.data.model.VpnState
import com.nsec.vpn.data.model.VpnStats
import com.nsec.vpn.ui.theme.*
import com.nsec.vpn.util.SpeedFormatter

@Composable
fun ConnectedScreen(
    vpnState: VpnState,
    vpnStats: VpnStats,
    selectedServer: Server?,
    userTier: String,
    onDisconnect: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
    connectedNode: com.nsec.vpn.data.model.TunnelConfig? = null,
    nativeAdContent: @Composable () -> Unit = {}
) {
    BackHandler {
        onBackToHome()
    }

    val durationText = SpeedFormatter.formatTimer(vpnStats.connectionDurationSeconds)
    val downloadSpeedText = SpeedFormatter.formatSpeed(vpnStats.downloadSpeedMbps)
    val uploadSpeedText = SpeedFormatter.formatSpeed(vpnStats.uploadSpeedMbps)

    val infiniteTransition = rememberInfiniteTransition(label = "ring_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val activeColor = EmeraldGreen

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("connected_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (vpnState is VpnState.Connected && connectedNode != null) {
            Text(connectedNode.nodeName.orEmpty(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(connectedNode.locationName.orEmpty(), fontSize = 12.sp)
            Text("VPN Server IP: ${connectedNode.publicIpv4.orEmpty()}", fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp).testTag("vpn_server_ip"))
        }

        // Top Navigation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBackToHome,
                modifier = Modifier.testTag("connected_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back To Home",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Text(
                text = if (vpnState is VpnState.Connected) "VPN CONNECTED" else "CONNECTING",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (userTier == "FREE") MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.amberBadgeContainer,
                border = BorderStroke(
                    1.dp,
                    if (userTier == "FREE") MaterialTheme.colorScheme.outline else AmberWarning.copy(alpha = 0.6f)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (userTier == "FREE") MaterialTheme.colorScheme.onSurfaceVariant else AmberWarning,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (userTier == "FREE") "FREE" else "VIP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (userTier == "FREE") MaterialTheme.colorScheme.onSurfaceVariant else AmberWarning
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selected Server Location Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedServer?.flagEmoji.orEmpty(),
                fontSize = 24.sp,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${connectedNode?.locationName.orEmpty()}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${if (vpnState is VpnState.Connected) "Connected" else "Connecting"} • Ping: ${selectedServer?.pingMs?.let { "${it}ms" } ?: "Unavailable"}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(EmeraldGreen)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large Circular Timer Box
        Box(
            modifier = Modifier
                .size(170.dp)
                .semantics {
                    contentDescription = "Connection Duration: $durationText"
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 10.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2

                // Background track ring
                drawCircle(
                    color = trackColor,
                    radius = radius,
                    style = Stroke(width = strokeWidth)
                )

                // Foreground glowing ring
                drawCircle(
                    color = activeColor.copy(alpha = glowAlpha),
                    radius = radius,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "CONNECTED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = EmeraldGreen
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = durationText,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("connection_timer_text")
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "WireGuard® Tunnel",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Speed Stats Row (Below Circle)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Download Speed Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Download Speed",
                            tint = EmeraldGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DOWNLOAD",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = downloadSpeedText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("download_speed_text")
                    )
                }
            }

            // Upload Speed Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Upload Speed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "UPLOAD",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = uploadSpeedText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("upload_speed_text")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Disconnect Button
        Button(
            onClick = onDisconnect,
            colors = ButtonDefaults.buttonColors(
                containerColor = CrimsonError,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("disconnect_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DISCONNECT VPN",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onBackToHome,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(
                text = "Keep Connected & Back To Home",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (vpnState is VpnState.Connected) {
            Spacer(modifier = Modifier.height(16.dp))
            nativeAdContent()
        }
    }
}
