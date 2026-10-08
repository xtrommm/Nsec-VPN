package com.example.data.monetization

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AdMobConfig(
    val bannerEnabled: Boolean = true,
    val interstitialEnabled: Boolean = true,
    val interstitialIntervalSeconds: Int = 120,
    val rewardedEnabled: Boolean = true,
    val appOpenEnabled: Boolean = false,
    val testMode: Boolean = true,
    val bannerAdUnitId: String = "ca-app-pub-3940256099942544/6300978111",
    val interstitialAdUnitId: String = "ca-app-pub-3940256099942544/1033173712",
    val rewardedAdUnitId: String = "ca-app-pub-3940256099942544/5224354917",
    val appOpenAdUnitId: String = "ca-app-pub-3940256099942544/9257395921"
)

class AdMobManager(private val context: Context) {
    private val _config = MutableStateFlow(AdMobConfig())
    val config: StateFlow<AdMobConfig> = _config.asStateFlow()

    private var lastInterstitialTimeMs: Long = 0

    fun updateConfig(newConfig: AdMobConfig) {
        _config.value = newConfig
    }

    /**
     * Checks if interstitial ad can be displayed based on frequency capping
     */
    fun canShowInterstitial(): Boolean {
        if (!_config.value.interstitialEnabled) return false
        val now = System.currentTimeMillis()
        val intervalMs = _config.value.interstitialIntervalSeconds * 1000L
        return (now - lastInterstitialTimeMs) >= intervalMs
    }

    /**
     * Records interstitial presentation timestamp for frequency capping
     */
    fun recordInterstitialShown() {
        lastInterstitialTimeMs = System.currentTimeMillis()
    }
}
