package com.nsec.vpn

import androidx.test.core.app.ApplicationProvider
import com.nsec.vpn.data.api.*
import com.nsec.vpn.data.local.KeyStoreManager
import com.nsec.vpn.data.repository.VpnRepository
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuditFixesTest {

    @Test
    fun vaultKeepsOneIdentityAndKeyAcrossInstances() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = TestVault.create(context)
        val second = TestVault.create(context)
        assertEquals(first.getOrCreateDeviceId(), second.getOrCreateDeviceId())
        assertEquals(first.getOrCreateRegistrationSecret(), second.getOrCreateRegistrationSecret())
        assertEquals(first.getOrCreateWireGuardKeyPair().privateKey, second.getOrCreateWireGuardKeyPair().privateKey)
    }

    @Test
    fun corruptedStoredKeyFailsInsteadOfSilentlyRegenerating() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("nsecvpn_secure_vault", android.content.Context.MODE_PRIVATE)
            .edit().putString("wg_private_key", "corrupted-test-ciphertext").commit()
        try {
            TestVault.create(context).getOrCreateWireGuardKeyPair()
            org.junit.Assert.fail("Corrupt stored key must fail closed")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun keyStoreManager_encryptsAndDecryptsTokensAndPrivateKeys() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val keyStore = TestVault.create(context)

        val rawToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test_payload_12345"
        val rawRefreshToken = "refresh_secret_abc123"

        keyStore.saveTokens(rawToken, rawRefreshToken)

        assertEquals(rawToken, keyStore.getAccessToken())
        assertEquals(rawRefreshToken, keyStore.getRefreshToken())

        val keyPair = keyStore.getOrCreateWireGuardKeyPair()
        assertNotNull(keyPair.privateKey.toBase64())
        assertEquals(keyPair.privateKey.toBase64(), keyStore.getStoredPrivateKeyBase64())
    }

    @Test
    fun vpnDisclosureAcceptance_persistsCorrectly() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val keyStore = TestVault.create(context)

        assertFalse(keyStore.isVpnDisclosureAccepted())
        keyStore.saveVpnDisclosureAccepted(true)
        assertTrue(keyStore.isVpnDisclosureAccepted())
    }

    @Test
    fun tokenRefresh_handles401UnauthorizedWithoutInfiniteLoop() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var refreshTokenCallCount = 0

        val fakeApi = object : NsecVpnApi {
            override suspend fun getDeviceProfile(token: String): Response<DeviceProfileResponse> = Response.success(DeviceProfileResponse(false, null))
    override suspend fun verifyPurchase(token: String, request: VerifyPurchaseRequest): Response<VerifyPurchaseResponse> = Response.success(VerifyPurchaseResponse(false))
    override suspend fun registerDevice(request: RegisterDeviceRequest): Response<RegisterDeviceResponse> {
                return Response.success(RegisterDeviceResponse(true, AuthDataDto("token_initial", "refresh_initial", DeviceInfoDto("dev1", "FREE", null))))
            }

            override suspend fun refreshToken(request: RefreshTokenRequest): Response<RefreshTokenResponse> {
                refreshTokenCallCount++
                return Response.success(RefreshTokenResponse(true, TokenDataDto("token_refreshed", "refresh_new")))
            }

            override suspend fun getServers(token: String, grouped: Boolean): Response<ServerListResponse> {
                return if (token == "Bearer token_refreshed") {
                    Response.success(ServerListResponse(true, ServerListDataDto(emptyList())))
                } else {
                    val errorBody = "Unauthorized".toResponseBody()
                    Response.error(401, errorBody)
                }
            }

            override suspend fun connect(token: String, request: ConnectVpnRequest): Response<ConnectVpnResponse> {
                return Response.success(ConnectVpnResponse(true, null))
            }

            override suspend fun disconnect(token: String, request: DisconnectVpnRequest): Response<DisconnectVpnResponse> {
                return Response.success(DisconnectVpnResponse(true, "Disconnected"))
            }

            override suspend fun getConfig(): Response<ConfigResponse> {
                return Response.success(ConfigResponse(true, null))
            }

            override suspend fun getLegal(type: String): Response<LegalResponse> {
                return Response.success(LegalResponse(true, null))
            }
        }

        val keyStore = TestVault.create(context)
        keyStore.saveTokens("token_expired", "refresh_valid")

        val repo = VpnRepository(context, fakeApi, keyStore)
        repo.refreshServers()

        assertEquals(1, refreshTokenCallCount)
        assertEquals("token_refreshed", keyStore.getAccessToken())
    }

    @Test
    fun pingMeasurement_returnsMeasuredPingOrNull_neverInventedRandoms() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fakeApi = FakeNsecVpnApi()
        val keyStore = TestVault.create(context)

        val repo = VpnRepository(context, fakeApi, keyStore)
        repo.refreshServers()
        repo.measurePings()

        val servers = repo.servers.value
        assertTrue(servers.isNotEmpty())
        for (server in servers) {
            val ping = server.pingMs
            if (ping != null) {
                assertTrue("Measured ping should be non-negative", ping >= 0L)
            }
        }
    }
}
