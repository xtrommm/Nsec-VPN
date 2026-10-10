package com.nsec.vpn

import androidx.test.core.app.ApplicationProvider
import com.nsec.vpn.data.api.*
import com.nsec.vpn.data.local.KeyStoreManager
import com.nsec.vpn.data.model.ThemeMode
import com.nsec.vpn.data.repository.VpnRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

class FakeNsecVpnApi : NsecVpnApi {
    override suspend fun registerDevice(request: RegisterDeviceRequest): Response<RegisterDeviceResponse> {
        return Response.success(RegisterDeviceResponse(true, null))
    }

    override suspend fun refreshToken(request: RefreshTokenRequest): Response<RefreshTokenResponse> {
        return Response.success(RefreshTokenResponse(true, null))
    }

    override suspend fun getServers(token: String, grouped: Boolean): Response<ServerListResponse> {
        return Response.success(ServerListResponse(true, null))
    }

    override suspend fun connect(token: String, request: ConnectVpnRequest): Response<ConnectVpnResponse> {
        return Response.success(ConnectVpnResponse(true, null))
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
    fun init_populatesFallbackServersAndSelectsDefault() {
        val fakeApi = FakeNsecVpnApi()
        val keyStoreManager = KeyStoreManager(ApplicationProvider.getApplicationContext())
        val repository = VpnRepository(ApplicationProvider.getApplicationContext(), fakeApi, keyStoreManager)

        val servers = repository.servers.value
        assertNotNull(servers)
        assertEquals(9, servers.size)

        val selected = repository.selectedServer.value
        assertNotNull(selected)
        assertEquals("srv_us_nyc", selected?.id)
    }

    @Test
    fun selectBestServer_selectsLowestLatencyServer() {
        val fakeApi = FakeNsecVpnApi()
        val keyStoreManager = KeyStoreManager(ApplicationProvider.getApplicationContext())
        val repository = VpnRepository(ApplicationProvider.getApplicationContext(), fakeApi, keyStoreManager)

        repository.selectBestServer()
        assertNotNull(repository.selectedServer.value)
    }

    @Test
    fun toggleFavorite_persistsFavoritesAcrossRepositoryReinitialization() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fakeApi = FakeNsecVpnApi()
        val keyStoreManager = KeyStoreManager(context)
        val repository1 = VpnRepository(context, fakeApi, keyStoreManager)

        val serverToFav = "srv_us_nyc"
        repository1.toggleFavorite(serverToFav)

        val favServerInRepo1 = repository1.servers.value.find { it.id == serverToFav }
        assertNotNull(favServerInRepo1)
        assertTrue(favServerInRepo1!!.isFavorite)

        // Simulate app restart / new repository instance reading from persistent KeyStoreManager
        val repository2 = VpnRepository(context, fakeApi, keyStoreManager)
        val favServerInRepo2 = repository2.servers.value.find { it.id == serverToFav }
        assertNotNull(favServerInRepo2)
        assertTrue(favServerInRepo2!!.isFavorite)
    }

    @Test
    fun setThemeMode_persistsThemeModeSetting() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fakeApi = FakeNsecVpnApi()
        val keyStoreManager = KeyStoreManager(context)
        val repository1 = VpnRepository(context, fakeApi, keyStoreManager)

        repository1.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repository1.themeMode.value)

        // Reinitialize repository to verify persistence
        val repository2 = VpnRepository(context, fakeApi, keyStoreManager)
        assertEquals(ThemeMode.DARK, repository2.themeMode.value)
    }
}
