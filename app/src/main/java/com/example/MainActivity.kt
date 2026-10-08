package com.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.network.ServiceLocator
import com.example.data.model.VpnState
import com.example.ui.navigation.Screen
import com.example.ui.screens.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val serviceLocator = try {
            (application as? NsecVpnApp)?.serviceLocator ?: ServiceLocator.getInstance(this)
        } catch (_: Throwable) {
            ServiceLocator.getInstance(this)
        }
        val vpnRepo = serviceLocator.vpnRepository
        val tunnelManager = serviceLocator.tunnelManager

        setContent {
            NsecVpnTheme {
                val coroutineScope = rememberCoroutineScope()

                // State flows
                val vpnState by tunnelManager.vpnState.collectAsStateWithLifecycle()
                val vpnStats by tunnelManager.vpnStats.collectAsStateWithLifecycle()
                val servers by vpnRepo.servers.collectAsStateWithLifecycle()
                val selectedServer by vpnRepo.selectedServer.collectAsStateWithLifecycle()
                val userTier by vpnRepo.userTier.collectAsStateWithLifecycle()
                val selectedDns by vpnRepo.selectedDns.collectAsStateWithLifecycle()
                val killSwitchEnabled by vpnRepo.killSwitchEnabled.collectAsStateWithLifecycle()
                val splitTunnelingEnabled by vpnRepo.splitTunnelingEnabled.collectAsStateWithLifecycle()
                val excludedPackages by vpnRepo.excludedPackages.collectAsStateWithLifecycle()

                // Navigation State
                var currentTab by remember { mutableStateOf<Screen>(Screen.Home) }
                var activeSubScreen by remember { mutableStateOf<String?>(null) } // "split_tunnel", "troubleshoot", "legal"

                // Dialog States
                var showVipModal by remember { mutableStateOf(false) }
                var showPromoDialog by remember { mutableStateOf(false) }
                var promoCodeInput by remember { mutableStateOf("") }

                // VPN Permission Launcher
                val vpnPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val server = selectedServer ?: servers.firstOrNull()
                        if (server != null) {
                            tunnelManager.startConnect(
                                server = server,
                                dnsServers = listOf(selectedDns.primary, selectedDns.secondary),
                                excludedApps = if (splitTunnelingEnabled) excludedPackages else emptySet(),
                                onNeedsPermission = {}
                            )
                        }
                    } else {
                        Toast.makeText(this, "VPN permission is required to secure your connection.", Toast.LENGTH_SHORT).show()
                    }
                }

                fun handleToggleVpn() {
                    when (vpnState) {
                        is VpnState.Connected, is VpnState.Connecting -> {
                            tunnelManager.disconnect()
                        }
                        is VpnState.Disconnected, is VpnState.Error -> {
                            val server = selectedServer ?: servers.firstOrNull()
                            if (server != null) {
                                tunnelManager.startConnect(
                                    server = server,
                                    dnsServers = listOf(selectedDns.primary, selectedDns.secondary),
                                    excludedApps = if (splitTunnelingEnabled) excludedPackages else emptySet(),
                                    onNeedsPermission = { intent ->
                                        vpnPermissionLauncher.launch(intent)
                                    }
                                )
                            }
                        }
                        else -> {}
                    }
                }

                // Handle system back button for sub screens
                if (activeSubScreen != null) {
                    BackHandler {
                        activeSubScreen = null
                    }
                }

                Scaffold(
                    bottomBar = {
                        if (activeSubScreen == null) {
                            NavigationBar(
                                containerColor = ObsidianSurface,
                                tonalElevation = 8.dp
                            ) {
                                listOf(Screen.Home, Screen.Servers, Screen.SpeedTest, Screen.Settings).forEach { screen ->
                                    val isSelected = currentTab == screen
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = { currentTab = screen },
                                        icon = {
                                            Icon(
                                                imageVector = screen.icon,
                                                contentDescription = screen.title
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = screen.title,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = CyberCyan,
                                            selectedTextColor = CyberCyan,
                                            unselectedIconColor = TextSecondary,
                                            unselectedTextColor = TextSecondary,
                                            indicatorColor = ObsidianCardHighlight
                                        ),
                                        modifier = Modifier.testTag("nav_${screen.route}")
                                    )
                                }
                            }
                        }
                    },
                    contentWindowInsets = WindowInsets.safeDrawing,
                    containerColor = ObsidianDark
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (activeSubScreen) {
                            "split_tunnel" -> {
                                SplitTunnelScreen(
                                    excludedPackages = excludedPackages,
                                    onTogglePackage = { vpnRepo.togglePackageExclusion(it) },
                                    onBack = { activeSubScreen = null }
                                )
                            }
                            "troubleshoot" -> {
                                TroubleshootScreen(
                                    onBack = { activeSubScreen = null }
                                )
                            }
                            "legal" -> {
                                LegalScreen(
                                    onBack = { activeSubScreen = null }
                                )
                            }
                            else -> {
                                when (currentTab) {
                                    Screen.Home -> {
                                        HomeScreen(
                                            vpnState = vpnState,
                                            vpnStats = vpnStats,
                                            selectedServer = selectedServer,
                                            userTier = userTier,
                                            onToggleVpn = { handleToggleVpn() },
                                            onNavigateToServers = { currentTab = Screen.Servers },
                                            onUpgradeClicked = { showVipModal = true }
                                        )
                                    }
                                    Screen.Servers -> {
                                        ServerListScreen(
                                            servers = servers,
                                            selectedServer = selectedServer,
                                            userTier = userTier,
                                            onSelectServer = { server ->
                                                vpnRepo.selectServer(server)
                                                currentTab = Screen.Home
                                            },
                                            onAutoSelectBest = {
                                                vpnRepo.selectBestServer()
                                                currentTab = Screen.Home
                                            },
                                            onToggleFavorite = { vpnRepo.toggleFavorite(it) },
                                            onRefreshPings = {
                                                coroutineScope.launch { vpnRepo.measurePings() }
                                            },
                                            onUpgradeRequired = { showVipModal = true }
                                        )
                                    }
                                    Screen.SpeedTest -> {
                                        SpeedTestScreen(currentServer = selectedServer)
                                    }
                                    Screen.Settings -> {
                                        SettingsScreen(
                                            killSwitchEnabled = killSwitchEnabled,
                                            onToggleKillSwitch = { vpnRepo.setKillSwitch(it) },
                                            selectedDns = selectedDns,
                                            onSelectDns = { vpnRepo.setDns(it) },
                                            splitTunnelingEnabled = splitTunnelingEnabled,
                                            onToggleSplitTunneling = { vpnRepo.setSplitTunneling(it) },
                                            onOpenSplitTunnelApps = { activeSubScreen = "split_tunnel" },
                                            onOpenTroubleshooting = { activeSubScreen = "troubleshoot" },
                                            onOpenPrivacyPolicy = { activeSubScreen = "legal" },
                                            onRedeemPromoCode = { showPromoDialog = true }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // VIP Upgrade Bottom Sheet / Modal
                if (showVipModal) {
                    AlertDialog(
                        onDismissRequest = { showVipModal = false },
                        containerColor = ObsidianSurface,
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = AmberWarning)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Upgrade to Nsec VPN VIP", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "Enjoy zero ads, unlimited 10Gbps WireGuard nodes, bypass all streaming geo-blocks, and priority routing.",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )

                                // Yearly Plan Card
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ObsidianCard,
                                    border = BorderStroke(1.dp, CyberCyan),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showVipModal = false
                                            vpnRepo.grantRewardedPass()
                                            Toast.makeText(this@MainActivity, "Yearly VIP Activated! Thank you.", Toast.LENGTH_LONG).show()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Yearly VIP Plan", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    "50% OFF",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = NeonEmerald,
                                                    modifier = Modifier.background(NeonEmerald.copy(alpha = 0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text("$29.99 / year ($2.50/mo)", fontSize = 11.sp, color = TextSecondary)
                                        }
                                        Icon(Icons.Default.Star, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                                    }
                                }

                                // Monthly Plan Card
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ObsidianCard,
                                    border = BorderStroke(1.dp, ObsidianCardHighlight),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showVipModal = false
                                            vpnRepo.grantRewardedPass()
                                            Toast.makeText(this@MainActivity, "Monthly VIP Activated!", Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Monthly VIP Plan", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                            Text("$4.99 / month • Cancel anytime", fontSize = 11.sp, color = TextSecondary)
                                        }
                                    }
                                }

                                // Rewarded Video Free Pass
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ObsidianCardHighlight,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showVipModal = false
                                            vpnRepo.grantRewardedPass()
                                            Toast.makeText(this@MainActivity, "Rewarded Ad Watched: 30-Minute VIP Pass Granted!", Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Watch Rewarded Ad for 30m Free VIP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                                        Text("Instantly unlocks all high-speed servers for 30 minutes without credit card.", fontSize = 10.sp, color = TextTertiary)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showVipModal = false }) {
                                Text("Close", color = TextSecondary)
                            }
                        }
                    )
                }

                // Promo Code Dialog
                if (showPromoDialog) {
                    AlertDialog(
                        onDismissRequest = { showPromoDialog = false },
                        containerColor = ObsidianSurface,
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = CyberCyan)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Redeem Promo Code", color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Enter your invite or promotional code to unlock Premium access.", color = TextSecondary, fontSize = 13.sp)
                                OutlinedTextField(
                                    value = promoCodeInput,
                                    onValueChange = { promoCodeInput = it },
                                    placeholder = { Text("e.g. VIP2026", color = TextTertiary) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = ObsidianCard,
                                        unfocusedContainerColor = ObsidianCard,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showPromoDialog = false
                                    if (promoCodeInput.trim().isNotEmpty()) {
                                        vpnRepo.grantRewardedPass()
                                        Toast.makeText(this@MainActivity, "Code redeemed: 7 Days VIP Unlocked!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                            ) {
                                Text("Redeem")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPromoDialog = false }) {
                                Text("Cancel", color = TextSecondary)
                            }
                        }
                    )
                }
            }
        }
    }
}
