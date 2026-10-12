package com.nsec.vpn.data.config

import com.nsec.vpn.data.api.NsecVpnApi
import com.nsec.vpn.data.monetization.AdMobConfig
import com.nsec.vpn.data.monetization.AdMobManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class RemoteAppConfig(
    val minAppVersionCode: Int = 1,
    val latestAppVersionCode: Int = 1,
    val forceUpdate: Boolean = false,
    val maintenanceMode: Boolean = false,
    val announcementActive: Boolean = false,
    val announcementTitle: String? = null,
    val announcementBody: String? = null
)

class RemoteConfigManager(
    private val api: NsecVpnApi,
    private val adMobManager: AdMobManager? = null
) {
    private val _config = MutableStateFlow(RemoteAppConfig())
    val config: StateFlow<RemoteAppConfig> = _config.asStateFlow()

    suspend fun fetchRemoteConfig() = withContext(Dispatchers.IO) {
        try {
            val response = api.getConfig()
            val body = response.body()
            if (response.isSuccessful && body?.data != null) {
                val data = body.data
                _config.value = RemoteAppConfig(
                    minAppVersionCode = data.app.minAppVersionCode,
                    latestAppVersionCode = data.app.latestAppVersionCode,
                    forceUpdate = data.app.forceUpdate,
                    maintenanceMode = data.app.maintenanceMode,
                    announcementActive = data.announcement.active,
                    announcementTitle = data.announcement.title,
                    announcementBody = data.announcement.body
                )
                val current = adMobManager?.config?.value ?: AdMobConfig()
                val debug = com.nsec.vpn.BuildConfig.DEBUG
                fun usable(unit: String?): Boolean = unit != null &&
                    Regex("ca-app-pub-[0-9]{16}/[0-9]{10}").matches(unit) &&
                    !unit.startsWith("ca-app-pub-3940256099942544/")
                val ads = AdMobConfig(
                    bannerEnabled = data.ads.bannerEnabled && (debug || usable(data.ads.bannerAdUnitId)),
                    nativeEnabled = (data.ads.nativeEnabled ?: data.ads.bannerEnabled) && (debug || usable(data.ads.nativeAdUnitId)),
                    interstitialEnabled = data.ads.interstitialEnabled && (debug || usable(data.ads.interstitialAdUnitId)),
                    interstitialIntervalSeconds = data.ads.interstitialIntervalSeconds,
                    rewardedEnabled = data.ads.rewardedEnabled && (debug || usable(data.ads.rewardedAdUnitId)),
                    testMode = debug,
                    bannerAdUnitId = if (debug) current.bannerAdUnitId else data.ads.bannerAdUnitId ?: current.bannerAdUnitId,
                    nativeAdUnitId = if (debug) current.nativeAdUnitId else data.ads.nativeAdUnitId ?: current.nativeAdUnitId,
                    interstitialAdUnitId = if (debug) current.interstitialAdUnitId else data.ads.interstitialAdUnitId ?: current.interstitialAdUnitId,
                    rewardedAdUnitId = if (debug) current.rewardedAdUnitId else data.ads.rewardedAdUnitId ?: current.rewardedAdUnitId,
                    rewardedPassMinutes = data.ads.rewardedPassMinutes.coerceIn(1, 1440)
                )
                withContext(Dispatchers.Main) { adMobManager?.updateConfig(ads) }

            }
        } catch (_: Exception) {}
    }
}
