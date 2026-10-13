package com.nsec.vpn

import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.nsec.vpn.data.api.*
import com.nsec.vpn.data.config.RemoteConfigManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import com.nsec.vpn.data.monetization.AdMobManager
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
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun restoresRealOfferBeforeLiveFetchAndIndependentlyOfSdkReadiness() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            runBlocking {
                val context = ApplicationProvider.getApplicationContext<android.content.Context>()
                context.getSharedPreferences("nsec_real_remote_config", 0).edit(commit = true) { clear() }
                val payload = settings().copy(ads = settings().ads.copy(
                    rewardedEnabled = true, enabled = true, rewardedPassMinutes = 45,
                    rewardedAdUnitId = "ca-app-pub-1234567890123456/1234567890"))
                val writer = RemoteConfigManager(object : FakeNsecVpnApi() {
                    override suspend fun getConfig() = Response.success(ConfigResponse(true, payload))
                }, context = context)
                writer.fetchRemoteConfig()
                var calls = 0
                val ads = AdMobManager(context, initializeSdk = {})
                val reader = RemoteConfigManager(object : FakeNsecVpnApi() {
                    override suspend fun getConfig(): Response<ConfigResponse> { calls++; throw java.io.IOException("Fixture offline") }
                }, ads, context)
                reader.restoreCachedConfig()
                assertTrue(reader.cacheRestored.value)
                assertTrue(reader.loaded.value)
                assertEquals(0, calls)
                assertEquals(45, reader.config.value.rewardedPassMinutes)
                assertTrue(reader.config.value.rewardedEnabled)
                assertFalse(ads.config.value.rewardedEnabled)
                reader.fetchRemoteConfig()
                assertEquals(1, calls)
                assertEquals(45, reader.config.value.rewardedPassMinutes)
                assertTrue(reader.config.value.rewardedEnabled)
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test fun corruptCacheCompletesBootstrapWithoutInventingAnOffer() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("nsec_real_remote_config", 0).edit(commit = true) {
            clear(); putInt("validated_response", 123)
        }
        val reader = RemoteConfigManager(object : FakeNsecVpnApi() {}, context = context)
        reader.restoreCachedConfig()
        assertTrue(reader.cacheRestored.value)
        assertFalse(reader.loaded.value)
        assertNull(reader.config.value.rewardedPassMinutes)
    }

}
