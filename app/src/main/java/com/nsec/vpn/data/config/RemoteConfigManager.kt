package com.nsec.vpn.data.config

import android.content.Context
import androidx.core.content.edit
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.nsec.vpn.data.api.AppRemoteConfigDto
import com.nsec.vpn.data.api.NsecVpnApi
import com.nsec.vpn.data.monetization.AdMobConfig
import com.nsec.vpn.data.monetization.AdMobManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

// This state comes only from the live API or a previously validated API response.
data class RemoteAppConfig(
    val minAppVersionCode: Int = 1,
    val latestAppVersionCode: Int = 1,
    val forceUpdate: Boolean = false,
    val maintenanceMode: Boolean = false,
    val announcementActive: Boolean = false,
    val announcementTitle: String? = null,
    val announcementBody: String? = null,
    val supportEmail: String? = null,
    val updatedAt: String? = null
)

class RemoteConfigManager(
    private val api: NsecVpnApi,
    private val adMobManager: AdMobManager? = null,
    context: Context? = null
) {
    private val _config = MutableStateFlow(RemoteAppConfig())
    val config: StateFlow<RemoteAppConfig> = _config.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val refreshMutex = Mutex()
    private val prefs = context?.applicationContext?.getSharedPreferences("nsec_real_remote_config", Context.MODE_PRIVATE)
    private val configJson = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter(AppRemoteConfigDto::class.java)
    private var restored = false

    suspend fun fetchRemoteConfig() = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            if (!restored) {
                restored = true
                prefs?.getString("validated_response", null)?.let { text ->
                    try { apply(requireNotNull(configJson.fromJson(text)) { "Invalid cached settings" }) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { prefs.edit { remove("validated_response") } }
                }
            }
            try {
                val response = withTimeout(10_000) { api.getConfig() }
                val body = response.body()
                if (!response.isSuccessful || body?.success != true || body.data == null)
                    throw IllegalStateException("Remote settings unavailable (${response.code()})")
                apply(body.data)
                prefs?.edit { putString("validated_response", configJson.toJson(body.data)) }
                _error.value = null
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _error.value = "Settings refresh timed out. Previously verified settings remain active."
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _error.value = "Could not refresh app settings. Previously verified settings remain active."
            }
        }
    }

    private suspend fun apply(data: AppRemoteConfigDto) {
        require(data.app.minAppVersionCode > 0 && data.app.latestAppVersionCode >= data.app.minAppVersionCode)
        require(data.ads.interstitialIntervalSeconds in 15..1800)
        require(data.ads.rewardedPassMinutes in 1..1440)
        if (data.announcement.active) require(!data.announcement.title.isNullOrBlank() && !data.announcement.body.isNullOrBlank())
        val support = data.support?.email?.takeIf { it.isNotBlank() }
        require(support == null || android.util.Patterns.EMAIL_ADDRESS.matcher(support).matches())
        _config.value = RemoteAppConfig(data.app.minAppVersionCode, data.app.latestAppVersionCode, data.app.forceUpdate,
            data.app.maintenanceMode, data.announcement.active, data.announcement.title, data.announcement.body, support, data.updatedAt)
        _loaded.value = true
        val current = adMobManager?.config?.value ?: AdMobConfig()
        val debug = com.nsec.vpn.BuildConfig.DEBUG
        val units = AdMobConfig()
        val enabled = data.ads.enabled != false
        fun usable(unit: String?): Boolean = unit != null && Regex("ca-app-pub-[0-9]{16}/[0-9]{10}").matches(unit) && !unit.startsWith("ca-app-pub-3940256099942544/")
        val ads = AdMobConfig(
            bannerEnabled = enabled && data.ads.bannerEnabled && (debug || usable(data.ads.bannerAdUnitId)),
            nativeEnabled = enabled && (data.ads.nativeEnabled ?: data.ads.bannerEnabled) && (debug || usable(data.ads.nativeAdUnitId)),
            interstitialEnabled = enabled && data.ads.interstitialEnabled && (debug || usable(data.ads.interstitialAdUnitId)),
            interstitialIntervalSeconds = data.ads.interstitialIntervalSeconds,
            rewardedEnabled = enabled && data.ads.rewardedEnabled && (debug || usable(data.ads.rewardedAdUnitId)),
            appOpenEnabled = false,
            testMode = debug,
            bannerAdUnitId = if (debug) units.bannerAdUnitId else data.ads.bannerAdUnitId ?: current.bannerAdUnitId,
            nativeAdUnitId = if (debug) units.nativeAdUnitId else data.ads.nativeAdUnitId ?: current.nativeAdUnitId,
            interstitialAdUnitId = if (debug) units.interstitialAdUnitId else data.ads.interstitialAdUnitId ?: current.interstitialAdUnitId,
            rewardedAdUnitId = if (debug) units.rewardedAdUnitId else data.ads.rewardedAdUnitId ?: current.rewardedAdUnitId,
            rewardedPassMinutes = data.ads.rewardedPassMinutes
        )
        if (adMobManager != null) withContext(Dispatchers.Main) { adMobManager.updateConfig(ads) }
    }
}
