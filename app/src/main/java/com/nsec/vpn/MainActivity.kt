package com.nsec.vpn

import android.Manifest
import android.app.Activity
import android.os.Build
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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nsec.vpn.core.network.ServiceLocator
import com.nsec.vpn.data.model.VpnState
import com.nsec.vpn.ui.navigation.Screen
import com.nsec.vpn.ui.screens.*
import com.nsec.vpn.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val serviceLocator = try {
            (application as? NsecVpnApp)?.serviceLocator ?: ServiceLocator.getInstance(this)
        } catch (_: Throwable) {
            ServiceLocator.getInstance(this)
        }
        val vpnRepo = serviceLocator.vpnRepository
        val tunnelManager = serviceLocator.tunnelManager
        val adMobManager = serviceLocator.adMobManager
        val remoteConfigManager = serviceLocator.remoteConfigManager
        val playBillingManager = serviceLocator.playBillingManager

        setContent {
            val themeMode by vpnRepo.themeMode.collectAsStateWithLifecycle()
            val adMobConfig by adMobManager.config.collectAsStateWithLifecycle()
            val remoteConfig by remoteConfigManager.config.collectAsStateWithLifecycle()

            NsecVpnTheme(themeMode = themeMode) {
                val coroutineScope = rememberCoroutineScope()

                val vpnState by tunnelManager.vpnState.collectAsStateWithLifecycle()
                val vpnStats by tunnelManager.vpnStats.collectAsStateWithLifecycle()
                val servers by vpnRepo.servers.collectAsStateWithLifecycle()
                val selectedServer by vpnRepo.selectedServer.collectAsStateWithLifecycle()
                val userTier by vpnRepo.userTier.collectAsStateWithLifecycle()
                val selectedDns by vpnRepo.selectedDns.collectAsStateWithLifecycle()
                val killSwitchEnabled by vpnRepo.killSwitchEnabled.collectAsStateWithLifecycle()
                val splitTunnelingEnabled by vpnRepo.splitTunnelingEnabled.collectAsStateWithLifecycle()
                val autoConnectEnabled by vpnRepo.autoConnectEnabled.collectAsStateWithLifecycle()
                val excludedPackages by vpnRepo.excludedPackages.collectAsStateWithLifecycle()

                var currentTab by remember { mutableStateOf<Screen>(Screen.Home) }
                var activeSubScreen by remember { mutableStateOf<String?>(null) }
                var isUserInitiatedConnect by remember { mutableStateOf(false) }

                var showVpnDisclosureDialog by remember { mutableStateOf(false) }
                var pendingUserInitiatedConnect by remember { mutableStateOf(false) }

                var showVipModal by remember { mutableStateOf(false) }
                var showPromoDialog by remember { mutableStateOf(false) }
                var promoCodeInput by remember { mutableStateOf("") }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { _ -> }

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

                fun startConnectInternal(isUserInitiated: Boolean) {
                    isUserInitiatedConnect = isUserInitiated
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

                fun triggerConnectSequence(isUserInitiated: Boolean = true) {
                    when (vpnState) {
                        is VpnState.Connected, is VpnState.Connecting -> {
                            tunnelManager.disconnect()
                        }
                        is VpnState.Disconnected, is VpnState.Error -> {
                            if (!vpnRepo.isVpnDisclosureAccepted()) {
                                pendingUserInitiatedConnect = isUserInitiated
                                showVpnDisclosureDialog = true
                            } else {
                                startConnectInternal(isUserInitiated)
                            }
                        }
                        else -> {}
                    }
                }

                LaunchedEffect(Unit) {
                    remoteConfigManager.fetchRemoteConfig()
                    vpnRepo.refreshServers()
                    adMobManager.preloadInterstitial(this@MainActivity)
                    adMobManager.preloadRewarded(this@MainActivity)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    vpnRepo.checkRewardedPassExpiry()
                    if (autoConnectEnabled && vpnState is VpnState.Disconnected) {
                        triggerConnectSequence(isUserInitiated = false)
                    }
                }

                LaunchedEffect(currentTab) {
                    if (currentTab == Screen.Servers) {
                        vpnRepo.refreshServers()
                    }
                }

                LaunchedEffect(vpnState) {
                    when (vpnState) {
                        is VpnState.Connected -> {
                            if (isUserInitiatedConnect) {
                                isUserInitiatedConnect = false
                                val isPremium = userTier != "FREE"
                                val adAllowed = !isPremium && adMobConfig.interstitialEnabled && adMobManager.canShowInterstitial() && adMobManager.isInterstitialReady()
                                if (adAllowed) {
                                    adMobManager.showInterstitial(this@MainActivity) {
                                        activeSubScreen = "connected"
                                    }
                                } else {
                                    activeSubScreen = "connected"
                                }
                            }
                        }
                        is VpnState.Disconnected, is VpnState.Error -> {
                            if (activeSubScreen == "connected") {
                                activeSubScreen = null
                            }
                        }
                        else -> {}
                    }
                }

                BackHandler(enabled = activeSubScreen != null || currentTab != Screen.Home) {
                    if (activeSubScreen != null) {
                        activeSubScreen = null
                    } else if (currentTab != Screen.Home) {
                        currentTab = Screen.Home
                    }
                }

                Scaffold(
                    bottomBar = {
                        if (activeSubScreen == null) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 6.dp
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
                                            selectedIconColor = PrimaryBlue,
                                            selectedTextColor = PrimaryBlue,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                        ),
                                        modifier = Modifier.testTag("nav_${screen.route}")
                                    )
                                }
                            }
                        }
                    },
                    contentWindowInsets = WindowInsets.safeDrawing,
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (activeSubScreen) {
                            "connected" -> {
                                ConnectedScreen(
                                    vpnState = vpnState,
                                    vpnStats = vpnStats,
                                    selectedServer = selectedServer,
                                    userTier = userTier,
                                    onDisconnect = { tunnelManager.disconnect() },
                                    onBackToHome = { activeSubScreen = null }
                                )
                            }
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
                                            tunnelManager = tunnelManager,
                                            selectedServer = selectedServer,
                                            userTier = userTier,
                                            adMobConfig = adMobConfig,
                                            nativeAdManager = adMobManager.nativeAdManager,
                                            announcementTitle = if (remoteConfig.announcementActive) remoteConfig.announcementTitle else null,
                                            announcementBody = if (remoteConfig.announcementActive) remoteConfig.announcementBody else null,
                                            onToggleVpn = { triggerConnectSequence(isUserInitiated = true) },
                                            onNavigateToServers = { currentTab = Screen.Servers },
                                            onNavigateToConnected = { activeSubScreen = "connected" },
                                            onUpgradeClicked = { showVipModal = true }
                                        )
                                    }
                                    Screen.Servers -> {
                                        ServerListScreen(
                                            servers = servers,
                                            selectedServer = selectedServer,
                                            userTier = userTier,
                                            adMobConfig = adMobConfig,
                                            nativeAdManager = adMobManager.nativeAdManager,
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
                                                coroutineScope.launch { vpnRepo.refreshServers() }
                                            },
                                            onUpgradeRequired = { showVipModal = true }
                                        )
                                    }
                                    Screen.SpeedTest -> {
                                        SpeedTestScreen(
                                            currentServer = selectedServer,
                                            userTier = userTier,
                                            adMobConfig = adMobConfig,
                                            nativeAdManager = adMobManager.nativeAdManager
                                        )
                                    }
                                    Screen.Settings -> {
                                        SettingsScreen(
                                            themeMode = themeMode,
                                            onSelectThemeMode = { vpnRepo.setThemeMode(it) },
                                            killSwitchEnabled = killSwitchEnabled,
                                            onToggleKillSwitch = { vpnRepo.setKillSwitch(it) },
                                            selectedDns = selectedDns,
                                            onSelectDns = { vpnRepo.setDns(it) },
                                            splitTunnelingEnabled = splitTunnelingEnabled,
                                            onToggleSplitTunneling = { vpnRepo.setSplitTunneling(it) },
                                            autoConnectEnabled = autoConnectEnabled,
                                            onToggleAutoConnect = { vpnRepo.setAutoConnect(it) },
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

                if (showVpnDisclosureDialog) {
                    AlertDialog(
                        onDismissRequest = { showVpnDisclosureDialog = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = PrimaryBlue)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("VPN Service Disclosure", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        },
                        text = {
                            Text(
                                "Nsec VPN utilizes Android VpnService to create a secure, encrypted network tunnel to protect your online traffic. Your connection is routed through zero-logs WireGuard servers.\n\nNo personal browsing data, connection timestamps, or originating IP addresses are logged or shared.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showVpnDisclosureDialog = false
                                    vpnRepo.setVpnDisclosureAccepted(true)
                                    startConnectInternal(pendingUserInitiatedConnect)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Text("Accept & Continue")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showVpnDisclosureDialog = false }) {
                                Text("Decline", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                }

                if (showVipModal) {
                    AlertDialog(
                        onDismissRequest = { showVipModal = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = AmberWarning)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Upgrade to Nsec VPN VIP", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "Enjoy zero ads, unlimited 10Gbps WireGuard nodes, bypass all streaming geo-blocks, and priority routing.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    border = BorderStroke(1.5.dp, PrimaryBlue),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showVipModal = false
                                            coroutineScope.launch {
                                                playBillingManager.launchPurchase(
                                                    productId = "nsecvpn_premium_yearly",
                                                    onSuccess = {
                                                        vpnRepo.grantRewardedPass(365 * 24 * 60)
                                                        Toast.makeText(this@MainActivity, "Yearly VIP Activated! Thank you.", Toast.LENGTH_LONG).show()
                                                    },
                                                    onError = { err ->
                                                        Toast.makeText(this@MainActivity, err, Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Yearly VIP Plan", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    "50% OFF",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = EmeraldGreen,
                                                    modifier = Modifier.background(MaterialTheme.colorScheme.emeraldBadgeContainer, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text("$29.99 / year ($2.50/mo)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(Icons.Default.Star, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showVipModal = false
                                            coroutineScope.launch {
                                                playBillingManager.launchPurchase(
                                                    productId = "nsecvpn_premium_monthly",
                                                    onSuccess = {
                                                        vpnRepo.grantRewardedPass(30 * 24 * 60)
                                                        Toast.makeText(this@MainActivity, "Monthly VIP Activated!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    onError = { err ->
                                                        Toast.makeText(this@MainActivity, err, Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Monthly VIP Plan", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            Text("$4.99 / month • Cancel anytime", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showVipModal = false
                                            adMobManager.showRewarded(
                                                activity = this@MainActivity,
                                                onRewarded = {
                                                    vpnRepo.grantRewardedPass(30)
                                                    Toast.makeText(this@MainActivity, "Rewarded Ad Watched: 30-Minute VIP Pass Granted!", Toast.LENGTH_LONG).show()
                                                },
                                                onAdDismissed = {}
                                            )
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Watch Rewarded Ad for 30m Free VIP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                                        Text("Instantly unlocks all high-speed servers for 30 minutes without credit card.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showVipModal = false }) {
                                Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                }

                if (showPromoDialog) {
                    AlertDialog(
                        onDismissRequest = { showPromoDialog = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = PrimaryBlue)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Redeem Promo Code", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            }
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Enter your invite or promotional code to unlock Premium access.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                OutlinedTextField(
                                    value = promoCodeInput,
                                    onValueChange = { promoCodeInput = it },
                                    placeholder = { Text("e.g. VIP2026", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                        focusedBorderColor = PrimaryBlue,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
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
                                        vpnRepo.grantRewardedPass(7 * 24 * 60)
                                        Toast.makeText(this@MainActivity, "Code redeemed: 7 Days VIP Unlocked!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Text("Redeem")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPromoDialog = false }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                }
            }
        }
    }
}
