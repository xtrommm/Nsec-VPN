package com.nsec.vpn.data.monetization

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class NativeAdManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val pool = mutableListOf<NativeAd>()
    private val assignedAds = mutableSetOf<NativeAd>()
    private val maxPoolSize = 3
    private val isPreloading = AtomicBoolean(false)
    private var retryAttempt = 0
    private var configuredUnit: String? = null
    private var configuredEnabled = true
    private var generation = 0

    fun configure(adUnitId: String, enabled: Boolean) {
        if (configuredUnit != adUnitId || configuredEnabled != enabled) {
            generation++
            destroyAll()
            isPreloading.set(false)
            retryAttempt = 0
        }
        configuredUnit = adUnitId
        configuredEnabled = enabled
    }

    private val _adLoadedEvent = MutableStateFlow(0L)
    val adLoadedEvent: StateFlow<Long> = _adLoadedEvent.asStateFlow()

    fun preloadNativeAds(adUnitId: String, enabled: Boolean) {
        if (!enabled || !configuredEnabled || (configuredUnit != null && configuredUnit != adUnitId) || pool.size + assignedAds.size >= maxPoolSize || isPreloading.get()) {
            return
        }

        isPreloading.set(true)
        val requestGeneration = generation
        try {
            val adLoader = AdLoader.Builder(context.applicationContext, adUnitId)
                .forNativeAd { nativeAd ->
                    scope.launch {
                        if (requestGeneration != generation || !configuredEnabled) {
                            nativeAd.destroy()
                            return@launch
                        }
                        isPreloading.set(false)
                        retryAttempt = 0
                        if (pool.size < maxPoolSize) {
                            pool.add(nativeAd)
                            _adLoadedEvent.value += 1
                        } else {
                            nativeAd.destroy()
                        }
                        // Preload more if needed
                        preloadNativeAds(adUnitId, enabled)
                    }
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        if (requestGeneration != generation || !configuredEnabled) return
                        isPreloading.set(false)
                        scope.launch {
                            retryAttempt++
                            val delayMs = (1000L * (1 shl retryAttempt.coerceAtMost(5))).coerceAtMost(30000L)
                            delay(delayMs)
                            if (requestGeneration == generation) preloadNativeAds(adUnitId, enabled)
                        }
                    }
                })
                .withNativeAdOptions(
                    NativeAdOptions.Builder()
                        .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                        .build()
                )
                .build()

            adLoader.loadAd(AdRequest.Builder().build())
        } catch (e: Throwable) {
            isPreloading.set(false)
            Log.e("NativeAdManager", "Failed to preload native ad")
        }
    }

    fun acquireAd(): NativeAd? {
        // Clean up any destroyed or invalid ads from pool
        val iterator = pool.iterator()
        while (iterator.hasNext()) {
            val ad = iterator.next()
            iterator.remove()
            assignedAds.add(ad)
            return ad
        }
        return null
    }

    fun releaseAd(ad: NativeAd?) {
        if (ad == null) return
        if (!assignedAds.remove(ad)) return
        try {
            ad.destroy()
        } catch (_: Throwable) {}
        configuredUnit?.takeIf { it.isNotBlank() }?.let { preloadNativeAds(it, configuredEnabled) }
    }

    fun destroyAll() {
        for (ad in pool) {
            try { ad.destroy() } catch (_: Throwable) {}
        }
        pool.clear()
        for (ad in assignedAds) {
            try { ad.destroy() } catch (_: Throwable) {}
        }
        assignedAds.clear()
    }
}
