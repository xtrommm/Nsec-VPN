package com.nsec.vpn.data.monetization

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
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
    private var interstitialAd: InterstitialAd? = null
    private var isPreloadingInterstitial = false

    init {
        try {
            MobileAds.initialize(context) {}
        } catch (_: Throwable) {}
    }

    fun updateConfig(newConfig: AdMobConfig) {
        _config.value = newConfig
    }

    fun canShowInterstitial(): Boolean {
        if (!_config.value.interstitialEnabled) return false
        val now = System.currentTimeMillis()
        val intervalMs = _config.value.interstitialIntervalSeconds * 1000L
        return (now - lastInterstitialTimeMs) >= intervalMs
    }

    fun recordInterstitialShown() {
        lastInterstitialTimeMs = System.currentTimeMillis()
    }

    fun isInterstitialReady(): Boolean = interstitialAd != null

    fun setInterstitialAdForTesting(ad: InterstitialAd?) {
        interstitialAd = ad
    }

    fun preloadInterstitial(appContext: Context = context) {
        if (interstitialAd != null || isPreloadingInterstitial) return
        if (!_config.value.interstitialEnabled) return

        isPreloadingInterstitial = true
        try {
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(
                appContext.applicationContext,
                _config.value.interstitialAdUnitId,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isPreloadingInterstitial = false
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        interstitialAd = null
                        isPreloadingInterstitial = false
                    }
                }
            )
        } catch (_: Throwable) {
            isPreloadingInterstitial = false
        }
    }

    fun showInterstitial(activity: Activity, onAdDismissed: () -> Unit) {
        val ad = interstitialAd
        if (ad == null || !canShowInterstitial() || activity.isFinishing || activity.isDestroyed) {
            interstitialAd = null
            onAdDismissed()
            preloadInterstitial(activity)
            return
        }

        try {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    recordInterstitialShown()
                    interstitialAd = null
                    onAdDismissed()
                    preloadInterstitial(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    onAdDismissed()
                    preloadInterstitial(activity)
                }

                override fun onAdShowedFullScreenContent() {
                    interstitialAd = null
                }
            }
            ad.show(activity)
        } catch (_: Throwable) {
            interstitialAd = null
            onAdDismissed()
            preloadInterstitial(activity)
        }
    }
}
