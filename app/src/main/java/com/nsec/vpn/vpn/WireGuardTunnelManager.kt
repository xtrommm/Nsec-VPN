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
import java.net.Inet4Address
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
    fun startConnect(server: Server, dnsServers: List<String> = listOf("1.1.1.1", "1.0.0.1"),
                     excludedApps: Set<String> = emptySet(), onNeedsPermission: (Intent) -> Unit) {
        if (_vpnState.value is VpnState.Connecting || _vpnState.value is VpnState.Connected || _vpnState.value is VpnState.Disconnecting) return
        VpnService.prepare(context)?.let { onNeedsPermission(it); return }
        lastExcludedApps = excludedApps; lastDns = dnsServers
        reconnectCount = 0
        _vpnState.value = VpnState.Connecting
        connectJob = scope.launch { connectWithFailover(server, dnsServers, excludedApps) }
    }
    private suspend fun connectWithFailover(initial: Server, dns: List<String>, excludedApps: Set<String>) {
        val visited = mutableSetOf<String>()
        var candidate: Server? = initial
        repeat(MAX_CONNECT_ATTEMPTS) {
            val server = candidate ?: return@repeat
            visited.add(server.id)
            activeServer = server
            _vpnState.value = VpnState.Connecting
            _vpnStats.value = VpnStats(); _connectedNode.value = null
            lastApiResponseSuccess = false; lastError = null
            try {
                val keyPair = keyStoreManager.getOrCreateWireGuardKeyPair()
                val publicKey = keyPair.publicKey.toBase64()
                activeClientPublicKey = publicKey
                val tc = TunnelNegotiation.establish(prepare = {
                val (tc, peerId) = vpnRepository.fetchTunnelConfig(server, publicKey)
                lastApiResponseSuccess = true; lastApiStatusCode = 200; activePeerId = peerId
                val result = validateTunnelConfig(tc)
                require(result.isValid) { result.errorMessage ?: "Invalid server data" }
                val addresses = InetAddress.getAllByName(tc.serverEndpoint.substringBeforeLast(':'))
                require(addresses.isNotEmpty() && addresses.all { it is Inet4Address && !isPrivateOrDocumentationIp(it.hostAddress.orEmpty()) }) { "Server endpoint must resolve to public IPv4" }
                require(addresses.any { it.hostAddress == tc.publicIpv4 }) { "Node public IP does not match endpoint" }
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
                connectionStartTime = SystemClock.elapsedRealtime()
                }, handshake = { tc -> waitForHandshakeOrTimeout(HANDSHAKE_TIMEOUT_MS, tc.serverPublicKey) })
                _connectedNode.value = tc
                activeServer = vpnRepository.servers.value.firstOrNull { it.id == tc.nodeId } ?: server
                _vpnState.value = VpnState.Connected
                updateNotification(tc.nodeName ?: server.name)
                startStatsMonitoring(tc.serverPublicKey)
                return
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { teardown() }; throw cancelled
            } catch (error: Exception) {
                lastError = when (error) {
                    is java.net.SocketTimeoutException -> "Couldn't reach the server"
                    is VpnApiException -> error.message
                    is IllegalArgumentException -> "Invalid server configuration"
                    else -> "Couldn't establish the VPN connection"
                }
                if (error is VpnApiException) lastApiStatusCode = error.statusCode
                teardown()
                candidate = FailoverPolicy.next(initial, visited, vpnRepository.servers.value,
                    vpnRepository.userTier.value, MAX_CONNECT_ATTEMPTS)
                if (candidate == null) {
                    _vpnState.value = VpnState.Error(lastError ?: "Couldn't reach the server"); return
                }
                delay(1000L * (it + 1))
            }
        }
        _vpnState.value = VpnState.Error(lastError ?: "Couldn't reach the server")
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
            var previousRx = 0L; var previousTx = 0L
            var previousTime = SystemClock.elapsedRealtime()
            while (isActive && _vpnState.value is VpnState.Connected) {
                delay(1000)
                try {
                    val stats = backend.getStatistics(tunnel)
                    val peer = stats.peer(Key.fromBase64(publicKey))
                    val handshake = peer?.latestHandshakeEpochMillis() ?: 0
                    latestHandshakeAgeSeconds = if (handshake > 0) (System.currentTimeMillis() - handshake).coerceAtLeast(0) / 1000 else Long.MAX_VALUE
                    if (backend.getState(tunnel) != Tunnel.State.UP || latestHandshakeAgeSeconds > STALE_HANDSHAKE_SECONDS) { recoverStaleTunnel(); return@launch }
                    val now = SystemClock.elapsedRealtime()
                    val seconds = (now - previousTime).coerceAtLeast(1) / 1000f
                    val rx = stats.totalRx(); val tx = stats.totalTx()
                    val duration = (now - connectionStartTime) / 1000
                    _vpnStats.value = VpnStats(duration, (rx - previousRx).coerceAtLeast(0) * 8 / seconds / 1_000_000f,
                        (tx - previousTx).coerceAtLeast(0) * 8 / seconds / 1_000_000f, rx, tx)
                    previousRx = rx; previousTx = tx; previousTime = now
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
        _connectedNode.value = null
        _vpnState.value = VpnState.Connecting
        statsJob?.cancel()
        connectJob = scope.launch {
            teardown()
            if (reconnectCount >= MAX_RECONNECTS) { _vpnState.value = VpnState.Error("Couldn't reach the server"); return@launch }
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
        try { backend.setState(tunnel, Tunnel.State.DOWN, null) } catch (_: Exception) { /* Already down. */ }
        stopping = false
        val id = activePeerId; val key = activeClientPublicKey
        if (id != null && key != null) vpnRepository.notifyDisconnect(id, key)
        activePeerId = null; activeTunnelConfig = null; _connectedNode.value = null
        context.stopService(Intent(context, NsecVpnService::class.java))
    }
    @Synchronized
    fun disconnect() {
        if (_vpnState.value is VpnState.Disconnected || _vpnState.value is VpnState.Disconnecting) return
        _vpnState.value = VpnState.Disconnecting
        statsJob?.cancel()
        val job = connectJob; connectJob = null
        scope.launch {
            job?.cancelAndJoin(); teardown()
            activeClientPublicKey = null; activeServer = null
            _vpnStats.value = VpnStats(); _vpnState.value = VpnState.Disconnected
        }
    }
    fun getDiagnosticsSummary(): String {
        if (!BuildConfig.DEBUG) return ""
        return "Node: ${activeTunnelConfig?.nodeName.orEmpty()}\nEndpoint: ${activeTunnelConfig?.serverEndpoint.orEmpty()}\nTunnel address: ${activeTunnelConfig?.clientAddress.orEmpty()}\nHandshake age: $latestHandshakeAgeSeconds s\nRX: ${_vpnStats.value.totalBytesReceived}\nTX: ${_vpnStats.value.totalBytesSent}\nAPI status: $lastApiStatusCode\nError: ${lastError.orEmpty()}"
    }
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
        } catch (_: Exception) { TunnelValidationResult(false, "Invalid server tunnel configuration") }
    }
}

object HandshakeGate {
    fun confirmed(handshakeMillis: Long, nowMillis: Long): Boolean = handshakeMillis > 0 && nowMillis - handshakeMillis in 0..15_000
}
class VpnApiException(val statusCode: Int, message: String) : IllegalStateException(message)
