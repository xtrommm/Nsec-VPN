package com.nsec.vpn.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

@Composable
fun AdMobBannerView(
    bannerAdUnitId: String,
    modifier: Modifier = Modifier
) {
    var adFailedToLoad by remember { mutableStateOf(false) }

    if (adFailedToLoad) {
        return
    }

    var adViewInstance by remember { mutableStateOf<AdView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                adViewInstance?.destroy()
            } catch (_: Throwable) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = bannerAdUnitId
                    adListener = object : AdListener() {
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            adFailedToLoad = true
                        }
                    }
                    try {
                        loadAd(AdRequest.Builder().build())
                    } catch (_: Throwable) {
                        adFailedToLoad = true
                    }
                    adViewInstance = this
                }
            },
            update = { adView ->
                adViewInstance = adView
            }
        )
    }
}

@Composable
fun AdBannerView(
    onRemoveAdsClicked: () -> Unit,
    modifier: Modifier = Modifier,
    bannerAdUnitId: String = "ca-app-pub-3940256099942544/6300978111"
) {
    AdMobBannerView(bannerAdUnitId = bannerAdUnitId, modifier = modifier)
}
