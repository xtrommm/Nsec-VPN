package com.nsec.vpn
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nsec.vpn.data.api.*
import com.nsec.vpn.data.model.DnsProvider
import com.nsec.vpn.data.repository.VpnRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsIdentityRequestTest {
    @Test fun installationIdentityAndDnsSurviveRepositoryRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = TestVault.create(context)
        val id = vault.getOrCreateDeviceId()
        assertEquals(id, TestVault.create(context).getOrCreateDeviceId())
        val repository = VpnRepository(context, FakeNsecVpnApi(), vault)
        repository.setCustomDns("1.1.1.1, 8.8.8.8")
        val recreated = VpnRepository(context, FakeNsecVpnApi(), TestVault.create(context))
        assertEquals(DnsProvider.CUSTOM, recreated.selectedDns.value)
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), recreated.dnsServersForConnection())
        recreated.setDns(DnsProvider.AUTOMATIC)
        assertTrue(recreated.dnsServersForConnection().isEmpty())
    }
    @Test fun serverRequestFailureDoesNotConsumeCooldownAndSuccessDoes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = TestVault.create(context)
        vault.saveServerRequestCooldown(0)
        var calls = 0
        var outage = true
        val api = object : FakeNsecVpnApi() {
            override suspend fun submitServerRequest(token: String, request: ServerRequestBody): Response<ServerRequestResponse> {
                calls++
                return if (outage) Response.success(ServerRequestResponse(false, null)) else
                    Response.success(ServerRequestResponse(true, ServerRequestData("fixture-request", request.country, request.city, "PENDING", "2026-10-12T00:00:00Z")))
            }
        }
        val repo = VpnRepository(context, api, vault)
        try { repo.requestServer("US", "New York", null); fail("Expected failure") } catch (_: IllegalStateException) { }
        assertEquals(0L, vault.getServerRequestCooldown())
        outage = false
        assertTrue(repo.requestServer("US", "New York", null).contains("Pending"))
        assertTrue(vault.getServerRequestCooldown() > System.currentTimeMillis())
        try { repo.requestServer("US", "New York", null); fail("Expected cooldown") } catch (_: IllegalStateException) { }
        assertEquals(2, calls)
    }
}
