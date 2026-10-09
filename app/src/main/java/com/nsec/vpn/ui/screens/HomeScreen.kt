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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nsec.vpn.R
import com.nsec.vpn.data.model.Server
import com.nsec.vpn.data.model.VpnState
import com.nsec.vpn.data.model.VpnStats
import com.nsec.vpn.ui.components.AdBannerView
import com.nsec.vpn.ui.components.ConnectButton
import com.nsec.vpn.ui.components.LiveStatsBar
import com.nsec.vpn.ui.theme.*

@Composable
fun HomeScreen(
    vpnState: VpnState,
    vpnStats: VpnStats,
    selectedServer: Server?,
    userTier: String,
    onToggleVpn: () -> Unit,
    onNavigateToServers: () -> Unit,
    onUpgradeClicked: () -> Unit,
    announcementTitle: String? = null,
    announcementBody: String? = null,
    modifier: Modifier = Modifier
) {
    var announcementDismissed by remember { mutableStateOf(false) }
    val isConnected = vpnState is VpnState.Connected

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
                    "✕",
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    modifier = Modifier.clickable { announcementDismissed = true }.padding(4.dp)
                )
            }
        }

        // Top App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(LightSurface)
                        .border(1.dp, LightCardBorder, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.nsec_vpn_logo_1791489769518),
                        contentDescription = "Nsec VPN Logo",
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Nsec VPN",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "WireGuard • nsecvpn.com",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryBlue
                    )
                }
            }

            // User Tier Badge
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
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (userTier != "FREE") {
                        Icon(Icons.Default.Star, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (userTier == "FREE") "FREE" else "VIP ACTIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (userTier == "FREE") TextSecondary else AmberWarning
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Virtual IP Banner Card
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
                    text = if (isConnected) "10.8.0.2 (WireGuard Gateway)" else "192.168.1.1 (Standard ISP)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            Text(
                text = if (isConnected) "ENCRYPTED" else "UNSECURED",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isConnected) EmeraldGreen else CrimsonError
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Main Connect Power Button
        ConnectButton(
            vpnState = vpnState,
            onToggle = onToggleVpn
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Live Speed & Duration Stats Bar
        LiveStatsBar(stats = vpnStats)

        Spacer(modifier = Modifier.height(24.dp))

        // Selected Server Card Picker
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SERVER LOCATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.8.sp
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

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = LightSurface,
                border = BorderStroke(1.dp, LightCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToServers() }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = selectedServer?.flagEmoji ?: "🌐",
                            fontSize = 24.sp,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column {
                            Text(
                                text = selectedServer?.name ?: "Fastest Available Location",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${selectedServer?.city ?: "Auto"}, ${selectedServer?.countryName ?: "Global"} • ${selectedServer?.pingMs ?: 35}ms",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Select Server",
                        tint = TextTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Monetization Banner for Free Tier Users
        if (userTier == "FREE") {
            AdBannerView(onRemoveAdsClicked = onUpgradeClicked)
        }
    }
}
