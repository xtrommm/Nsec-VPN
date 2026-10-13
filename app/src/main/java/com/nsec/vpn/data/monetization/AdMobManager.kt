package com.nsec.vpn.data.monetization

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AdMobConfig(
    val bannerEnabled: Boolean = true,
    val nativeEnabled: Boolean = true,
    val interstitialEnabled: Boolean = true,
    val interstitialIntervalSeconds: Int = 120,
    val rewardedEnabled: Boolean = true,
    val appOpenEnabled: Boolean = false,
    val testMode: Boolean = true,
    val bannerAdUnitId: String = "ca-app-pub-3940256099942544/6300978111",
    val nativeAdUnitId: String = "ca-app-pub-3940256099942544/2247696110",
    val interstitialAdUnitId: String = "ca-app-pub-3940256099942544/1033173712",
    val rewardedAdUnitId: String = "ca-app-pub-3940256099942544/5224354917",
    val rewardedPassMinutes: Int = 30,
    val adFreeUntilEpochMillis: Long? = null,
    val appOpenAdUnitId: String = "ca-app-pub-3940256099942544/9257395921"
)

class AdMobManager(private val context: Context, private val initializeSdk: ((() -> Unit) -> Unit)? = null) {
    val nativeAdManager = NativeAdManager(context)
    private val _config = MutableStateFlow(AdMobConfig(
        bannerEnabled = com.nsec.vpn.BuildConfig.DEBUG, nativeEnabled = com.nsec.vpn.BuildConfig.DEBUG,
        interstitialEnabled = com.nsec.vpn.BuildConfig.DEBUG, rewardedEnabled = com.nsec.vpn.BuildConfig.DEBUG,
        testMode = com.nsec.vpn.BuildConfig.DEBUG
    ))
    val config: StateFlow<AdMobConfig> = _config.asStateFlow()

    private var baseConfig = _config.value
    private var consentAllowed = false
    private var sdkInitialized = false
    private var sdkInitializationStarted = false
    private var configGeneration = 0
    private val rewardPrefs = context.getSharedPreferences("nsec_ad_free_reward", Context.MODE_PRIVATE)
    private val rewardHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val expiryCheck = object : Runnable {
        override fun run() { refreshAdFreeConfig() }
    }

    private var lastInterstitialTimeMs: Long = 0
    private var interstitialAd: InterstitialAd? = null
    private var isPreloadingInterstitial = false

    private var rewardedAd: RewardedAd? = null
    private var isPreloadingRewarded = false

    init {
        refreshAdFreeConfig()
    }

