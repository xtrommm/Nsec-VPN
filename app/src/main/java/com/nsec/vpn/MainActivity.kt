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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
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

        lifecycleScope.launch { vpnRepo.refreshServers() }

        setContent {
            val themeMode by vpnRepo.themeMode.collectAsStateWithLifecycle()
            val adMobConfig by adMobManager.config.collectAsStateWithLifecycle()
            val remoteConfig by remoteConfigManager.config.collectAsStateWithLifecycle()
            val remoteConfigLoaded by remoteConfigManager.loaded.collectAsStateWithLifecycle()
            val remoteConfigError by remoteConfigManager.error.collectAsStateWithLifecycle()

            NsecVpnTheme(themeMode = themeMode) {
                val coroutineScope = rememberCoroutineScope()

                val vpnState by tunnelManager.vpnState.collectAsStateWithLifecycle()
                val vpnStats by tunnelManager.vpnStats.collectAsStateWithLifecycle()
                val connectedNode by tunnelManager.connectedNode.collectAsStateWithLifecycle()
                val serversLoading by vpnRepo.serversLoading.collectAsStateWithLifecycle()
                val usingCachedServers by vpnRepo.usingCachedServers.collectAsStateWithLifecycle()
                val serverListError by vpnRepo.serverListError.collectAsStateWithLifecycle()
                val servers by vpnRepo.servers.collectAsStateWithLifecycle()
                val selectedServer by vpnRepo.selectedServer.collectAsStateWithLifecycle()
                val userTier by vpnRepo.userTier.collectAsStateWithLifecycle()
                val activeDns by tunnelManager.activeDns.collectAsStateWithLifecycle()
                val selectedDns by vpnRepo.selectedDns.collectAsStateWithLifecycle()
                val splitTunnelingEnabled by vpnRepo.splitTunnelingEnabled.collectAsStateWithLifecycle()
                val autoConnectEnabled by vpnRepo.autoConnectEnabled.collectAsStateWithLifecycle()
                val installationId = remember { serviceLocator.keyStoreManager.getOrCreateDeviceId() }
                val excludedPackages by vpnRepo.excludedPackages.collectAsStateWithLifecycle()

                var currentTab by rememberSaveable(stateSaver = Saver<Screen, String>(save = { it.route }, restore = { route -> listOf(Screen.Home, Screen.Servers, Screen.SpeedTest, Screen.Settings).firstOrNull { it.route == route } ?: Screen.Home })) { mutableStateOf<Screen>(Screen.Home) }
                var activeSubScreen by rememberSaveable { mutableStateOf<String?>(null) }
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
                            val dns = try { vpnRepo.dnsServersForConnection() } catch (error: IllegalArgumentException) {
                                Toast.makeText(this@MainActivity, error.message ?: "Invalid DNS settings.", Toast.LENGTH_LONG).show()
                                return@rememberLauncherForActivityResult
                            }
                            tunnelManager.startConnect(
                                server = server,
                                dnsServers = dns,
                                excludedApps = if (splitTunnelingEnabled) excludedPackages else emptySet(),
                                onNeedsPermission = {}
                            )
                        }
                    } else {
                        Toast.makeText(this, "VPN permission is required to secure your connection.", Toast.LENGTH_SHORT).show()
                    }
                }

                fun startConnectInternal(isUserInitiated: Boolean) {
                    if (!remoteConfigLoaded) {
                        Toast.makeText(this@MainActivity, "App settings are still loading. Please retry shortly.", Toast.LENGTH_SHORT).show()
                        return
                    }
                    if (remoteConfig.maintenanceMode) {
                        Toast.makeText(this@MainActivity, "VPN maintenance is in progress. Please try again later.", Toast.LENGTH_LONG).show()
                        return
                    }
                    if (remoteConfig.forceUpdate && BuildConfig.VERSION_CODE < remoteConfig.minAppVersionCode) {
                        Toast.makeText(this@MainActivity, "Update Nsec VPN before connecting.", Toast.LENGTH_LONG).show()
                        return
                    }
                    isUserInitiatedConnect = isUserInitiated
                    val server = selectedServer ?: servers.firstOrNull()
                    if (server != null) {
                        val dns = try { vpnRepo.dnsServersForConnection() } catch (error: IllegalArgumentException) {
                            Toast.makeText(this@MainActivity, error.message ?: "Invalid DNS settings.", Toast.LENGTH_LONG).show()
                            return
                        }
                        tunnelManager.startConnect(
                            server = server,
                            dnsServers = dns,
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
                    val configRefresh = launch { remoteConfigManager.fetchRemoteConfig() }
                    vpnRepo.refreshServers()
                    configRefresh.join()
                    vpnRepo.retryPendingReward()
                    adMobManager.preloadInterstitial(this@MainActivity)
                    adMobManager.preloadRewarded(this@MainActivity)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    vpnRepo.checkRewardedPassExpiry()

                }

                LaunchedEffect(Unit) {
                    while (true) {
                        kotlinx.coroutines.delay(30_000)
                        remoteConfigManager.fetchRemoteConfig()
                        vpnRepo.checkRewardedPassExpiry()
                        vpnRepo.retryPendingReward()
                    }
                }

                LaunchedEffect(servers.isNotEmpty(), autoConnectEnabled, remoteConfigLoaded) {
                    if (servers.isNotEmpty() && autoConnectEnabled && remoteConfigLoaded && vpnState is VpnState.Disconnected) triggerConnectSequence(isUserInitiated = false)
                }

                LaunchedEffect(currentTab) {
                    if (currentTab == Screen.Servers) {
                        vpnRepo.refreshServers()
                    }
                }

                LaunchedEffect(remoteConfigError) {
                    remoteConfigError?.let { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() }
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
                                        currentTab = Screen.Home; activeSubScreen = "connected"
                                    }
                                } else {
                                    currentTab = Screen.Home; activeSubScreen = "connected"
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
                        if (activeSubScreen == null || activeSubScreen == "connected") {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 6.dp
                            ) {
                                listOf(Screen.Home, Screen.Servers, Screen.SpeedTest, Screen.Settings).forEach { screen ->
                                    val isSelected = currentTab == screen
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = { activeSubScreen = null; currentTab = screen },
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
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
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
                                    connectedNode = connectedNode,
                                    selectedServer = tunnelManager.activeServer ?: selectedServer,
                                    userTier = userTier,
                                    onDisconnect = { tunnelManager.disconnect() },
                                    onBackToHome = { activeSubScreen = null; currentTab = Screen.Home }
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
                            "debug_diagnostics" -> {
                                if (com.nsec.vpn.BuildConfig.DEBUG) DebugDiagnosticsScreen(
                                    onBack = { activeSubScreen = null }
                                )
                            }
                            else -> {
                                when (currentTab) {
                                    Screen.Home -> {
                                        HomeScreen(
                                            vpnState = vpnState,
                                            tunnelManager = tunnelManager,
                                            selectedServer = tunnelManager.activeServer ?: selectedServer,
                                            userTier = userTier,
                                            adMobConfig = adMobConfig,
                                            nativeAdManager = adMobManager.nativeAdManager,
                                            announcementTitle = if (remoteConfig.announcementActive) remoteConfig.announcementTitle else null,
                                            announcementBody = if (remoteConfig.announcementActive) remoteConfig.announcementBody else null,
                                            onToggleVpn = { triggerConnectSequence(isUserInitiated = true) },
                                            onNavigateToServers = { currentTab = Screen.Servers },
                                            onNavigateToConnected = { currentTab = Screen.Home; activeSubScreen = "connected" },
                                            onUpgradeClicked = { showVipModal = true }
                                        )
                                    }
                                    Screen.Servers -> {
                                        ServerListScreen(
                                            servers = servers,
                                            isLoading = serversLoading,
                                            usingCache = usingCachedServers,
                                            errorMessage = serverListError,
                                            selectedServer = tunnelManager.activeServer ?: selectedServer,
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
                                            selectedDns = selectedDns,
                                            onSelectDns = { provider ->
                                                try { vpnRepo.setDns(provider); tunnelManager.updateDns(vpnRepo.dnsServersForConnection()); Toast.makeText(this@MainActivity, "DNS saved. Active tunnels reconnect to apply it.", Toast.LENGTH_SHORT).show() }
                                                catch (e: IllegalArgumentException) { Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show() }
                                            },
                                            customDns = vpnRepo.customDns(),
                                            activeDns = activeDns,
                                            onCustomDns = { raw -> vpnRepo.setCustomDns(raw); tunnelManager.updateDns(vpnRepo.dnsServersForConnection()) },
                                            splitTunnelingEnabled = splitTunnelingEnabled,
                                            onToggleSplitTunneling = { vpnRepo.setSplitTunneling(it) },
                                            autoConnectEnabled = autoConnectEnabled,
                                            onToggleAutoConnect = { vpnRepo.setAutoConnect(it) },
                                            onOpenSplitTunnelApps = { activeSubScreen = "split_tunnel" },
                                            onOpenTroubleshooting = { activeSubScreen = "troubleshoot" },
                                            onOpenPrivacyPolicy = { activeSubScreen = "legal" },
                                            onRedeemPromoCode = { showPromoDialog = true },
                                            supportEmail = remoteConfig.supportEmail,
                                            deviceId = installationId,
                                            onRequestServer = { country, city, note -> vpnRepo.requestServer(country, city, note) },
                                            onOpenDebugDiagnostics = { activeSubScreen = "debug_diagnostics" }
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
                                "Nsec VPN utilizes Android VpnService to create a secure, encrypted network tunnel to protect your online traffic. Your connection is routed through zero-logs WireGuard servers.\n\nNo browsing data, DNS queries or originating IP addresses are logged. Device credentials and expiring public-key/address leases are stored to authorize and operate the VPN. Ads and billing providers process data under their own policies.",
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
                                                    activity = this@MainActivity,
                                                    productId = "nsecvpn_premium_yearly",
                                                    onSuccess = {
                                                        coroutineScope.launch {
                                                            try { vpnRepo.refreshVerifiedEntitlement() }
                                                            catch (_: Exception) { Toast.makeText(this@MainActivity, "Purchase verified; entitlement refresh pending", Toast.LENGTH_LONG).show() }
                                                        }
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
                                            Text("Exact price shown by Google Play before purchase", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                    activity = this@MainActivity,
                                                    productId = "nsecvpn_premium_monthly",
                                                    onSuccess = {
                                                        coroutineScope.launch {
                                                            try { vpnRepo.refreshVerifiedEntitlement() }
                                                            catch (_: Exception) { Toast.makeText(this@MainActivity, "Purchase verified; entitlement refresh pending", Toast.LENGTH_LONG).show() }
                                                        }
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
                                            Text("Exact price shown by Google Play before purchase", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                            coroutineScope.launch {
                                                try {
                                                    val ticket = vpnRepo.prepareReward()
                                                    adMobManager.showRewarded(
                                                        activity = this@MainActivity,
                                                        verificationTicket = ticket,
                                                        onRewarded = {
                                                            adMobManager.grantAdFreeReward()
                                                            coroutineScope.launch {
                                                                try {
                                                                    vpnRepo.claimReward(ticket)
                                                                    Toast.makeText(this@MainActivity, "VIP activated: ad-free and premium servers unlocked.", Toast.LENGTH_LONG).show()
                                                                } catch (error: Exception) {
                                                                    Toast.makeText(this@MainActivity, error.message ?: "VIP claim will retry", Toast.LENGTH_LONG).show()
                                                                }
                                                            }
                                                        },
                                                        onAdDismissed = {}
                                                    )
                                                } catch (error: Exception) {
                                                    Toast.makeText(this@MainActivity, error.message ?: "Could not prepare VIP reward", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Watch Rewarded Ad for 45 minutes VIP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                                        Text("Unlocks ad-free VIP and premium servers after successful completion.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    placeholder = { Text("Enter promo code", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
                                        coroutineScope.launch {
                                            try {
                                                vpnRepo.redeemPromo(promoCodeInput)
                                                Toast.makeText(this@MainActivity, "Promo redeemed. Premium access is active.", Toast.LENGTH_LONG).show()
                                                promoCodeInput = ""
                                            } catch (error: Exception) {
                                                Toast.makeText(this@MainActivity, error.message ?: "Could not redeem code", Toast.LENGTH_LONG).show()
                                            }
                                        }
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
