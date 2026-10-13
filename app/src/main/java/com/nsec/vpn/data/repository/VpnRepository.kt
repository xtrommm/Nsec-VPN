package com.nsec.vpn.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.nsec.vpn.BuildConfig
import com.nsec.vpn.data.local.ServerCatalogCache
import com.nsec.vpn.data.api.ServerItemDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import com.nsec.vpn.data.api.NsecVpnApi
import com.nsec.vpn.data.api.RegisterDeviceRequest
import com.nsec.vpn.data.local.KeyStoreManager
import com.nsec.vpn.data.model.DnsProvider
import com.nsec.vpn.data.model.Server
import com.nsec.vpn.data.model.LocationSelection
import com.nsec.vpn.data.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nsec.vpn.data.network.NodeLatencyProbe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.update

class VpnRepository(
    private val context: Context,
    private val api: NsecVpnApi,
    private val keyStoreManager: KeyStoreManager
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val refreshMutex = Mutex()
    private val catalogCache = ServerCatalogCache(context)
    private var latencyJob: Job? = null
    private val _deviceIdentity = MutableStateFlow(keyStoreManager.getOrCreateDeviceId())
    val deviceIdentity: StateFlow<String> = _deviceIdentity.asStateFlow()
    private val _serversLoading = MutableStateFlow(false)
    val serversLoading: StateFlow<Boolean> = _serversLoading.asStateFlow()
    private val _usingCachedServers = MutableStateFlow(false)
    val usingCachedServers: StateFlow<Boolean> = _usingCachedServers.asStateFlow()
    private val _serverListError = MutableStateFlow<String?>(null)
    val serverListError: StateFlow<String?> = _serverListError.asStateFlow()
    private val _servers = MutableStateFlow<List<Server>>(emptyList())
    val servers: StateFlow<List<Server>> = _servers.asStateFlow()

    private val _selectedServer = MutableStateFlow<Server?>(null)
    val selectedServer: StateFlow<Server?> = _selectedServer.asStateFlow()

    private val _selectedDns = MutableStateFlow(DnsProvider.AUTOMATIC)
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

    private var cacheRestoreJob: Job? = null

    init {
        _favoriteServerIds.value = keyStoreManager.getFavoriteServerIds()

        val savedThemeName = keyStoreManager.getThemeMode()
        _themeMode.value = ThemeMode.entries.find { it.name == savedThemeName } ?: ThemeMode.SYSTEM

        val savedDnsName = keyStoreManager.getSelectedDns()
        if (savedDnsName != null) {
            _selectedDns.value = DnsProvider.entries.find { it.name == savedDnsName } ?: DnsProvider.AUTOMATIC
        }

        _killSwitchEnabled.value = keyStoreManager.isKillSwitchEnabled()
        _splitTunnelingEnabled.value = keyStoreManager.isSplitTunnelingEnabled()
        _excludedPackages.value = keyStoreManager.getExcludedPackages()
        _autoConnectEnabled.value = keyStoreManager.isAutoConnectEnabled()

        cacheRestoreJob = repositoryScope.launch {
            val cached = catalogCache.read()
            _servers.value = mapServers(cached)
            _usingCachedServers.value = cached.isNotEmpty()

            val savedServerId = keyStoreManager.getSelectedServerId()
            _selectedServer.value = _servers.value.firstOrNull { it.id == savedServerId }
                ?: _servers.value.firstOrNull { it.isRecommended }
                ?: _servers.value.firstOrNull()

        }
        repositoryScope.launch {
            cleanUninstalledExcludedPackages(context)
        }
    }

    suspend fun refreshServers(refreshLatency: Boolean = true) = withContext(Dispatchers.IO) {
        cacheRestoreJob?.join()
        if (!refreshMutex.tryLock()) return@withContext
        _serversLoading.value = true
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
                ServerCatalogCache.validate(dtos)
                val mapped = mapServers(dtos)
                catalogCache.write(dtos)
                _usingCachedServers.value = false
                _serverListError.value = null
                _servers.value = mapped
                repositoryScope.launch { try { refreshVerifiedEntitlement() } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Retain verified state. */ } }

                val currentSelected = _selectedServer.value
                _selectedServer.value = currentSelected?.let { selected -> mapped.firstOrNull { it.id == selected.id } }
                    ?: mapped.firstOrNull { it.isRecommended } ?: mapped.firstOrNull()
            } else { _serverListError.value = "Server List Unavailable (${response.code()})" }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            _serverListError.value = "Server List Unavailable; Check Your Internet Connection"
            // No fallback to mock servers. Keep existing servers or empty list.
        } finally {
            _serversLoading.value = false
            refreshMutex.unlock()
        }
        if (refreshLatency) {
            latencyJob?.cancel()
            latencyJob = repositoryScope.launch { measurePings() }
        }
    }

    private fun mapServers(dtos: List<ServerItemDto>): List<Server> = dtos.map { dto ->
        Server(dto.id, dto.name, dto.countryCode, dto.countryName, dto.city, dto.flagEmoji,
            dto.currentLoad, dto.isPremiumOnly, dto.isRecommended,
            isFavorite = _favoriteServerIds.value.contains(dto.id), totalNodes = dto.totalNodes,
            publicIpv4 = dto.publicIpv4, loadUpdatedAt = dto.loadUpdatedAt,
            pingMs = _servers.value.firstOrNull { it.id == dto.id && it.publicIpv4 == dto.publicIpv4 }?.pingMs)
    }

    suspend fun fetchTunnelConfig(
        server: Server,
        clientPublicKey: String,
        excludedNodeIds: Set<String> = emptySet()
    ): Pair<com.nsec.vpn.data.model.TunnelConfig, String?> = withContext(Dispatchers.IO) {
        var token = keyStoreManager.getAccessToken() ?: authenticateDevice()
        val latencies = _servers.value.filter { LocationSelection.sameLocation(it, server) }
            .mapNotNull { node -> node.pingMs?.let { com.nsec.vpn.data.api.NodeLatencyDto(node.id, it.coerceIn(0, 60000)) } }.take(1000)
        val request = com.nsec.vpn.data.api.ConnectVpnRequest(server.id, clientPublicKey, locationSelection = true,
            nodeLatencies = latencies, excludedNodeIds = excludedNodeIds.toList())
        var response = api.connect("Bearer $token", request)

        if (response.code() == 401) {
            val newToken = tryRefreshToken()
            if (newToken != null) {
                token = newToken
                response = api.connect("Bearer $token", request)
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
                "ALL_SERVERS_FULL" -> "All Servers Is Full, Try Again Later"
                "SERVER_FULL" -> "Server Is Full"
                "MAINTENANCE_MODE" -> "VPN Maintenance Is In Progress. Please Try Again Later."
                "NODE_DISABLED" -> "Server Is Disabled"
                "NODE_OFFLINE", "AGENT_UNREACHABLE" -> "Server Is Offline"
                "TIER_UPGRADE_REQUIRED" -> "This Server Requires An Active Premium Pass"
                else -> "Peer Creation Failed (${response.code()})"
            }, code)
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



    suspend fun measurePings() = withContext(Dispatchers.IO) {
        val limit = Semaphore(4)
        val measured = coroutineScope {
            _servers.value.map { server -> async {
                limit.withPermit {
                    server.id to (server.publicIpv4 to NodeLatencyProbe.measure(server.publicIpv4))
                }
            } }.awaitAll().toMap()
        }
        _servers.update { current -> current.map { server ->
            val result = measured[server.id]
            if (result != null && result.first == server.publicIpv4) server.copy(pingMs = result.second)
            else server
        } }
        _selectedServer.update { selected ->
            selected?.let { previous -> _servers.value.find { it.id == previous.id } ?: previous }
        }
    }

    private suspend fun <T> recoveryAuthenticated(call: suspend (String) -> retrofit2.Response<T>): retrofit2.Response<T> {
        val token = keyStoreManager.getAccessToken() ?: authenticateDevice()
        val response = call("Bearer $token")
        if (response.code() == 401) {
            val code = try { org.json.JSONObject(response.errorBody()?.source()?.peek()?.readUtf8().orEmpty()).optString("error") } catch (_: Exception) { "" }
            if (code in setOf("TOKEN_INVALID", "TOKEN_REVOKED", "UNAUTHORIZED")) {
                val renewed = tryRefreshToken()
                if (renewed != null) return call("Bearer $renewed")
            }
        }
        return response
    }

    private fun recoveryFailure(response: retrofit2.Response<*>): Nothing {
        val code = try { org.json.JSONObject(response.errorBody()?.string().orEmpty()).optString("error") } catch (_: Exception) { "" }
        throw IllegalStateException(when (code) {
            "INVALID_RECOVERY_PROOF" -> "Device ID Or Recovery PIN Is Incorrect. Set A PIN Before Clearing Data."
            "RECOVERY_LOCKED" -> "Recovery Is Locked. Try Again In 15 Minutes."
            "VALIDATION_ERROR" -> "Use A Strong Recovery PIN With 8–12 Digits."
            "AUTH_RATE_LIMIT", "TOO_MANY_REQUESTS" -> "Too Many Attempts. Please Wait And Retry."
            "TOKEN_INVALID", "TOKEN_REVOKED", "UNAUTHORIZED" -> "Refresh Your Device Session And Retry."
            else -> "Recovery Unavailable (${response.code()}). Check The Backend Update And Retry."
        })
    }

    suspend fun recoveryConfigured(): Boolean = withContext(Dispatchers.IO) {
        val response = recoveryAuthenticated { token -> com.nsec.vpn.core.network.NetworkModule.recoveryApi.status(token) }
        val body = response.body()
        if (!response.isSuccessful || body?.success != true || body.data == null) recoveryFailure(response)
        body.data.configured
    }

    suspend fun setRecoveryPin(pin: String, currentPin: String?) = withContext(Dispatchers.IO) {
        val response = recoveryAuthenticated { token -> com.nsec.vpn.core.network.NetworkModule.recoveryApi.setPin(token,
            com.nsec.vpn.data.api.SetRecoveryPinRequest(pin, currentPin)) }
        if (!response.isSuccessful || response.body()?.success != true) recoveryFailure(response)
    }

    suspend fun recoverDevice(deviceId: String, pin: String) = withContext(Dispatchers.IO) {
        val bytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val secret = bytes.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        val response = com.nsec.vpn.core.network.NetworkModule.recoveryApi.recover(
            com.nsec.vpn.data.api.RecoverDeviceRequest(deviceId, pin, secret))
        val body = response.body()
        if (!response.isSuccessful || body?.success != true || body.data == null) recoveryFailure(response)
        val data = body.data
        keyStoreManager.restoreDeviceSession(deviceId, secret, data.accessToken, data.refreshToken)
        _deviceIdentity.value = deviceId
        applyVerifiedEntitlement(data.device.tier, data.device.passExpiresAt)
        refreshServers(refreshLatency = false)
    }

    suspend fun authenticateDevice(): String = withContext(Dispatchers.IO) {
        val deviceId = keyStoreManager.getOrCreateDeviceId()
        val res = api.registerDevice(
            RegisterDeviceRequest(
                deviceId = deviceId,
                deviceModel = Build.MODEL,
                osVersion = Build.VERSION.RELEASE,
                appVersion = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE,
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
        throw IllegalStateException("Device Registration Failed")
    }

    fun selectServer(server: Server) {
        _selectedServer.value = server
        keyStoreManager.saveSelectedServerId(server.id)
    }

    fun selectBestServer() {
        val best = LocationSelection.ranked(_servers.value, _userTier.value).firstOrNull()
        if (best != null) {
            selectServer(best)
        }
    }

    fun toggleFavorite(serverId: String) {
        val currentFavs = _favoriteServerIds.value.toMutableSet()
        val anchor = _servers.value.firstOrNull { it.id == serverId } ?: return
        val ids = _servers.value.filter { LocationSelection.sameLocation(it, anchor) }.map { it.id }
        if (ids.any { it in currentFavs }) currentFavs.removeAll(ids.toSet()) else currentFavs.addAll(ids)
        _favoriteServerIds.value = currentFavs
        keyStoreManager.saveFavoriteServerIds(currentFavs)

        val updated = _servers.value.map {
            it.copy(isFavorite = currentFavs.contains(it.id))
        }
        _servers.value = updated
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        keyStoreManager.saveThemeMode(mode.name)
    }

    fun customDns(): String = keyStoreManager.getCustomDns()
    fun dnsServersForConnection(): List<String> = com.nsec.vpn.vpn.DnsPolicy.forIpv4Tunnel(_selectedDns.value, customDns())
    fun setCustomDns(raw: String) {
        val addresses = com.nsec.vpn.vpn.DnsPolicy.parseCustom(raw).joinToString(", ")
        com.nsec.vpn.vpn.DnsPolicy.forIpv4Tunnel(DnsProvider.CUSTOM, addresses)
        keyStoreManager.saveCustomDns(addresses)
        setDns(DnsProvider.CUSTOM)
    }
    fun setDns(dns: DnsProvider) {
        com.nsec.vpn.vpn.DnsPolicy.forIpv4Tunnel(dns, customDns())
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

    suspend fun requestServer(country: String, city: String, note: String?): String = withContext(Dispatchers.IO) {
        val cleanCountry = country.trim()
        val cleanCity = city.trim()
        require(cleanCountry.length in 2..80 && cleanCity.length in 2..80 && !Regex("[<>\\p{Cntrl}]").containsMatchIn(cleanCountry + cleanCity)) { "Enter A Valid Country And City (2–80 Characters)." }
        require((note?.trim()?.length ?: 0) <= 500) { "Your Note Must Be 500 Characters Or Fewer." }
        val remaining = keyStoreManager.getServerRequestCooldown() - System.currentTimeMillis()
        if (remaining > 0) throw IllegalStateException("Please Wait ${(remaining / 60000) + 1} Minutes Before Another Request.")
        val request = com.nsec.vpn.data.api.ServerRequestBody(cleanCountry, cleanCity, note?.trim()?.takeIf { it.isNotEmpty() })
        var token = keyStoreManager.getAccessToken() ?: authenticateDevice()
        var response = api.submitServerRequest("Bearer $token", request)
        if (response.code() == 401) {
            token = tryRefreshToken() ?: throw IllegalStateException("Please Reopen The App To Sign In Again.")
            response = api.submitServerRequest("Bearer $token", request)
        }
        val body = response.body()
        if (response.isSuccessful && body?.success == true && body.data != null) {
            keyStoreManager.saveServerRequestCooldown(System.currentTimeMillis() + 10 * 60000L)
            return@withContext "Request Submitted: ${body.data.city}, ${body.data.country} (Pending)."
        }
        if (response.code() == 429) {
            val seconds = response.headers()["Retry-After"]?.toLongOrNull()?.coerceIn(1, 600) ?: 600
            keyStoreManager.saveServerRequestCooldown(System.currentTimeMillis() + seconds * 1000)
            throw IllegalStateException("Please Wait ${(seconds + 59) / 60} Minutes Before Another Request.")
        }
        throw IllegalStateException(if (response.code() == 403) "This Device Is Blocked. Contact Support." else "Could Not Submit The Request (${response.code()}). Try Again.")
    }

    suspend fun prepareReward(): String = withContext(Dispatchers.IO) {
        var token = keyStoreManager.getAccessToken() ?: authenticateDevice()
        var response = api.prepareReward("Bearer $token")
        if (response.code() == 401) {
            token = tryRefreshToken() ?: throw IllegalStateException("Please Sign In Again")
            response = api.prepareReward("Bearer $token")
        }
        val result = response.body()
        if (!response.isSuccessful || result?.success != true || result.data == null)
            throw IllegalStateException("VIP Reward Preparation Is Unavailable. Please Try Again")
        result.data.ticketId
    }

    suspend fun claimReward(ticketId: String) = withContext(Dispatchers.IO) {
        keyStoreManager.savePendingRewardTicket(ticketId)
        repeat(11) { attempt ->
            var token = keyStoreManager.getAccessToken() ?: authenticateDevice()
            var response = api.claimReward("Bearer $token", com.nsec.vpn.data.api.ClaimRewardRequest(ticketId))
            if (response.code() == 401) {
                token = tryRefreshToken() ?: throw IllegalStateException("Please Sign In Again")
                response = api.claimReward("Bearer $token", com.nsec.vpn.data.api.ClaimRewardRequest(ticketId))
            }
            val result = response.body()
            if (response.code() == 202) {
                if (attempt < 10) {
                    kotlinx.coroutines.delay(3_000)
                    return@repeat
                }
                throw IllegalStateException("Ad-Free Is Active; VIP Is Waiting For Server Verification. It Will Retry Automatically.")
            }
            if (!response.isSuccessful || result?.success != true || result.data == null) {
                if (response.code() in listOf(400, 403, 404, 410)) keyStoreManager.savePendingRewardTicket(null)
                throw IllegalStateException("VIP Grant Unavailable. A Completed Reward Will Retry When The Service Returns")
            }
            applyVerifiedEntitlement(result.data.tier, result.data.passExpiresAt)
            keyStoreManager.savePendingRewardTicket(null)
            return@withContext
        }
    }

    private var nextRewardRetryElapsedMs = 0L
    suspend fun retryPendingReward() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now < nextRewardRetryElapsedMs) return
        nextRewardRetryElapsedMs = now + 300_000L
        val ticket = keyStoreManager.getPendingRewardTicket() ?: return
        try { claimReward(ticket) } catch (_: Exception) { /* Retained until retry or terminal rejection. */ }
    }

    suspend fun redeemPromo(code: String) = withContext(Dispatchers.IO) {
        val token = keyStoreManager.getAccessToken() ?: authenticateDevice()
        var response = api.redeemPromo("Bearer $token", com.nsec.vpn.data.api.RedeemPromoRequest(code.trim()))
        if (response.code() == 401) {
            val refreshed = tryRefreshToken() ?: throw IllegalStateException("Please Sign In Again")
            response = api.redeemPromo("Bearer $refreshed", com.nsec.vpn.data.api.RedeemPromoRequest(code.trim()))
        }
        val result = response.body()
        if (!response.isSuccessful || result?.success != true || result.data == null) {
            val error = try { org.json.JSONObject(response.errorBody()?.string().orEmpty()).optString("error") } catch (_: Exception) { "" }
            throw IllegalStateException(when (error) {
                "PROMO_ALREADY_REDEEMED" -> "You Already Redeemed This Code"
                "PROMO_EXPIRED" -> "This Code Has Expired"
                "PROMO_NOT_STARTED" -> "This Promotion Has Not Started Yet."
                "PROMO_ALREADY_VIP" -> "You Already Have VIP Without An Expiry; This Code Was Not Consumed."
                "PROMO_EXHAUSTED" -> "This Code Has Reached Its Redemption Limit"
                "PROMO_RATE_LIMIT" -> "Too Many Attempts. Try Again Later"
                "PROMO_INVALID" -> "Code Is Invalid Or Disabled"
                else -> "Could Not Redeem The Code. Please Try Again"
            })
        }
        applyVerifiedEntitlement(result.data.tier, result.data.passExpiresAt)
    }

    private fun applyVerifiedEntitlement(tier: String, expiresAt: String?) {
        require(tier in setOf("FREE", "VIP", "REWARDED_PASS")) { "Invalid Entitlement Response" }
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
