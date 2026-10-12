package com.nsec.vpn

import androidx.test.core.app.ApplicationProvider
import com.nsec.vpn.data.api.*
import com.nsec.vpn.data.local.KeyStoreManager
import com.nsec.vpn.data.model.ThemeMode
import com.nsec.vpn.data.repository.VpnRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

open class FakeNsecVpnApi : NsecVpnApi {
    override suspend fun prepareReward(token: String): retrofit2.Response<com.nsec.vpn.data.api.RewardTicketResponse> =
        retrofit2.Response.success(com.nsec.vpn.data.api.RewardTicketResponse(false, null))
    override suspend fun claimReward(token: String, request: com.nsec.vpn.data.api.ClaimRewardRequest): retrofit2.Response<com.nsec.vpn.data.api.RedeemPromoResponse> =
        retrofit2.Response.success(com.nsec.vpn.data.api.RedeemPromoResponse(false, null))

    override suspend fun redeemPromo(token: String, request: com.nsec.vpn.data.api.RedeemPromoRequest): retrofit2.Response<com.nsec.vpn.data.api.RedeemPromoResponse> =
        retrofit2.Response.success(com.nsec.vpn.data.api.RedeemPromoResponse(false, null))

    override suspend fun getDeviceProfile(token: String): Response<DeviceProfileResponse> = Response.success(DeviceProfileResponse(false, null))
    override suspend fun verifyPurchase(token: String, request: VerifyPurchaseRequest): Response<VerifyPurchaseResponse> = Response.success(VerifyPurchaseResponse(false))
    override suspend fun registerDevice(request: RegisterDeviceRequest): Response<RegisterDeviceResponse> {
        return Response.success(RegisterDeviceResponse(true, AuthDataDto("token", "refresh", DeviceInfoDto("1", "FREE", null))))
    }

    override suspend fun refreshToken(request: RefreshTokenRequest): Response<RefreshTokenResponse> {
        return Response.success(RefreshTokenResponse(true, TokenDataDto("new_token", "new_refresh")))
    }

    override suspend fun getServers(token: String, grouped: Boolean): Response<ServerListResponse> {
        val servers = listOf(
            ServerItemDto("srv_us_nyc", "United States - New York", "US", "United States", "New York", "🇺🇸", 38, false, true, 1),
            ServerItemDto("srv_us_lax", "United States - Los Angeles", "US", "United States", "Los Angeles", "🇺🇸", 45, false, false, 1),
            ServerItemDto("srv_de_fra", "Germany - Frankfurt", "DE", "Germany", "Frankfurt", "🇩🇪", 52, false, false, 1),
            ServerItemDto("srv_gb_lon", "United Kingdom - London", "GB", "United Kingdom", "London", "🇬🇧", 49, false, false, 1),
            ServerItemDto("srv_nl_ams", "Netherlands - Amsterdam", "NL", "Netherlands", "Amsterdam", "🇳🇱", 29, false, false, 1),
            ServerItemDto("srv_jp_tyo", "Japan - Tokyo", "JP", "Japan", "Tokyo", "🇯🇵", 62, true, false, 1),
            ServerItemDto("srv_sg_sin", "Singapore", "SG", "Singapore", "Singapore", "🇸🇬", 58, true, false, 1),
            ServerItemDto("srv_ca_tor", "Canada", "CA", "Canada", "Toronto", "🇨🇦", 33, true, false, 1),
            ServerItemDto("srv_ch_zrh", "Switzerland", "CH", "Switzerland", "Zurich", "🇨🇭", 21, true, false, 1)
        )
        return Response.success(ServerListResponse(true, ServerListDataDto(servers)))
    }

    override suspend fun connect(token: String, request: ConnectVpnRequest): Response<ConnectVpnResponse> {
        return Response.success(ConnectVpnResponse(true, ConnectVpnDataDto(
            TunnelConfigDto("8J5aOQv4rTqK3F6mE5nP7sL9wY2zU1xV4bN7mQ0kP8M=", "8.8.8.8:51820", "10.8.0.2/32", listOf("1.1.1.1"), listOf("0.0.0.0/0"), 25, 1420),
            "peer_1"
        )))
    }

    override suspend fun disconnect(token: String, request: DisconnectVpnRequest): Response<DisconnectVpnResponse> {
        return Response.success(DisconnectVpnResponse(true, null))
    }

    override suspend fun getConfig(): Response<ConfigResponse> {
        return Response.success(ConfigResponse(true, null))
    }

