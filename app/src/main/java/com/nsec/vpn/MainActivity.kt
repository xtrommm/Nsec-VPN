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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Shield
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nsec.vpn.data.model.ServerStatus
import com.nsec.vpn.ui.components.VipAccessSheet
import com.nsec.vpn.ui.components.RewardOffer

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
                val operationError by tunnelManager.operationError.collectAsStateWithLifecycle()
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
                val installationId by vpnRepo.deviceIdentity.collectAsStateWithLifecycle()
                val privacyOptionsRequired by serviceLocator.adConsentManager.privacyOptionsRequired.collectAsStateWithLifecycle()
                val consentError by serviceLocator.adConsentManager.error.collectAsStateWithLifecycle()
                val excludedPackages by vpnRepo.excludedPackages.collectAsStateWithLifecycle()

                var currentTab by rememberSaveable(stateSaver = Saver<Screen, String>(save = { it.route }, restore = { route -> listOf(Screen.Home, Screen.Servers, Screen.SpeedTest, Screen.Settings).firstOrNull { it.route == route } ?: Screen.Home })) { mutableStateOf<Screen>(Screen.Home) }
                var activeSubScreen by rememberSaveable { mutableStateOf<String?>(null) }
                var isUserInitiatedConnect by remember { mutableStateOf(false) }


                var showVipModal by remember { mutableStateOf(false) }
                var rewardBusy by remember { mutableStateOf(false) }
                var serverStatusNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
                val lifecycleOwner = LocalLifecycleOwner.current
                val displayedServer = ServerStatus.current(servers, connectedNode?.nodeId, tunnelManager.activeServer ?: selectedServer)
                val rewardOffer = RewardOffer.from(remoteConfigLoaded, adMobConfig.rewardedEnabled,
                    adMobConfig.rewardedPassMinutes, userTier, adMobConfig.adFreeUntilEpochMillis != null)
                var showPromoDialog by remember { mutableStateOf(false) }
                var promoCodeInput by remember { mutableStateOf("") }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { _ -> }

                fun connectionBlockReason() = com.nsec.vpn.vpn.ConnectionEligibility.blockedReason(
                    remoteConfigLoaded, remoteConfig.maintenanceMode, remoteConfig.forceUpdate,
                    remoteConfig.minAppVersionCode, BuildConfig.VERSION_CODE)

                val vpnPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        connectionBlockReason()?.let { message ->
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                            return@rememberLauncherForActivityResult
                        }
                        val server = selectedServer ?: servers.firstOrNull()
                        if (server != null) {
                            val dns = try { vpnRepo.dnsServersForConnection() } catch (error: IllegalArgumentException) {
                                Toast.makeText(this@MainActivity, error.message ?: "Invalid DNS Settings.", Toast.LENGTH_LONG).show()
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
                        Toast.makeText(this, "VPN Permission Is Required To Secure Your Connection.", Toast.LENGTH_SHORT).show()
                    }
                }

                fun startConnectInternal(isUserInitiated: Boolean) {
                    connectionBlockReason()?.let { message ->
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                        return
                    }
                    isUserInitiatedConnect = isUserInitiated
                    val server = selectedServer ?: servers.firstOrNull()
                    if (server != null) {
                        val dns = try { vpnRepo.dnsServersForConnection() } catch (error: IllegalArgumentException) {
                            Toast.makeText(this@MainActivity, error.message ?: "Invalid DNS Settings.", Toast.LENGTH_LONG).show()
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
                            startConnectInternal(isUserInitiated)
                        }
                        else -> {}
                    }
                }

                fun watchReward() {
                    if (rewardBusy || !rewardOffer.available) return
                    if (!adMobManager.isRewardedReady()) {
                        adMobManager.preloadRewarded(this@MainActivity)
                        Toast.makeText(this@MainActivity, "The Ad Is Loading. Please Try Again Shortly.", Toast.LENGTH_LONG).show()
                        return
                    }
                    rewardBusy = true
                    coroutineScope.launch {
                        try {
                            val ticket = vpnRepo.prepareReward()
                            showVipModal = false
                            var earned = false
                            var adFailed = false
                            adMobManager.showRewarded(
                                activity = this@MainActivity,
                                verificationTicket = ticket,
                                onRewarded = {
                                    earned = true
                                    adMobManager.grantAdFreeReward()
                                    coroutineScope.launch {
                                        try {
                                            vpnRepo.claimReward(ticket)
                                            Toast.makeText(this@MainActivity, "VIP Activated: Ad-Free And VIP Servers Unlocked.", Toast.LENGTH_LONG).show()
                                        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
                                        catch (error: Exception) {
                                            Toast.makeText(this@MainActivity, error.message ?: "Ad-Free Is Active; VIP Activation Will Retry.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onError = { error ->
                                    adFailed = true
                                    Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                                },
                                onAdDismissed = {
                                    rewardBusy = false
                                    if (!earned && !adFailed) Toast.makeText(this@MainActivity, "Complete The Rewarded Ad To Unlock VIP.", Toast.LENGTH_SHORT).show()
                                }
                            )
                        } catch (error: kotlinx.coroutines.CancellationException) { rewardBusy = false; throw error }
                        catch (error: Exception) {
                            rewardBusy = false
                            Toast.makeText(this@MainActivity, error.message ?: "Could Not Prepare The VIP Reward.", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    serviceLocator.adConsentManager.request(this@MainActivity)
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

                LaunchedEffect(lifecycleOwner) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (true) {
                            kotlinx.coroutines.delay(30_000)
                            remoteConfigManager.fetchRemoteConfig()
                            vpnRepo.checkRewardedPassExpiry()
                            vpnRepo.retryPendingReward()
                        }
                    }
                }

                LaunchedEffect(servers.isNotEmpty(), autoConnectEnabled, remoteConfigLoaded) {
                    if (servers.isNotEmpty() && autoConnectEnabled && remoteConfigLoaded && vpnState is VpnState.Disconnected) triggerConnectSequence(isUserInitiated = false)
                }

                LaunchedEffect(lifecycleOwner) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (true) {
                            vpnRepo.refreshServers(refreshLatency = false)
                            serverStatusNow = System.currentTimeMillis()
                            kotlinx.coroutines.delay(15_000)
                        }
                    }
                }

                LaunchedEffect(vpnState, connectedNode?.nodeId) {
                    if (vpnState is VpnState.Connected) vpnRepo.refreshServers(refreshLatency = false)
                }

                LaunchedEffect(currentTab) {
                    if (currentTab == Screen.Servers) {
                        vpnRepo.refreshServers()
                    }
                }

                LaunchedEffect(operationError) {
                    operationError?.let { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() }
                }

                LaunchedEffect(consentError) {
                    consentError?.let { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() }
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
                                    selectedServer = displayedServer,
                                    userTier = userTier,
                                    onDisconnect = { tunnelManager.disconnect() },
                                    onBackToHome = { activeSubScreen = null; currentTab = Screen.Home },
                                    nativeAdContent = {
                                        com.nsec.vpn.ui.components.SmartNativeAd(
                                            nativeAdManager = adMobManager.nativeAdManager,
                                            adMobConfig = adMobConfig,
                                            userTier = userTier,
                                            vpnState = vpnState,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                )
                            }
                            "split_tunnel" -> {
                                SplitTunnelScreen(
                                    excludedPackages = excludedPackages,
                                    onTogglePackage = {
                                        vpnRepo.togglePackageExclusion(it)
                                        tunnelManager.updateExcludedApps(if (splitTunnelingEnabled) vpnRepo.excludedPackages.value else emptySet())
                                    },
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
                                            selectedServer = displayedServer,
                                            userTier = userTier,
                                            adMobConfig = adMobConfig,
                                            nativeAdManager = adMobManager.nativeAdManager,
                                            announcementTitle = if (remoteConfig.announcementActive) remoteConfig.announcementTitle else null,
                                            announcementBody = if (remoteConfig.announcementActive) remoteConfig.announcementBody else null,
                                            onToggleVpn = { triggerConnectSequence(isUserInitiated = true) },
                                            onNavigateToServers = { currentTab = Screen.Servers },
                                            onNavigateToConnected = { currentTab = Screen.Home; activeSubScreen = "connected" },
                                            onUpgradeClicked = { showVipModal = true },
                                            rewardOffer = rewardOffer,
                                            rewardBusy = rewardBusy,
                                            onWatchReward = { watchReward() },
                                            serverStatusNow = serverStatusNow
                                        )
                                    }
                                    Screen.Servers -> {
                                        ServerListScreen(
                                            servers = servers,
                                            isLoading = serversLoading,
                                            usingCache = usingCachedServers,
                                            errorMessage = serverListError,
                                            selectedServer = displayedServer,
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
                                                coroutineScope.launch { vpnRepo.refreshServers(force = true) }
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
                                                try { vpnRepo.setDns(provider); tunnelManager.updateDns(vpnRepo.dnsServersForConnection()); Toast.makeText(this@MainActivity, "DNS Saved. Active Tunnels Reconnect To Apply It.", Toast.LENGTH_SHORT).show() }
                                                catch (e: IllegalArgumentException) { Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show() }
                                            },
                                            customDns = vpnRepo.customDns(),
                                            activeDns = activeDns,
                                            onCustomDns = { raw -> vpnRepo.setCustomDns(raw); tunnelManager.updateDns(vpnRepo.dnsServersForConnection()) },
                                            splitTunnelingEnabled = splitTunnelingEnabled,
                                            onToggleSplitTunneling = {
                                                vpnRepo.setSplitTunneling(it)
                                                tunnelManager.updateExcludedApps(if (it) vpnRepo.excludedPackages.value else emptySet())
                                            },
                                            autoConnectEnabled = autoConnectEnabled,
                                            onToggleAutoConnect = { vpnRepo.setAutoConnect(it) },
                                            onOpenSplitTunnelApps = { activeSubScreen = "split_tunnel" },
                                            onOpenTroubleshooting = { activeSubScreen = "troubleshoot" },
                                            onOpenPrivacyPolicy = { activeSubScreen = "legal" },
                                            onRedeemPromoCode = { showPromoDialog = true },
                                            supportEmail = remoteConfig.supportEmail,
                                            privacyOptionsRequired = privacyOptionsRequired,
                                            privacyUpdateFailed = consentError != null,
                                            onRestorePurchases = {
                                                vpnRepo.refreshVerifiedEntitlement()
                                                try { playBillingManager.restorePurchases() }
                                                finally { vpnRepo.refreshVerifiedEntitlement() }
                                            },
                                            onPrivacyOptions = { serviceLocator.adConsentManager.showPrivacyOptions(this@MainActivity) },
                                            deviceId = installationId,
                                            onRecoveryStatus = { vpnRepo.recoveryConfigured() },
                                            onSetRecoveryPin = { pin, oldPin -> vpnRepo.setRecoveryPin(pin, oldPin) },
                                            onRecoverDevice = { id, pin ->
                                                require(vpnState !is VpnState.Connected && vpnState !is VpnState.Connecting && vpnState !is VpnState.Disconnecting) { "Disconnect The VPN Before Restoring A Device." }
                                                vpnRepo.recoverDevice(id, pin)
                                            },
                                            onRequestServer = { country, city, note -> vpnRepo.requestServer(country, city, note) },
                                            onOpenDebugDiagnostics = { activeSubScreen = "debug_diagnostics" }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (showVipModal) {
                    VipAccessSheet(
                        offer = rewardOffer,
                        busy = rewardBusy,
                        onDismiss = { showVipModal = false },
                        onWatchReward = { watchReward() },
                        onPurchase = { productId ->
                            showVipModal = false
                            coroutineScope.launch {
                                playBillingManager.launchPurchase(
                                    activity = this@MainActivity,
                                    productId = productId,
                                    onSuccess = {
                                        coroutineScope.launch {
                                            try { vpnRepo.refreshVerifiedEntitlement() }
                                            catch (error: kotlinx.coroutines.CancellationException) { throw error }
                                            catch (_: Exception) { Toast.makeText(this@MainActivity, "Purchase Verified; Entitlement Refresh Pending", Toast.LENGTH_LONG).show() }
                                        }
                                    },
                                    onError = { error -> Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show() }
                                )
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
                                Text("Enter Your Invite Or Promotional Code To Unlock Premium Access.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                OutlinedTextField(
                                    value = promoCodeInput,
                                    onValueChange = { promoCodeInput = it },
                                    placeholder = { Text("Enter Promo Code", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
                                                Toast.makeText(this@MainActivity, "Promo Redeemed. Premium Access Is Active.", Toast.LENGTH_LONG).show()
                                                promoCodeInput = ""
                                            } catch (error: Exception) {
                                                Toast.makeText(this@MainActivity, error.message ?: "Could Not Redeem Code", Toast.LENGTH_LONG).show()
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
