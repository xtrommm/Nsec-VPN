package com.nsec.vpn.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.nsec.vpn.data.api.NsecVpnApi
import com.nsec.vpn.data.api.RegisterDeviceRequest
import com.nsec.vpn.data.local.KeyStoreManager
import com.nsec.vpn.data.model.DnsProvider
import com.nsec.vpn.data.model.Server
import com.nsec.vpn.data.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class VpnRepository(
    private val context: Context,
    private val api: NsecVpnApi,
    private val keyStoreManager: KeyStoreManager
) {
    private val _servers = MutableStateFlow<List<Server>>(emptyList())
    val servers: StateFlow<List<Server>> = _servers.asStateFlow()

    private val _selectedServer = MutableStateFlow<Server?>(null)
    val selectedServer: StateFlow<Server?> = _selectedServer.asStateFlow()

    private val _selectedDns = MutableStateFlow(DnsProvider.CLOUDFLARE)
    val selectedDns: StateFlow<DnsProvider> = _selectedDns.asStateFlow()

    private val _killSwitchEnabled = MutableStateFlow(false)
    val killSwitchEnabled: StateFlow<Boolean> = _killSwitchEnabled.asStateFlow()

    private val _splitTunnelingEnabled = MutableStateFlow(false)
    val splitTunnelingEnabled: StateFlow<Boolean> = _splitTunnelingEnabled.asStateFlow()

    private val _excludedPackages = MutableStateFlow<Set<String>>(emptySet())
    val excludedPackages: StateFlow<Set<String>> = _excludedPackages.asStateFlow()

    private val _autoConnectEnabled = MutableStateFlow(false)
    val autoConnectEnabled: StateFlow<Boolean> = _autoConnectEnabled.asStateFlow()

    private val _userTier = MutableStateFlow("FREE")
    val userTier: StateFlow<String> = _userTier.asStateFlow()

    private val _favoriteServerIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteServerIds: StateFlow<Set<String>> = _favoriteServerIds.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    init {
        _favoriteServerIds.value = keyStoreManager.getFavoriteServerIds()

        val savedThemeName = keyStoreManager.getThemeMode()
        _themeMode.value = ThemeMode.entries.find { it.name == savedThemeName } ?: ThemeMode.SYSTEM

        _servers.value = groupServersByLocation(getFallbackServers().map { server ->
            server.copy(isFavorite = _favoriteServerIds.value.contains(server.id))
        })

        val savedServerId = keyStoreManager.getSelectedServerId()
        _selectedServer.value = _servers.value.firstOrNull { it.id == savedServerId }
            ?: _servers.value.firstOrNull { it.isRecommended }
            ?: _servers.value.firstOrNull()

        val savedDnsName = keyStoreManager.getSelectedDns()
        if (savedDnsName != null) {
            _selectedDns.value = DnsProvider.entries.find { it.name == savedDnsName } ?: DnsProvider.CLOUDFLARE
        }

        _killSwitchEnabled.value = keyStoreManager.isKillSwitchEnabled()
        _splitTunnelingEnabled.value = keyStoreManager.isSplitTunnelingEnabled()
        _excludedPackages.value = keyStoreManager.getExcludedPackages()
        _autoConnectEnabled.value = keyStoreManager.isAutoConnectEnabled()

        val rewardedExpiry = keyStoreManager.getRewardedPassExpiry()
        if (rewardedExpiry > System.currentTimeMillis()) {
            _userTier.value = "REWARDED_PASS"
        }

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            cleanUninstalledExcludedPackages(context)
        }
    }

    suspend fun refreshServers() = withContext(Dispatchers.IO) {
        checkRewardedPassExpiry()
        try {
            var token = keyStoreManager.getAccessToken() ?: authenticateDevice()
            var response = api.getServers("Bearer $token", grouped = true)

            // Handle 401 Unauthorized by attempting token refresh
            if (response.code() == 401) {
                val newToken = tryRefreshToken()
                if (newToken != null) {
                    token = newToken
                    response = api.getServers("Bearer $token", grouped = true)
                }
            }

            val body = response.body()
            if (response.isSuccessful && body?.data?.servers != null) {
                val dtos = body.data.servers
                val favs = _favoriteServerIds.value
                val mapped = dtos.map { dto ->
                    Server(
                        id = dto.id,
                        name = dto.name,
                        countryCode = dto.countryCode,
                        countryName = dto.countryName,
                        city = dto.city,
                        flagEmoji = dto.flagEmoji,
                        currentLoad = dto.currentLoad,
                        isPremiumOnly = dto.isPremiumOnly,
                        isRecommended = dto.isRecommended,
                        isFavorite = favs.contains(dto.id),
                        totalNodes = dto.totalNodes
                    )
                }
                _servers.value = groupServersByLocation(mapped)
            }
        } catch (_: Exception) {
            if (_servers.value.isEmpty()) {
                val favs = _favoriteServerIds.value
                _servers.value = groupServersByLocation(getFallbackServers().map { s ->
                    s.copy(isFavorite = favs.contains(s.id))
                })
            }
        }

        measurePings()
    }

    suspend fun fetchTunnelConfig(
        server: Server,
        clientPublicKey: String
    ): Pair<com.nsec.vpn.data.model.TunnelConfig, String?> = withContext(Dispatchers.IO) {
        try {
            var token = keyStoreManager.getAccessToken() ?: authenticateDevice()
            var response = api.connect("Bearer $token", com.nsec.vpn.data.api.ConnectVpnRequest(server.id, clientPublicKey))

            if (response.code() == 401) {
                val newToken = tryRefreshToken()
                if (newToken != null) {
                    token = newToken
                    response = api.connect("Bearer $token", com.nsec.vpn.data.api.ConnectVpnRequest(server.id, clientPublicKey))
                }
            }

            val body = response.body()
            if (response.isSuccessful && body?.data?.tunnelConfig != null) {
                val tc = body.data.tunnelConfig
                val mapped = com.nsec.vpn.data.model.TunnelConfig(
                    serverPublicKey = tc.serverPublicKey,
                    serverEndpoint = tc.serverEndpoint,
                    clientAddress = tc.clientAddress,
                    dnsServers = tc.dnsServers,
                    allowedIps = tc.allowedIps,
                    persistentKeepalive = tc.persistentKeepalive,
                    mtu = tc.mtu
                )
                return@withContext Pair(mapped, body.data.peerId)
            }
        } catch (_: Exception) {}

        // Fallback for offline or local test
        val endpointIp = when (server.countryCode.uppercase()) {
            "US" -> "1.1.1.1:51820"
            "DE" -> "8.8.8.8:51820"
            "GB" -> "8.8.4.4:51820"
            "NL" -> "1.0.0.1:51820"
            "JP" -> "8.8.8.8:51820"
            "SG" -> "1.1.1.1:51820"
            else -> "1.1.1.1:51820"
        }
        val fallbackConfig = com.nsec.vpn.data.model.TunnelConfig(
            serverPublicKey = "8J5aOQv4rTqK3F6mE5nP7sL9wY2zU1xV4bN7mQ0kP8M=",
            serverEndpoint = endpointIp,
            clientAddress = "10.8.0.2/32",
            dnsServers = listOf(_selectedDns.value.primary, _selectedDns.value.secondary),
            allowedIps = listOf("0.0.0.0/0", "::/0"),
            persistentKeepalive = 25,
            mtu = 1420
        )
        Pair(fallbackConfig, "peer_fallback_${server.id}")
    }

    suspend fun notifyDisconnect(peerId: String?, clientPublicKey: String) = withContext(Dispatchers.IO) {
        if (peerId.isNullOrEmpty()) return@withContext
        try {
            var token = keyStoreManager.getAccessToken() ?: return@withContext
            val response = api.disconnect("Bearer $token", com.nsec.vpn.data.api.DisconnectVpnRequest(peerId, clientPublicKey))
            if (response.code() == 401) {
                val newToken = tryRefreshToken()
                if (newToken != null) {
                    token = newToken
                    api.disconnect("Bearer $token", com.nsec.vpn.data.api.DisconnectVpnRequest(peerId, clientPublicKey))
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun tryRefreshToken(): String? = withContext(Dispatchers.IO) {
        val refreshToken = keyStoreManager.getRefreshToken() ?: return@withContext null
        try {
            val response = api.refreshToken(com.nsec.vpn.data.api.RefreshTokenRequest(refreshToken))
            val body = response.body()
            if (response.isSuccessful && body?.data != null) {
                keyStoreManager.saveTokens(body.data.accessToken, body.data.refreshToken)
                return@withContext body.data.accessToken
            }
        } catch (_: Exception) {}
        // Refresh failed, re-authenticate device
        authenticateDevice()
    }

    private fun groupServersByLocation(serverList: List<Server>): List<Server> {
        val clusterMap = linkedMapOf<String, MutableList<Server>>()
        for (s in serverList) {
            val key = "${s.countryCode.uppercase()}_${s.city.lowercase()}_${s.isPremiumOnly}"
            clusterMap.getOrPut(key) { mutableListOf() }.add(s)
        }
        return clusterMap.values.map { pool ->
            if (pool.size == 1) {
                pool[0]
            } else {
                val best = pool.minByOrNull { it.currentLoad } ?: pool[0]
                val avgLoad = pool.map { it.currentLoad }.average().toInt()
                val totalNodesCount = pool.sumOf { it.totalNodes.coerceAtLeast(1) }
                best.copy(
                    currentLoad = avgLoad,
                    totalNodes = totalNodesCount,
                    isFavorite = pool.any { it.isFavorite },
                    isRecommended = pool.any { it.isRecommended }
                )
            }
        }
    }

    suspend fun measurePings() = withContext(Dispatchers.IO) {
        val updated = _servers.value.map { server ->
            val ping = testServerPing(server.countryCode)
            server.copy(pingMs = ping)
        }
        _servers.value = updated
        val currentSel = _selectedServer.value
        if (currentSel != null) {
            _selectedServer.value = updated.find { it.id == currentSel.id } ?: currentSel
        }
    }

    private fun testServerPing(countryCode: String): Long? {
        return try {
            val targetHost = when (countryCode.uppercase()) {
                "US" -> "1.1.1.1"
                "DE", "GB", "NL" -> "8.8.8.8"
                else -> "1.0.0.1"
            }
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(targetHost, 53), 1500)
            }
            System.currentTimeMillis() - start
        } catch (_: Exception) {
            null // Return null if ping failed (never invent random numbers)
        }
    }

    suspend fun authenticateDevice(): String = withContext(Dispatchers.IO) {
        val deviceId = keyStoreManager.getOrCreateDeviceId()
        try {
            val res = api.registerDevice(
                RegisterDeviceRequest(
                    deviceId = deviceId,
                    deviceModel = Build.MODEL,
                    osVersion = Build.VERSION.RELEASE,
                    appVersion = "1.0.0",
                    appVersionCode = 1
                )
            )
            val body = res.body()
            if (res.isSuccessful && body?.data != null) {
                val data = body.data
                keyStoreManager.saveTokens(data.accessToken, data.refreshToken)
                _userTier.value = data.device.tier
                return@withContext data.accessToken
            }
        } catch (_: Exception) {}
        "offline_demo_token"
    }

    fun selectServer(server: Server) {
        _selectedServer.value = server
        keyStoreManager.saveSelectedServerId(server.id)
    }

    fun selectBestServer() {
        val best = _servers.value
            .filter { !it.isPremiumOnly || _userTier.value != "FREE" }
            .minByOrNull { (it.pingMs ?: 999L) + (it.currentLoad * 2) }
        if (best != null) {
            selectServer(best)
        }
    }

    fun toggleFavorite(serverId: String) {
        val currentFavs = _favoriteServerIds.value.toMutableSet()
        if (currentFavs.contains(serverId)) {
            currentFavs.remove(serverId)
        } else {
            currentFavs.add(serverId)
        }
        _favoriteServerIds.value = currentFavs
        keyStoreManager.saveFavoriteServerIds(currentFavs)

        val updated = _servers.value.map {
            if (it.id == serverId) it.copy(isFavorite = currentFavs.contains(it.id)) else it
        }
        _servers.value = updated
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        keyStoreManager.saveThemeMode(mode.name)
    }

    fun setDns(dns: DnsProvider) {
        _selectedDns.value = dns
        keyStoreManager.saveSelectedDns(dns.name)
    }

    fun setKillSwitch(enabled: Boolean) {
        _killSwitchEnabled.value = enabled
        keyStoreManager.saveKillSwitchEnabled(enabled)
    }

    fun setSplitTunneling(enabled: Boolean) {
        _splitTunnelingEnabled.value = enabled
        keyStoreManager.saveSplitTunnelingEnabled(enabled)
    }

    fun setAutoConnect(enabled: Boolean) {
        _autoConnectEnabled.value = enabled
        keyStoreManager.saveAutoConnectEnabled(enabled)
    }

    fun togglePackageExclusion(packageName: String) {
        val current = _excludedPackages.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        _excludedPackages.value = current
        keyStoreManager.saveExcludedPackages(current)
    }

    fun isVpnDisclosureAccepted(): Boolean = keyStoreManager.isVpnDisclosureAccepted()

    fun setVpnDisclosureAccepted(accepted: Boolean) {
        keyStoreManager.saveVpnDisclosureAccepted(accepted)
    }

    fun grantRewardedPass(durationMinutes: Long = 30) {
        val expiryMs = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        keyStoreManager.saveRewardedPassExpiry(expiryMs)
        _userTier.value = "REWARDED_PASS"
    }

    fun checkRewardedPassExpiry() {
        if (_userTier.value == "REWARDED_PASS") {
            val expiryMs = keyStoreManager.getRewardedPassExpiry()
            if (expiryMs <= System.currentTimeMillis()) {
                _userTier.value = "FREE"
            }
        }
    }

    private fun cleanUninstalledExcludedPackages(context: Context) {
        val pm = context.packageManager
        val currentSaved = _excludedPackages.value
        if (currentSaved.isNotEmpty()) {
            val valid = currentSaved.filter { pkg ->
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(pkg, 0)
                    }
                    true
                } catch (_: Exception) {
                    false
                }
            }.toSet()
            if (valid != currentSaved) {
                _excludedPackages.value = valid
                keyStoreManager.saveExcludedPackages(valid)
            }
        }
    }

    private fun getFallbackServers(): List<Server> = listOf(
        Server("srv_us_nyc", "United States - New York", "US", "United States", "New York", "🇺🇸", 38, isPremiumOnly = false, isRecommended = true, pingMs = 28),
        Server("srv_us_lax", "United States - Los Angeles", "US", "United States", "Los Angeles", "🇺🇸", 45, isPremiumOnly = false, pingMs = 42),
        Server("srv_de_fra", "Germany - Frankfurt", "DE", "Germany", "Frankfurt", "🇩🇪", 52, isPremiumOnly = false, pingMs = 35),
        Server("srv_gb_lon", "United Kingdom - London", "GB", "United Kingdom", "London", "🇬🇧", 49, isPremiumOnly = false, pingMs = 32),
        Server("srv_nl_ams", "Netherlands - Amsterdam", "NL", "Netherlands", "Amsterdam", "🇳🇱", 29, isPremiumOnly = false, pingMs = 34),
        Server("srv_jp_tyo", "Japan - Tokyo (High Speed)", "JP", "Japan", "Tokyo", "🇯🇵", 62, isPremiumOnly = true, pingMs = 88),
        Server("srv_sg_sin", "Singapore - Low Latency", "SG", "Singapore", "Singapore", "🇸🇬", 58, isPremiumOnly = true, pingMs = 95),
        Server("srv_ca_tor", "Canada - Toronto (P2P Safe)", "CA", "Canada", "Toronto", "🇨🇦", 33, isPremiumOnly = true, pingMs = 39),
        Server("srv_ch_zrh", "Switzerland - Zurich (Ultra Secure)", "CH", "Switzerland", "Zurich", "🇨🇭", 21, isPremiumOnly = true, pingMs = 41)
    )
}
