package com.nsec.vpn

import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.nsec.vpn.data.api.*
import com.nsec.vpn.data.config.RemoteConfigManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RemoteConfigRefreshTest {
    private fun settings(email: String? = null) = AppRemoteConfigDto(
        AppVersionDto(1, 2, false), AnnouncementDto(false, null, null, true),
        AdsDto(false, false, 120, false, rewardedPassMinutes = 30, enabled = false),
        SupportDto(email), "2030-01-01T00:00:00Z"
    )
    @Test fun restoresOnlyPreviouslyVerifiedSettingsAndReportsFetchFailure() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("nsec_real_remote_config", 0).edit(commit = true) { clear() }
        val live = object : FakeNsecVpnApi() {
            override suspend fun getConfig() = Response.success(ConfigResponse(true, settings("support@example.org")))
        }
        val first = RemoteConfigManager(live, context = context)
        first.fetchRemoteConfig()
        assertEquals("support@example.org", first.config.value.supportEmail)
        assertNull(first.error.value)
        val failed = object : FakeNsecVpnApi() {
            override suspend fun getConfig(): Response<ConfigResponse> = throw java.io.IOException("Test fixture offline")
        }
        val restored = RemoteConfigManager(failed, context = context)
        restored.fetchRemoteConfig()
        assertEquals("support@example.org", restored.config.value.supportEmail)
        assertNotNull(restored.error.value)
    }
    @Test fun rejectsUnsuccessfulOrInvalidSettingsWithoutReplacingVerifiedState() = runBlocking {
        var payload = ConfigResponse(true, settings())
        val api = object : FakeNsecVpnApi() { override suspend fun getConfig() = Response.success(payload) }
        val manager = RemoteConfigManager(api)
        manager.fetchRemoteConfig()
        assertEquals(2, manager.config.value.latestAppVersionCode)
        payload = ConfigResponse(false, settings().copy(app = AppVersionDto(1, 50, false)))
        manager.fetchRemoteConfig()
        assertEquals(2, manager.config.value.latestAppVersionCode)
        payload = ConfigResponse(true, settings().copy(app = AppVersionDto(5, 2, false)))
        manager.fetchRemoteConfig()
        assertEquals(2, manager.config.value.latestAppVersionCode)
        assertNotNull(manager.error.value)
    }
}
