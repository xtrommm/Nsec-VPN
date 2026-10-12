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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class NativeAdManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val pool = mutableListOf<NativeAd>()
    private val assignedAds = mutableSetOf<NativeAd>()
    private val maxPoolSize = 3
    private val isPreloading = AtomicBoolean(false)
    private var retryAttempt = 0

    private val _adLoadedEvent = MutableStateFlow(0L)
    val adLoadedEvent: StateFlow<Long> = _adLoadedEvent.asStateFlow()

    fun preloadNativeAds(adUnitId: String, enabled: Boolean) {
        if (!enabled || pool.size + assignedAds.size >= maxPoolSize || isPreloading.get()) {
            return
        }

        isPreloading.set(true)
        try {
            val adLoader = AdLoader.Builder(context.applicationContext, adUnitId)
                .forNativeAd { nativeAd ->
                    scope.launch {
                        isPreloading.set(false)
                        retryAttempt = 0
                        if (pool.size < maxPoolSize) {
                            pool.add(nativeAd)
                            _adLoadedEvent.value = System.currentTimeMillis()
                        } else {
                            nativeAd.destroy()
                        }
                        // Preload more if needed
                        preloadNativeAds(adUnitId, enabled)
                    }
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isPreloading.set(false)
                        scope.launch {
                            retryAttempt++
                            val delayMs = (1000L * (1 shl retryAttempt.coerceAtMost(5))).coerceAtMost(30000L)
                            delay(delayMs)
                            preloadNativeAds(adUnitId, enabled)
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
        assignedAds.remove(ad)
        try {
            ad.destroy()
        } catch (_: Throwable) {}
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
