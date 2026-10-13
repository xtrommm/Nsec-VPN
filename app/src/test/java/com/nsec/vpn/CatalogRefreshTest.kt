package com.nsec.vpn

import androidx.test.core.app.ApplicationProvider
import com.nsec.vpn.data.api.ServerListResponse
import com.nsec.vpn.data.repository.VpnRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogRefreshTest {
    @Test
    fun repeatedStartupRefreshesCoalesceButExplicitRefreshFetchesAgain() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vault = TestVault.create(context)
        vault.saveTokens("test-access", "test-refresh")
        var requests = 0
        val api = object : FakeNsecVpnApi() {
            override suspend fun getServers(token: String, grouped: Boolean): Response<ServerListResponse> {
                requests++
                return super.getServers(token, grouped)
            }
        }
        val repository = VpnRepository(context, api, vault)
        repository.refreshServers(refreshLatency = false)
        repository.refreshServers(refreshLatency = false)
        assertEquals(1, requests)
        repository.refreshServers(refreshLatency = false, force = true)
        assertEquals(2, requests)
    }
}