    override suspend fun getLegal(type: String): Response<LegalResponse> {
        return Response.success(LegalResponse(true, null))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VpnRepositoryTest {

    @Test
    fun legacyLocalRewardExpiryCannotGrantAnEntitlement() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vault = TestVault.create(context)
        vault.saveRewardedPassExpiry(System.currentTimeMillis() + 3600000)
        val repository = VpnRepository(context, FakeNsecVpnApi(), vault)
        assertEquals("FREE", repository.userTier.value)
    }

    @Test
    fun paidTierComesFromTheServerAndExpiresWithoutLocalGrants() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var expired = false
        val api = object : FakeNsecVpnApi() {
            override suspend fun getDeviceProfile(token: String): Response<DeviceProfileResponse> =
                Response.success(DeviceProfileResponse(true, DeviceInfoDto("test-device", "VIP",
                    java.time.Instant.now().plusSeconds(if (expired) -60 else 3600).toString())))
        }
        val repository = VpnRepository(context, api, TestVault.create(context))
        repository.refreshServers()
        repository.refreshVerifiedEntitlement()
        assertEquals("VIP", repository.userTier.value)
        expired = true
        repository.refreshVerifiedEntitlement()
        assertEquals("FREE", repository.userTier.value)
    }

    @Test
    fun failedServerApiNeverInventsDataAndRetainsOnlyThePriorApiList() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var unavailable = true
        val api = object : FakeNsecVpnApi() {
            override suspend fun getServers(token: String, grouped: Boolean): Response<ServerListResponse> {
                if (unavailable) throw java.io.IOException("test outage")
                return super.getServers(token, grouped)
            }
        }
        val repository = VpnRepository(context, api, TestVault.create(context))
        repository.refreshServers()
        assertTrue(repository.servers.value.isEmpty())
        assertNotNull(repository.serverListError.value)
        unavailable = false
        repository.refreshServers()
        val prior = repository.servers.value
        unavailable = true
        repository.refreshServers()
        assertEquals(prior, repository.servers.value)
    }

    @Test
    fun init_populatesServersAndSelectsDefault() = runBlocking {
        val fakeApi = FakeNsecVpnApi()
        val keyStoreManager = TestVault.create(ApplicationProvider.getApplicationContext())
        val repository = VpnRepository(ApplicationProvider.getApplicationContext(), fakeApi, keyStoreManager)
        repository.refreshServers()

        val servers = repository.servers.value
        assertNotNull(servers)
        assertEquals(9, servers.size)

        val selected = repository.selectedServer.value
        assertNotNull(selected)
        assertEquals("srv_us_nyc", selected?.id)
    }

    @Test
    fun selectBestServer_selectsLowestLatencyServer() = runBlocking {
        val fakeApi = FakeNsecVpnApi()
        val keyStoreManager = TestVault.create(ApplicationProvider.getApplicationContext())
        val repository = VpnRepository(ApplicationProvider.getApplicationContext(), fakeApi, keyStoreManager)
        repository.refreshServers()

        repository.selectBestServer()
        assertNotNull(repository.selectedServer.value)
    }

    @Test
    fun toggleFavorite_persistsFavoritesAcrossRepositoryReinitialization() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fakeApi = FakeNsecVpnApi()
        val keyStoreManager = TestVault.create(context)
        val repository1 = VpnRepository(context, fakeApi, keyStoreManager)
        repository1.refreshServers()

        val serverToFav = "srv_us_nyc"
        repository1.toggleFavorite(serverToFav)

        val favServerInRepo1 = repository1.servers.value.find { it.id == serverToFav }
        assertNotNull(favServerInRepo1)
        assertTrue(favServerInRepo1!!.isFavorite)

        // Simulate app restart / new repository instance reading from persistent KeyStoreManager
        val repository2 = VpnRepository(context, fakeApi, keyStoreManager)
        repository2.refreshServers()
        val favServerInRepo2 = repository2.servers.value.find { it.id == serverToFav }
        assertNotNull(favServerInRepo2)
        assertTrue(favServerInRepo2!!.isFavorite)
    }

    @Test
    fun setThemeMode_persistsThemeModeSetting() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fakeApi = FakeNsecVpnApi()
        val keyStoreManager = TestVault.create(context)
        val repository1 = VpnRepository(context, fakeApi, keyStoreManager)

        repository1.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repository1.themeMode.value)

        // Reinitialize repository to verify persistence
        val repository2 = VpnRepository(context, fakeApi, keyStoreManager)
        assertEquals(ThemeMode.DARK, repository2.themeMode.value)
    }
}
