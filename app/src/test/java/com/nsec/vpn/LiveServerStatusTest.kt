package com.nsec.vpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nsec.vpn.data.api.*
import com.nsec.vpn.data.repository.VpnRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveServerStatusTest {
    @Test fun silentRefreshUpdatesFreeVipFreeAndSelectedServerWithoutAUserTap() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("nsec_verified_server_catalog", Context.MODE_PRIVATE).edit().clear().commit()
        var premium = false
        var fail = false
        var requests = 0
        lateinit var repo: VpnRepository
        val api = object : FakeNsecVpnApi() {
            override suspend fun getServers(token: String, grouped: Boolean): Response<ServerListResponse> {
                assertFalse(repo.serversLoading.value)
                requests++
                return Response.success(if (fail) ServerListResponse(false, null) else ServerListResponse(true,
                    ServerListDataDto(listOf(ServerItemDto("fixture-live-node", "Singapore", "SG", "Singapore", "Singapore", "", 20, premium)))))
            }
        }
        repo = VpnRepository(context, api, TestVault.create(context))
        repo.refreshServers(refreshLatency = false, force = true, showLoading = false)
        assertFalse(repo.servers.value.single().isPremiumOnly)
        premium = true
        repo.refreshServers(refreshLatency = false, force = true, showLoading = false)
        assertTrue(repo.servers.value.single().isPremiumOnly)
        assertTrue(repo.selectedServer.value!!.isPremiumOnly)
        premium = false
        repo.refreshServers(refreshLatency = false, force = true, showLoading = false)
        assertFalse(repo.servers.value.single().isPremiumOnly)
        assertFalse(repo.selectedServer.value!!.isPremiumOnly)
        fail = true
        repo.refreshServers(refreshLatency = false, force = true, showLoading = false)
        assertEquals(4, requests)
        assertEquals("fixture-live-node", repo.servers.value.single().id)
        assertNotNull(repo.serverListError.value)
        assertFalse(repo.serversLoading.value)
    }
}
