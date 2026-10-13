package com.nsec.vpn.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.SystemClock
import com.nsec.vpn.BuildConfig
import com.nsec.vpn.core.network.ServiceLocator
import com.nsec.vpn.data.local.KeyStoreManager
import com.nsec.vpn.data.model.Server
import com.nsec.vpn.data.model.TunnelConfig
import com.nsec.vpn.data.model.VpnState
import com.nsec.vpn.data.model.VpnStats
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.InetEndpoint
import com.wireguard.config.InetNetwork
import com.wireguard.crypto.Key
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

class WireGuardTunnelManager private constructor(private val context: android.app.Application) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val keyStoreManager = KeyStoreManager(context)
    private val vpnRepository by lazy { ServiceLocator.getInstance(context).vpnRepository }
    private val backend: Backend by lazy { GoBackend(context) }
    private val _vpnState = MutableStateFlow<VpnState>(VpnState.Disconnected)
    val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()
    private val _vpnStats = MutableStateFlow(VpnStats())
    val vpnStats: StateFlow<VpnStats> = _vpnStats.asStateFlow()
    private val _activeDns = MutableStateFlow<List<String>>(emptyList())
    val activeDns: StateFlow<List<String>> = _activeDns.asStateFlow()
    private val _connectedNode = MutableStateFlow<TunnelConfig?>(null)
    val connectedNode: StateFlow<TunnelConfig?> = _connectedNode.asStateFlow()
    // Retained diagnostics API; ads/API traffic always remains excluded.
    private val _includeAppInTunnel = MutableStateFlow(false)
    val includeAppInTunnel: StateFlow<Boolean> = _includeAppInTunnel.asStateFlow()
    var activeServer: Server? = null; private set
    var activePeerId: String? = null; private set
    var activeClientPublicKey: String? = null; private set
    var lastApiResponseSuccess = false; private set
    var lastApiStatusCode = 0; private set
    var lastError: String? = null; private set
    var latestHandshakeAgeSeconds = 0L; private set
    var activeTunnelConfig: TunnelConfig? = null; private set
    private val operations = TunnelOperations()
    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()
    private var connectJob: Job? = null
    private var statsJob: Job? = null
    private var connectionStartTime = 0L
    private var stopping = false
    private var reconnectCount = 0
    private var lastExcludedApps: Set<String> = emptySet()
    private var lastDns: List<String> = emptyList()
    private val tunnel = object : Tunnel {
        override fun getName() = "NsecVpnTunnel"
        override fun onStateChange(newState: Tunnel.State) {
            if (newState == Tunnel.State.DOWN && !stopping && _vpnState.value is VpnState.Connected) {
                scope.launch { recoverStaleTunnel() }
            }
        }
    }
    fun setIncludeAppInTunnel(@Suppress("UNUSED_PARAMETER") include: Boolean) { _includeAppInTunnel.value = false }

    @Synchronized
    fun startConnect(server: Server, dnsServers: List<String> = emptyList(),
                     excludedApps: Set<String> = emptySet(), onNeedsPermission: (Intent) -> Unit) {
        if (_vpnState.value is VpnState.Connecting || _vpnState.value is VpnState.Connected || _vpnState.value is VpnState.Disconnecting) return
        VpnService.prepare(context)?.let { onNeedsPermission(it); return }
        lastExcludedApps = excludedApps; lastDns = dnsServers
        reconnectCount = 0
        _vpnState.value = VpnState.Connecting
        connectJob = launchOperation { connectWithFailover(server, dnsServers, excludedApps) }
    }
    private fun launchOperation(previous: Job? = null, work: suspend () -> Unit): Job = scope.launch {
        previous?.cancelAndJoin()
        operations.exclusive {
            _operationError.value = null
            try { work() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                lastError = "VPN Operation Failed. The Tunnel May Still Be Active; Retry Disconnecting."
                _operationError.value = lastError
                val verified = _connectedNode.value
                val stillUp = runCatching { backend.getState(tunnel) == Tunnel.State.UP }.getOrDefault(false)
                if (stillUp && verified != null) {
                    _vpnState.value = VpnState.Connected
                    startStatsMonitoring(verified.serverPublicKey)
                } else _vpnState.value = VpnState.Error(lastError.orEmpty())
            }
        }
    }

    private suspend fun connectWithFailover(initial: Server, dns: List<String>, excludedApps: Set<String>) {
        val visited = mutableSetOf<String>()
        var candidate: Server? = com.nsec.vpn.data.model.LocationSelection.ranked(
            vpnRepository.servers.value.filter { com.nsec.vpn.data.model.LocationSelection.sameLocation(it, initial) },
            vpnRepository.userTier.value).firstOrNull() ?: initial
        repeat(MAX_CONNECT_ATTEMPTS) {
            val server = candidate ?: return@repeat
            var attemptedNodeId: String? = null
            activeServer = server
            _vpnState.value = VpnState.Connecting
            _vpnStats.value = VpnStats(); _connectedNode.value = null
            lastApiResponseSuccess = false; lastError = null
            try {
                val keyPair = keyStoreManager.getOrCreateWireGuardKeyPair()
                val publicKey = keyPair.publicKey.toBase64()
                activeClientPublicKey = publicKey
                val tc = TunnelNegotiation.establish(prepare = {
                val (tc, peerId) = vpnRepository.fetchTunnelConfig(server, publicKey, visited)
                attemptedNodeId = tc.nodeId ?: server.id
                lastApiResponseSuccess = true; lastApiStatusCode = 200; activePeerId = peerId
                val result = validateTunnelConfig(tc)
                require(result.isValid) { result.errorMessage ?: "Invalid Server Data" }
                val addresses = InetAddress.getAllByName(tc.serverEndpoint.substringBeforeLast(':'))
                EndpointPolicy.validate(addresses.toList(), tc.publicIpv4.orEmpty())
                activeTunnelConfig = tc
                tc
                }, activate = { tc ->
                val applications = mutableSetOf(context.packageName)
                excludedApps.forEach { pkg ->
                    try {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageInfo(pkg, 0)
                        applications.add(pkg)
                    } catch (_: android.content.pm.PackageManager.NameNotFoundException) { /* Removed app. */ }
                }
                val config = TunnelConfigBuilder.build(tc, keyPair.privateKey.toBase64(), context.packageName, applications, dns)
                updateNotification(tc.nodeName ?: server.name)
                backend.setState(tunnel, Tunnel.State.UP, config)
                _activeDns.value = dns.ifEmpty { tc.dnsServers }
                connectionStartTime = SystemClock.elapsedRealtime()
                }, handshake = { tc -> waitForHandshakeOrTimeout(HANDSHAKE_TIMEOUT_MS, tc.serverPublicKey) })
                _connectedNode.value = tc
                activeServer = vpnRepository.servers.value.firstOrNull { it.id == tc.nodeId } ?: server
                keyStoreManager.saveConnectionIntent(org.json.JSONObject()
                    .put("nodeId", activeServer!!.id).put("countryCode", activeServer!!.countryCode)
                    .put("city", activeServer!!.city).toString())
                _vpnState.value = VpnState.Connected
                updateNotification(tc.nodeName ?: server.name)
                startStatsMonitoring(tc.serverPublicKey)
                return
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { teardown() }; throw cancelled
            } catch (error: Exception) {
                visited.add(attemptedNodeId ?: server.id)
                lastError = when (error) {
                    is java.net.SocketTimeoutException -> "Couldn't Reach The Server"
                    is VpnApiException -> error.message
                    is IllegalArgumentException -> "Invalid Server Configuration"
                    else -> "Couldn't Establish The VPN Connection"
                }
                if (error is VpnApiException) lastApiStatusCode = error.statusCode
                teardown()
                if (error is VpnApiException && error.errorCode in setOf("ALL_SERVERS_FULL", "DEVICE_BLOCKED", "TIER_UPGRADE_REQUIRED", "MAINTENANCE_MODE", "APP_UPDATE_REQUIRED")) {
                    _vpnState.value = VpnState.Error(lastError ?: "Connection Failed")
                    return
                }
                candidate = FailoverPolicy.next(initial, visited, vpnRepository.servers.value,
                    vpnRepository.userTier.value, MAX_CONNECT_ATTEMPTS)
                if (candidate == null) {
                    _vpnState.value = VpnState.Error(lastError ?: "Couldn't Reach The Server"); return
                }
                delay(1000L * (it + 1))
            }
        }
        _vpnState.value = VpnState.Error(lastError ?: "Couldn't Reach The Server")
    }
    private suspend fun waitForHandshakeOrTimeout(timeoutMs: Long, publicKey: String): Boolean {
        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            currentCoroutineContext().ensureActive()
            val peer = backend.getStatistics(tunnel).peer(Key.fromBase64(publicKey))
            if (HandshakeGate.confirmed(peer?.latestHandshakeEpochMillis() ?: 0, System.currentTimeMillis())) return true
            delay(HANDSHAKE_POLL_MS)
        }
        return false
    }
    private fun startStatsMonitoring(publicKey: String) {
        statsJob?.cancel()
        statsJob = scope.launch {
            val sampler = TrafficCounterSampler()
            while (isActive && _vpnState.value is VpnState.Connected) {
                delay(1000)
                try {
                    val stats = backend.getStatistics(tunnel)
                    val peer = stats.peer(Key.fromBase64(publicKey))
                    val handshake = peer?.latestHandshakeEpochMillis() ?: 0
                    latestHandshakeAgeSeconds = if (handshake > 0) (System.currentTimeMillis() - handshake).coerceAtLeast(0) / 1000 else Long.MAX_VALUE
                    if (backend.getState(tunnel) != Tunnel.State.UP || latestHandshakeAgeSeconds > STALE_HANDSHAKE_SECONDS) { recoverStaleTunnel(); return@launch }
                    val now = SystemClock.elapsedRealtime()
                    val rx = stats.totalRx(); val tx = stats.totalTx()
                    val rate = sampler.sample(now, rx, tx)
                    val duration = (now - connectionStartTime) / 1000
                    _vpnStats.value = VpnStats(duration, rate.downloadMbps, rate.uploadMbps, rx, tx)
                    if (duration >= 300) reconnectCount = 0
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { recoverStaleTunnel(); return@launch }
            }
        }
    }
    @Synchronized
    private fun recoverStaleTunnel() {
        if (_vpnState.value !is VpnState.Connected) return
        val server = activeServer ?: return
        _vpnState.value = VpnState.Connecting
        statsJob?.cancel()
        connectJob = launchOperation {
            teardown()
            if (reconnectCount >= MAX_RECONNECTS) { _vpnState.value = VpnState.Error("Couldn't Reach The Server"); return@launchOperation }
            delay(1000L shl reconnectCount++)
            connectWithFailover(server, lastDns, lastExcludedApps)
        }
    }
    private fun updateNotification(name: String) {
        val intent = Intent(context, NsecVpnService::class.java).apply {
            action = NsecVpnService.ACTION_CONNECT; putExtra(NsecVpnService.EXTRA_SERVER_NAME, name)
        }
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
    }
    private suspend fun teardown() {
        stopping = true
        try {
            TunnelShutdown.stop(
                setDown = { backend.setState(tunnel, Tunnel.State.DOWN, null) },
                isDown = { backend.getState(tunnel) == Tunnel.State.DOWN }
            )
        } finally { stopping = false }
        val id = activePeerId; val key = activeClientPublicKey
        // The local TUN is confirmed DOWN. Remote lease cleanup must never delay the UI
        // or hold the tunnel-operation lock. The backend retains uncertain leases and
        // ignores stale handles if the user reconnects before this request completes.
        if (id != null && key != null) RemotePeerCleanup.enqueue(scope) {
            vpnRepository.notifyDisconnect(id, key)
        }
        activePeerId = null; activeTunnelConfig = null; _connectedNode.value = null; _activeDns.value = emptyList()
        context.stopService(Intent(context, NsecVpnService::class.java))
    }
    @Synchronized
    fun disconnect() {
        if (_vpnState.value is VpnState.Disconnected || _vpnState.value is VpnState.Disconnecting) return
        _vpnState.value = VpnState.Disconnecting
        statsJob?.cancel()
        val job = connectJob; connectJob = null
        launchOperation(previous = job) {
            keyStoreManager.saveConnectionIntent(null)
            teardown()
            activeClientPublicKey = null; activeServer = null
            _vpnStats.value = VpnStats(); _vpnState.value = VpnState.Disconnected
        }
    }
    @Synchronized
    fun updateDns(dns: List<String>) {
        require(dns.size <= 4 && dns.none { it.contains(':') }) { "This Node Needs Reachable IPv4 DNS Resolvers." }
        if (dns == lastDns) return
        lastDns = dns
        if (_vpnState.value !is VpnState.Connected && _vpnState.value !is VpnState.Connecting) return
        val server = activeServer ?: return
        val previous = connectJob
        statsJob?.cancel()
        _vpnState.value = VpnState.Connecting
        connectJob = launchOperation(previous = previous) {
            teardown()
            currentCoroutineContext().ensureActive()
            connectWithFailover(server, dns, lastExcludedApps)
        }
    }

    @Synchronized
    fun updateExcludedApps(excludedApps: Set<String>) {
        if (excludedApps == lastExcludedApps) return
        lastExcludedApps = excludedApps.toSet()
        if (_vpnState.value !is VpnState.Connected && _vpnState.value !is VpnState.Connecting) return
        val server = activeServer ?: return
        val previous = connectJob
        statsJob?.cancel()
        _vpnState.value = VpnState.Connecting
        connectJob = launchOperation(previous = previous) {
            teardown()
            currentCoroutineContext().ensureActive()
            connectWithFailover(server, lastDns, lastExcludedApps)
        }
    }

    /** Restart uses an encrypted intent and fresh backend negotiation, never a saved private config. */
    @Synchronized
    fun restoreConnection() {
        if (_vpnState.value !is VpnState.Disconnected || VpnService.prepare(context) != null) return
        // Reading/decrypting and network work stay off the main/always-on callback thread.
        if (connectJob?.isActive == true) return
        connectJob = scope.launch {
            val json = keyStoreManager.getConnectionIntent() ?: return@launch
            val target = try { org.json.JSONObject(json) } catch (_: Exception) {
                keyStoreManager.saveConnectionIntent(null); return@launch
            }
            val nodeId = target.optString("nodeId")
            val country = target.optString("countryCode")
            val city = target.optString("city")
            if (nodeId.isBlank() || country.length != 2 || city.isBlank()) {
                keyStoreManager.saveConnectionIntent(null); return@launch
            }
            operations.exclusive {
                if (_vpnState.value !is VpnState.Disconnected) return@exclusive
                _vpnState.value = VpnState.Connecting
                try {
                    val configManager = ServiceLocator.getInstance(context).remoteConfigManager
                    configManager.fetchRemoteConfig()
                    val config = configManager.config.value
                    ConnectionEligibility.blockedReason(configManager.loaded.value, config.maintenanceMode,
                        config.forceUpdate, config.minAppVersionCode, BuildConfig.VERSION_CODE)?.let { error(it) }
                    vpnRepository.refreshServers(refreshLatency = false)
                    val server = vpnRepository.servers.value.firstOrNull { it.id == nodeId }
                        ?: vpnRepository.servers.value.firstOrNull { it.countryCode == country && it.city == city }
                        ?: error("Previously Connected Location Is Unavailable.")
                    lastDns = vpnRepository.dnsServersForConnection()
                    lastExcludedApps = if (vpnRepository.splitTunnelingEnabled.value)
                        vpnRepository.excludedPackages.value else emptySet()
                    reconnectCount = 0
                    connectWithFailover(server, lastDns, lastExcludedApps)
                } catch (cancelled: CancellationException) {
                    withContext(NonCancellable) { teardown() }; throw cancelled
                } catch (error: Exception) {
                    _vpnState.value = VpnState.Error(error.message ?: "Couldn't Restore The VPN Connection.")
                }
            }
        }
    }

    fun getDiagnosticsSummary(): String {
        if (!BuildConfig.DEBUG) return ""
        return "Node: ${activeTunnelConfig?.nodeName.orEmpty()}\nEndpoint: ${activeTunnelConfig?.serverEndpoint.orEmpty()}\nTunnel address: ${activeTunnelConfig?.clientAddress.orEmpty()}\nHandshake age: $latestHandshakeAgeSeconds s\nRX: ${_vpnStats.value.totalBytesReceived}\nTX: ${_vpnStats.value.totalBytesSent}\nAPI status: $lastApiStatusCode\nError: ${lastError.orEmpty()}"
    }
    fun isApplicationTunneled(packageName: String): Boolean =
        _vpnState.value is VpnState.Connected && packageName != context.packageName &&
            packageName !in lastExcludedApps

    companion object {
        const val HANDSHAKE_TIMEOUT_MS = 15_000L
        const val HANDSHAKE_POLL_MS = 500L
        const val STALE_HANDSHAKE_SECONDS = 180L
        const val MAX_CONNECT_ATTEMPTS = 3
        const val MAX_RECONNECTS = 3
        @Volatile private var instance: WireGuardTunnelManager? = null
        fun getInstance(context: Context): WireGuardTunnelManager = instance ?: synchronized(this) {
            instance ?: WireGuardTunnelManager(context.applicationContext as android.app.Application).also { instance = it }
        }
        fun isPrivateOrDocumentationIp(host: String): Boolean {
            if (host.lowercase() in setOf("localhost", "::1")) return true
            val a = host.split('.').map { it.toIntOrNull() ?: return false }
            if (a.size != 4) return false
            return a.any { it !in 0..255 } || a[0] in setOf(0, 10, 127) || a[0] >= 224 ||
                (a[0] == 172 && a[1] in 16..31) || (a[0] == 192 && (a[1] == 168 || a[1] == 0)) ||
                (a[0] == 169 && a[1] == 254) || (a[0] == 100 && a[1] in 64..127) ||
                (a[0] == 198 && (a[1] in 18..19 || (a[1] == 51 && a[2] == 100))) || (a[0] == 203 && a[1] == 0 && a[2] == 113)
        }
        data class TunnelValidationResult(val isValid: Boolean, val errorMessage: String? = null)
        fun validateTunnelConfig(tc: TunnelConfig): TunnelValidationResult = try {
            val key = Key.fromBase64(tc.serverPublicKey)
            require(key.toBase64() == tc.serverPublicKey && tc.serverPublicKey != "A".repeat(43) + "=")
            val endpoint = InetEndpoint.parse(tc.serverEndpoint)
            require(endpoint.port in 1..65535 && endpoint.port != 51821)
            require(!isPrivateOrDocumentationIp(endpoint.host))
            val clientParts = tc.clientAddress.substringBefore('/').split('.').map { it.toIntOrNull() ?: -1 }
            require(tc.clientAddress.endsWith("/32") && clientParts.size == 4 && clientParts.all { it in 0..255 } &&
                (clientParts[0] == 10 || (clientParts[0] == 172 && clientParts[1] in 16..31) || (clientParts[0] == 192 && clientParts[1] == 168)))
            InetNetwork.parse(tc.clientAddress)
            require(tc.dnsServers.isNotEmpty())
            tc.dnsServers.forEach { require(it.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))); InetNetwork.parse("$it/32"); require(!isPrivateOrDocumentationIp(it)) }
            require(tc.allowedIps == listOf("0.0.0.0/0"))
            require(tc.mtu in 1280..1420 && tc.persistentKeepalive in 1..120)
            require(!tc.publicIpv4.isNullOrBlank() && tc.publicIpv4.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) && !isPrivateOrDocumentationIp(tc.publicIpv4))
            require(!tc.nodeId.isNullOrBlank() && !tc.nodeName.isNullOrBlank() && !tc.locationName.isNullOrBlank())
            TunnelValidationResult(true)
        } catch (_: Exception) { TunnelValidationResult(false, "Invalid Server Tunnel Configuration") }
    }
}

object HandshakeGate {
    fun confirmed(handshakeMillis: Long, nowMillis: Long): Boolean = handshakeMillis > 0 && nowMillis - handshakeMillis in 0..15_000
}
class VpnApiException(val statusCode: Int, message: String, val errorCode: String = "") : IllegalStateException(message)
