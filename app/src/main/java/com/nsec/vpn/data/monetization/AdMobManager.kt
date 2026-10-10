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
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
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

    private var rewardedAd: RewardedAd? = null
    private var isPreloadingRewarded = false

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
    fun isRewardedReady(): Boolean = rewardedAd != null

    fun setInterstitialAdForTesting(ad: InterstitialAd?) {
        interstitialAd = ad
    }

    fun setRewardedAdForTesting(ad: RewardedAd?) {
        rewardedAd = ad
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

    fun preloadRewarded(appContext: Context = context) {
        if (rewardedAd != null || isPreloadingRewarded) return
        if (!_config.value.rewardedEnabled) return

        isPreloadingRewarded = true
        try {
            val adRequest = AdRequest.Builder().build()
            RewardedAd.load(
                appContext.applicationContext,
                _config.value.rewardedAdUnitId,
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        isPreloadingRewarded = false
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        rewardedAd = null
                        isPreloadingRewarded = false
                    }
                }
            )
        } catch (_: Throwable) {
            isPreloadingRewarded = false
        }
    }

    fun showRewarded(activity: Activity, onRewarded: () -> Unit, onAdDismissed: () -> Unit) {
        val ad = rewardedAd
        if (ad == null || activity.isFinishing || activity.isDestroyed) {
            rewardedAd = null
            onRewarded()
            onAdDismissed()
            preloadRewarded(activity)
            return
        }

        try {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    onAdDismissed()
                    preloadRewarded(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedAd = null
                    onAdDismissed()
                    preloadRewarded(activity)
                }
            }

            ad.show(activity) { _ ->
                onRewarded()
            }
        } catch (_: Throwable) {
            rewardedAd = null
            onRewarded()
            onAdDismissed()
            preloadRewarded(activity)
        }
    }
}