    fun applyConsent(allowed: Boolean) {
        if (consentAllowed != allowed) {
            configGeneration++
            interstitialAd = null; rewardedAd = null
            isPreloadingInterstitial = false; isPreloadingRewarded = false
        }
        consentAllowed = allowed
        if (allowed && !sdkInitializationStarted) {
            try {
                sdkInitializationStarted = true
                val ready = {
                    sdkInitialized = true
                    refreshAdFreeConfig()
                    preloadInterstitial()
                    preloadRewarded()
                }
                if (initializeSdk != null) initializeSdk.invoke(ready) else {
                    MobileAds.setRequestConfiguration(RequestConfiguration.Builder()
                        .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR)).build())
                    MobileAds.initialize(context) { ready() }
                }
            } catch (_: Exception) { sdkInitializationStarted = false; consentAllowed = false }
        }
        refreshAdFreeConfig()
    }

    fun updateConfig(newConfig: AdMobConfig) {
        if (baseConfig != newConfig) {
            configGeneration++
            interstitialAd = null
            rewardedAd = null
            isPreloadingInterstitial = false
            isPreloadingRewarded = false
        }
        baseConfig = newConfig
        refreshAdFreeConfig()
    }

    fun grantAdFreeReward() {
        val minutes = baseConfig.rewardedPassMinutes.coerceIn(1, 1440)
        val now = System.currentTimeMillis()
        val boot = android.provider.Settings.Global.getInt(context.contentResolver,
            android.provider.Settings.Global.BOOT_COUNT, -1)
        rewardPrefs.edit {
            putLong("grantedWall", now)
            putLong("expiresWall", now + minutes * 60000L)
            putLong("expiresElapsed", android.os.SystemClock.elapsedRealtime() + minutes * 60000L)
            putInt("boot", boot)
        }
        refreshAdFreeConfig()
    }

    private fun refreshAdFreeConfig() {
        rewardHandler.removeCallbacks(expiryCheck)
        val wasActive = _config.value.adFreeUntilEpochMillis != null
        val active = AdFreeRewardClock.active(rewardPrefs.getLong("grantedWall", 0),
            rewardPrefs.getLong("expiresWall", 0), rewardPrefs.getLong("expiresElapsed", 0),
            rewardPrefs.getInt("boot", -1), android.provider.Settings.Global.getInt(context.contentResolver,
                android.provider.Settings.Global.BOOT_COUNT, -1), System.currentTimeMillis(),
            android.os.SystemClock.elapsedRealtime())
        val config = if (!consentAllowed || !sdkInitialized) baseConfig.copy(bannerEnabled = false, nativeEnabled = false,
            interstitialEnabled = false, rewardedEnabled = false, appOpenEnabled = false) else if (active) baseConfig.copy(bannerEnabled = false, nativeEnabled = false,
            interstitialEnabled = false, appOpenEnabled = false,
            adFreeUntilEpochMillis = rewardPrefs.getLong("expiresWall", 0)) else baseConfig
        _config.value = config
        nativeAdManager.configure(config.nativeAdUnitId, config.nativeEnabled)
        if (active) {
            if (!wasActive) {
                configGeneration++
                isPreloadingInterstitial = false
                isPreloadingRewarded = false
                rewardedAd = null
            }
            interstitialAd = null
            val remaining = minOf(rewardPrefs.getLong("expiresElapsed", 0) - android.os.SystemClock.elapsedRealtime(),
                rewardPrefs.getLong("expiresWall", 0) - System.currentTimeMillis())
            rewardHandler.postDelayed(expiryCheck, remaining.coerceIn(1, 60_000))
        } else if (wasActive) {
            preloadInterstitial()
        }
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
        val generation = configGeneration
        try {
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(
                appContext.applicationContext,
                _config.value.interstitialAdUnitId,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        if (generation != configGeneration || !_config.value.interstitialEnabled) return
                        interstitialAd = ad
                        isPreloadingInterstitial = false
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        if (generation != configGeneration) return
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
        val generation = configGeneration
        try {
            val adRequest = AdRequest.Builder().build()
            RewardedAd.load(
                appContext.applicationContext,
                _config.value.rewardedAdUnitId,
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        if (generation != configGeneration || !_config.value.rewardedEnabled) return
                        rewardedAd = ad
                        isPreloadingRewarded = false
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        if (generation != configGeneration) return
                        rewardedAd = null
                        isPreloadingRewarded = false
                    }
                }
            )
        } catch (_: Throwable) {
            isPreloadingRewarded = false
        }
    }

    fun showRewarded(activity: Activity, onRewarded: () -> Unit, onAdDismissed: () -> Unit, verificationTicket: String? = null, onError: (String) -> Unit = {}) {
        val ad = rewardedAd
        if (ad == null || activity.isFinishing || activity.isDestroyed) {
            rewardedAd = null
            onError("The reward ad is not ready. Please try again shortly.")
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
                    onError("Could not show the reward ad. Please try again.")
                    onAdDismissed()
                    preloadRewarded(activity)
                }
            }

            val deviceId = com.nsec.vpn.core.network.ServiceLocator.getInstance(activity).keyStoreManager.getOrCreateDeviceId()
            val verification = com.google.android.gms.ads.rewarded.ServerSideVerificationOptions.Builder().setUserId(deviceId)
            verificationTicket?.let { verification.setCustomData(it) }
            ad.setServerSideVerificationOptions(verification.build())
            ad.show(activity) { _ ->
                onRewarded()
            }
        } catch (_: Throwable) {
            rewardedAd = null
            onError("Could not show the reward ad. Please try again.")
            onAdDismissed()
            preloadRewarded(activity)
        }
    }
}
