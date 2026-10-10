package com.nsec.vpn.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val context = LocalContext.current

    val adSize = remember(screenWidthDp) {
        try {
            // Smart inline adaptive banner ad size (adjusts height/width like 320x100, 320x250, etc. based on screen size)
            AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(context, screenWidthDp)
        } catch (_: Throwable) {
            AdSize.MEDIUM_RECTANGLE
        }
    }

    val adHeightDp = adSize.height.coerceAtLeast(100).coerceAtMost(280)

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
            .height(adHeightDp.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(adSize)
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
