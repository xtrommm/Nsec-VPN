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
    private val _serverListError = MutableStateFlow<String?>(null)
    val serverListError: StateFlow<String?> = _serverListError.asStateFlow()
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

    private var verifiedEntitlementExpiresAtMs: Long? = null
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

        _servers.value = emptyList()

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

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            cleanUninstalledExcludedPackages(context)
        }
    }

    suspend fun refreshServers() = withContext(Dispatchers.IO) {
        checkRewardedPassExpiry()
        try {
            var token = keyStoreManager.getAccessToken() ?: authenticateDevice()
            var response = api.getServers("Bearer $token", grouped = false)

            // Handle 401 Unauthorized by attempting token refresh
            if (response.code() == 401) {
                val newToken = tryRefreshToken()
                if (newToken != null) {
                    token = newToken
                    response = api.getServers("Bearer $token", grouped = false)
                }
            }

            val body = response.body()
            if (response.isSuccessful && body?.success == true && body.data?.servers != null) {
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
                _serverListError.value = null
                _servers.value = mapped
                try { refreshVerifiedEntitlement() } catch (_: Exception) { /* Retain only this process's verified entitlement. */ }

                val currentSelected = _selectedServer.value
                if (currentSelected == null || !mapped.any { it.id == currentSelected.id }) {
                    _selectedServer.value = mapped.firstOrNull { it.isRecommended } ?: mapped.firstOrNull()
                }
            } else { _serverListError.value = "Server list unavailable (${response.code()})" }
        } catch (e: Exception) {
            _serverListError.value = "Server list unavailable; check your internet connection"
            // No fallback to mock servers. Keep existing servers or empty list.
        }

        measurePings()
    }

    suspend fun fetchTunnelConfig(
        server: Server,
        clientPublicKey: String
    ): Pair<com.nsec.vpn.data.model.TunnelConfig, String?> = withContext(Dispatchers.IO) {
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
        if (response.isSuccessful && body?.success == true && body.data?.tunnelConfig != null) {
            val tc = body.data.tunnelConfig
            val mapped = com.nsec.vpn.data.model.TunnelConfig(
                serverPublicKey = tc.serverPublicKey,
                serverEndpoint = tc.serverEndpoint,
                clientAddress = tc.clientAddress,
                dnsServers = tc.dnsServers,
                allowedIps = tc.allowedIps,
                persistentKeepalive = tc.persistentKeepalive,
                mtu = tc.mtu, nodeId = tc.nodeId, nodeName = tc.nodeName, locationName = tc.locationName, publicIpv4 = tc.publicIpv4
            )
            return@withContext Pair(mapped, body.data.peerId)
        } else {
            val code = try { org.json.JSONObject(response.errorBody()?.string().orEmpty()).optString("error") } catch (_: Exception) { "" }
            throw com.nsec.vpn.vpn.VpnApiException(response.code(), when (code) {
                "SERVER_FULL" -> "Server is full"
                "NODE_DISABLED" -> "Server is disabled"
                "NODE_OFFLINE", "AGENT_UNREACHABLE" -> "Server is offline"
                "TIER_UPGRADE_REQUIRED" -> "This server requires an active premium pass"
                else -> "Peer creation failed (${response.code()})"
            })
        }
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
            val ping: Long? = null
            server.copy(pingMs = ping)
        }
        _servers.value = updated
        val currentSel = _selectedServer.value
        if (currentSel != null) {
            _selectedServer.value = updated.find { it.id == currentSel.id } ?: currentSel
        }
    }

    suspend fun authenticateDevice(): String = withContext(Dispatchers.IO) {
        val deviceId = keyStoreManager.getOrCreateDeviceId()
        val res = api.registerDevice(
            RegisterDeviceRequest(
                deviceId = deviceId,
                deviceModel = Build.MODEL,
                osVersion = Build.VERSION.RELEASE,
                appVersion = "1.0.0",
                appVersionCode = 1,
                registrationSecret = keyStoreManager.getOrCreateRegistrationSecret(),
                registrationProof = keyStoreManager.getAccessToken()
            )
        )
        val body = res.body()
        if (res.isSuccessful && body?.success == true && body.data != null) {
            val data = body.data
            keyStoreManager.saveTokens(data.accessToken, data.refreshToken)
            applyVerifiedEntitlement(data.device.tier, data.device.passExpiresAt)
            return@withContext data.accessToken
        }
        throw IllegalStateException("Device registration failed")
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

    suspend fun refreshVerifiedEntitlement(): Boolean = withContext(Dispatchers.IO) {
        val token = keyStoreManager.getAccessToken() ?: return@withContext false
        val response = api.getDeviceProfile("Bearer $token")
        val profile = response.body()
        if (!response.isSuccessful || profile?.success != true || profile.data == null) return@withContext false
        applyVerifiedEntitlement(profile.data.tier, profile.data.passExpiresAt)
        _userTier.value != "FREE"
    }

    private fun applyVerifiedEntitlement(tier: String, expiresAt: String?) {
        require(tier in setOf("FREE", "VIP", "REWARDED_PASS")) { "Invalid entitlement response" }
        verifiedEntitlementExpiresAtMs = expiresAt?.let { java.time.Instant.parse(it).toEpochMilli() }
        _userTier.value = tier
        checkRewardedPassExpiry()
    }

    fun checkRewardedPassExpiry() {
        if (verifiedEntitlementExpiresAtMs?.let { it <= System.currentTimeMillis() } == true) _userTier.value = "FREE"
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

    fun getNextNodeInSameLocation(currentServer: Server): Server? {
        val allServers = _servers.value
        val sameLocation = allServers.filter {
            it.countryCode.uppercase() == currentServer.countryCode.uppercase() &&
            it.city.lowercase() == currentServer.city.lowercase() &&
            it.id != currentServer.id
        }
        return sameLocation.firstOrNull { !it.isPremiumOnly || _userTier.value != "FREE" }
    }
}
