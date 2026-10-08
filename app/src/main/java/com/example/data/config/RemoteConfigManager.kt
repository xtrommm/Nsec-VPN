package com.example.data.config

import com.example.data.api.AuraVpnApi
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

class RemoteConfigManager(private val api: AuraVpnApi) {
    private val _config = MutableStateFlow(RemoteAppConfig())
    val config: StateFlow<RemoteAppConfig> = _config.asStateFlow()

    suspend fun fetchRemoteConfig() = withContext(Dispatchers.IO) {
        try {
            val response = api.getConfig()
            if (response.isSuccessful && response.body()?.data != null) {
                val data = response.body()!!.data!!
                _config.value = RemoteAppConfig(
                    minAppVersionCode = data.app.minAppVersionCode,
                    latestAppVersionCode = data.app.latestAppVersionCode,
                    forceUpdate = data.app.forceUpdate,
                    announcementActive = data.announcement.active,
                    announcementTitle = data.announcement.title,
                    announcementBody = data.announcement.body
                )
            }
        } catch (_: Exception) {}
    }
}
