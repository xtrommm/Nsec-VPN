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
                    announcementActive = data.announcement.active,
                    announcementTitle = data.announcement.title,
                    announcementBody = data.announcement.body
                )
                adMobManager?.updateConfig(
                    AdMobConfig(
                        bannerEnabled = data.ads.bannerEnabled,
                        nativeEnabled = data.ads.bannerEnabled,
                        interstitialEnabled = data.ads.interstitialEnabled,
                        interstitialIntervalSeconds = data.ads.interstitialIntervalSeconds,
                        rewardedEnabled = data.ads.rewardedEnabled
                    )
                )
            }
        } catch (_: Exception) {}
    }
}
