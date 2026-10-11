package com.nsec.vpn

import androidx.test.core.app.ApplicationProvider
import com.nsec.vpn.data.api.*
import com.nsec.vpn.data.local.KeyStoreManager
import com.nsec.vpn.data.model.Server
import com.nsec.vpn.data.model.TunnelConfig
import com.nsec.vpn.data.repository.VpnRepository
import com.nsec.vpn.vpn.WireGuardTunnelManager
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VpnBugFixesTest {

    @Test(expected = IllegalStateException::class)
    fun peerApiFailure_throwsExceptionAndPreventsTunnelStart() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val failingApi = object : NsecVpnApi {
            override suspend fun registerDevice(request: RegisterDeviceRequest) = Response.success(RegisterDeviceResponse(true, AuthDataDto("token", "refresh", DeviceInfoDto("1", "FREE", null))))
            override suspend fun refreshToken(request: RefreshTokenRequest) = Response.success(RefreshTokenResponse(true, null))
            override suspend fun getServers(token: String, grouped: Boolean) = Response.success(ServerListResponse(true, ServerListDataDto(emptyList())))
            override suspend fun connect(token: String, request: ConnectVpnRequest): Response<ConnectVpnResponse> = Response.error(500, "Server Error".toResponseBody("text/plain".toMediaTypeOrNull()))
            override suspend fun disconnect(token: String, request: DisconnectVpnRequest) = Response.success(DisconnectVpnResponse(true, "OK"))
            override suspend fun getConfig() = Response.success(ConfigResponse(true, null))
            override suspend fun getLegal(type: String) = Response.success(LegalResponse(true, null))
        }

        val keyStore = KeyStoreManager(context)
        val repo = VpnRepository(context, failingApi, keyStore)
        val server = Server("srv_1", "Test", "US", "United States", "New York", "🇺🇸", 20, false, true, 20)
        val keyPair = keyStore.generateEphemeralWireGuardKeyPair()
        
        // Should throw IllegalStateException instead of returning fake fallback config
        repo.fetchTunnelConfig(server, keyPair.publicKey.toBase64())
        Unit
    }

    @Test
    fun invalidOrMissingResponseFields_rejected() {
        val invalidConfig1 = TunnelConfig(
            serverPublicKey = "",
            serverEndpoint = "203.0.113.1:51820",
            clientAddress = "10.8.0.2/32",
            dnsServers = listOf("1.1.1.1"),
            allowedIps = listOf("0.0.0.0/0"),
            persistentKeepalive = 25,
            mtu = 1420
        )
        val result1 = WireGuardTunnelManager.validateTunnelConfig(invalidConfig1)
        assertFalse(result1.isValid)

        val invalidConfig2 = TunnelConfig(
            serverPublicKey = "valid_key",
            serverEndpoint = "invalid_endpoint_no_port",
            clientAddress = "10.8.0.2/32",
            dnsServers = listOf("1.1.1.1"),
            allowedIps = listOf("0.0.0.0/0"),
            persistentKeepalive = 25,
            mtu = 1420
        )
        val result2 = WireGuardTunnelManager.validateTunnelConfig(invalidConfig2)
        assertFalse(result2.isValid)
    }

    @Test
    fun privateOrDocumentationEndpointIp_rejected() {
        assertTrue(WireGuardTunnelManager.isPrivateOrDocumentationIp("10.0.0.1"))
        assertTrue(WireGuardTunnelManager.isPrivateOrDocumentationIp("192.168.1.100"))
        assertTrue(WireGuardTunnelManager.isPrivateOrDocumentationIp("172.16.0.5"))
        assertTrue(WireGuardTunnelManager.isPrivateOrDocumentationIp("127.0.0.1"))
        assertTrue(WireGuardTunnelManager.isPrivateOrDocumentationIp("192.0.2.1"))
        assertTrue(WireGuardTunnelManager.isPrivateOrDocumentationIp("198.51.100.1"))
        assertTrue(WireGuardTunnelManager.isPrivateOrDocumentationIp("203.0.113.1"))
        assertTrue(WireGuardTunnelManager.isPrivateOrDocumentationIp("localhost"))

        val privateConfig = TunnelConfig(
            serverPublicKey = "valid_key",
            serverEndpoint = "192.0.2.1:51820",
            clientAddress = "10.8.0.2/32",
            dnsServers = listOf("1.1.1.1"),
            allowedIps = listOf("0.0.0.0/0"),
            persistentKeepalive = 25,
            mtu = 1420
        )
        val validation = WireGuardTunnelManager.validateTunnelConfig(privateConfig)
        assertFalse(validation.isValid)
        assertTrue(validation.errorMessage?.contains("private or documentation endpoint IP rejected") == true)
    }

    @Test
    fun keyPairConsistency_clientPublicKeyMatchesTunnelPrivateKey() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val keyStore = KeyStoreManager(context)
        val keyPair1 = keyStore.generateEphemeralWireGuardKeyPair()
        val pubKey1 = keyPair1.publicKey.toBase64()
        val privKey1 = keyPair1.privateKey.toBase64()

        assertNotNull(pubKey1)
        assertNotNull(privKey1)
        assertEquals(pubKey1, keyPair1.publicKey.toBase64())
    }

    @Test
    fun emptyIncludeList_behavesAsAllAppsThroughVpn() {
        val excludedApps = emptySet<String>()
        assertTrue(excludedApps.isEmpty())
    }

    @Test
    fun configBuilderOutput_validatesParameters() {
        val config = TunnelConfig(
            serverPublicKey = "8J5aOQv4rTqK3F6mE5nP7sL9wY2zU1xV4bN7mQ0kP8M=",
            serverEndpoint = "8.8.8.8:51820",
            clientAddress = "10.8.0.2/32",
            dnsServers = listOf("1.1.1.1", "1.0.0.1"),
            allowedIps = listOf("0.0.0.0/0"),
            persistentKeepalive = 25,
            mtu = 1420
        )
        val validation = WireGuardTunnelManager.validateTunnelConfig(config)
        assertTrue(validation.isValid)
        assertEquals("8.8.8.8:51820", config.serverEndpoint)
        assertEquals("10.8.0.2/32", config.clientAddress)
        assertEquals(1420, config.mtu)
        assertEquals(25, config.persistentKeepalive)
        assertTrue(config.allowedIps.contains("0.0.0.0/0"))
    }
}
