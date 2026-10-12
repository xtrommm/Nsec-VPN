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
import com.nsec.vpn.data.model.Server
import com.nsec.vpn.data.model.VpnState
import com.nsec.vpn.data.monetization.AdMobConfig
import com.nsec.vpn.data.monetization.NativeAdManager
import com.nsec.vpn.ui.components.ConnectButton
import com.nsec.vpn.ui.components.SmartNativeAd
import com.nsec.vpn.ui.theme.*
import com.nsec.vpn.vpn.WireGuardTunnelManager

@Composable
fun HomeScreen(
    vpnState: VpnState,
    tunnelManager: WireGuardTunnelManager,
    selectedServer: Server?,
    userTier: String,
    nativeAdManager: NativeAdManager,
    onToggleVpn: () -> Unit,
    onNavigateToServers: () -> Unit,
    onUpgradeClicked: () -> Unit,
    modifier: Modifier = Modifier,
    adMobConfig: AdMobConfig = AdMobConfig(),
    announcementTitle: String? = null,
    announcementBody: String? = null,
    onNavigateToConnected: () -> Unit = {}
) {
    val isConnected = vpnState is VpnState.Connected
    val connectedNode by tunnelManager.connectedNode.collectAsState()
    val serverListError by com.nsec.vpn.core.network.ServiceLocator.getInstance(androidx.compose.ui.platform.LocalContext.current).vpnRepository.serverListError.collectAsState()
    var announcementDismissed by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        serverListError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (!announcementDismissed && !announcementTitle.isNullOrEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(1.dp, PrimaryBlue.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = announcementTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (!announcementBody.isNullOrEmpty()) {
                        Text(
                            text = announcementBody,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                        )
                    }
                }
                Text(
                    text = "DISMISS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "MILITARY GRADE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (userTier == "FREE") MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.amberBadgeContainer,
                border = BorderStroke(
                    1.dp,
                    if (userTier == "FREE") MaterialTheme.colorScheme.outline else AmberWarning.copy(alpha = 0.6f)
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
                        tint = if (userTier == "FREE") MaterialTheme.colorScheme.onSurfaceVariant else AmberWarning,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (userTier == "FREE") "FREE TIER" else "VIP ACTIVE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (userTier == "FREE") MaterialTheme.colorScheme.onSurfaceVariant else AmberWarning
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .then(
                    if (isConnected) Modifier.clickable { onNavigateToConnected() } else Modifier
                )
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
                    text = if (isConnected) "SECURE TUNNEL (ACTIVE)" else "UNPROTECTED NETWORK",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isConnected) EmeraldGreen else CrimsonError,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = if (isConnected) "${connectedNode?.locationName.orEmpty()} • WireGuard® Secured"
                    else "Direct ISP Connection • UNENCRYPTED",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (isConnected) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "View Connection Details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            } else {
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "CHANGE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
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
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
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
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Ping: ${selectedServer.pingMs?.let { "${it}ms" } ?: "Unavailable"} • Load: ${selectedServer.currentLoad}%",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Change Server",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        SmartNativeAd(
            nativeAdManager = nativeAdManager,
            adMobConfig = adMobConfig,
            userTier = userTier,
            vpnState = vpnState,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}
