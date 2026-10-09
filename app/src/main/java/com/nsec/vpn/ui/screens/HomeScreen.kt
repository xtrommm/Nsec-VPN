package com.nsec.vpn.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nsec.vpn.data.model.Server
import com.nsec.vpn.data.model.VpnState
import com.nsec.vpn.ui.components.ConnectButton
import com.nsec.vpn.ui.components.LiveStatsBar
import com.nsec.vpn.ui.theme.*
import com.nsec.vpn.vpn.WireGuardTunnelManager

@Composable
fun HomeScreen(
    vpnState: VpnState,
    tunnelManager: WireGuardTunnelManager,
    selectedServer: Server?,
    userTier: String,
    announcementTitle: String? = null,
    announcementBody: String? = null,
    onToggleVpn: () -> Unit,
    onNavigateToServers: () -> Unit,
    onUpgradeClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = vpnState is VpnState.Connected
    val vpnStats by tunnelManager.vpnStats.collectAsStateWithLifecycle()
    var announcementDismissed by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!announcementDismissed && !announcementTitle.isNullOrEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryBlueContainer)
                    .border(1.dp, PrimaryBlue.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(announcementTitle, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    if (!announcementBody.isNullOrEmpty()) {
                        Text(announcementBody, fontSize = 11.sp, color = TextPrimary)
                    }
                }
                Text(
                    text = "DISMISS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    modifier = Modifier.clickable { announcementDismissed = true }.padding(4.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Nsec -VPN",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "MILITARY GRADE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = PrimaryBlue
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (userTier == "FREE") LightCardHighlight else AmberContainer,
                border = BorderStroke(
                    1.dp,
                    if (userTier == "FREE") LightCardBorder else AmberWarning.copy(alpha = 0.6f)
                ),
                modifier = Modifier.clickable { onUpgradeClicked() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (userTier == "FREE") TextSecondary else AmberWarning,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (userTier == "FREE") "FREE TIER" else "VIP ACTIVE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (userTier == "FREE") TextSecondary else AmberWarning
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(LightSurface)
                .border(1.dp, LightCardBorder, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) EmeraldGreen else CrimsonError)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isConnected) "VIRTUAL IP (PROTECTED)" else "PUBLIC IP (EXPOSED)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isConnected) EmeraldGreen else CrimsonError,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = if (isConnected) "10.8.0.2 • ${selectedServer?.city ?: "New York"}, ${selectedServer?.countryCode ?: "US"}"
                    else "198.51.100.22 • Unencrypted Connection",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }
            if (!isConnected) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Unencrypted",
                    tint = AmberWarning,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        ConnectButton(
            vpnState = vpnState,
            onToggle = onToggleVpn
        )

        Spacer(modifier = Modifier.height(36.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SELECTED SERVER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "CHANGE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue,
                    modifier = Modifier
                        .clickable { onNavigateToServers() }
                        .padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedServer != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(LightSurface)
                        .border(1.dp, LightCardBorder, RoundedCornerShape(14.dp))
                        .clickable { onNavigateToServers() }
                        .padding(14.dp)
                        .testTag("selected_server_card"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedServer.flagEmoji,
                        fontSize = 24.sp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${selectedServer.city}, ${selectedServer.countryName}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Ping: ${selectedServer.pingMs ?: 32}ms • Load: ${selectedServer.currentLoad}%",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Change Server",
                        tint = TextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        LiveStatsBar(stats = vpnStats)

        Spacer(modifier = Modifier.height(20.dp))
    }
}
