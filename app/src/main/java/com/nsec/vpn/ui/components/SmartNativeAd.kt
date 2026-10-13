package com.nsec.vpn.ui.components

import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.nsec.vpn.BuildConfig
import com.nsec.vpn.R
import com.nsec.vpn.data.model.VpnState
import com.nsec.vpn.data.monetization.AdMobConfig
import com.nsec.vpn.data.monetization.NativeAdManager
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

enum class NativeAdTemplate {
    COMPACT, MEDIUM, LARGE, HIDDEN
}

val COMPACT_MAX_HEIGHT = 120.dp
val MEDIUM_MAX_HEIGHT = 240.dp
val COMPACT_MAX_WIDTH = 340.dp
val MEDIUM_MAX_WIDTH = 550.dp
val MIN_VISIBLE_WIDTH = 220.dp
val MIN_VISIBLE_HEIGHT = 70.dp

object NativeAdLayoutPolicy {
    fun select(width: Float, height: Float, video: Boolean = false, fontScale: Float = 1f): NativeAdTemplate = when {
        width < MIN_VISIBLE_WIDTH.value || height < MIN_VISIBLE_HEIGHT.value -> NativeAdTemplate.HIDDEN
        // Video assets require a MediaView; compact XML has none. Allow room for media and text.
        video && height < 260f * fontScale.coerceAtLeast(1f) -> NativeAdTemplate.HIDDEN
        video -> if (width <= MEDIUM_MAX_WIDTH.value) NativeAdTemplate.MEDIUM else NativeAdTemplate.LARGE
        height <= COMPACT_MAX_HEIGHT.value || width <= COMPACT_MAX_WIDTH.value -> NativeAdTemplate.COMPACT
        height <= MEDIUM_MAX_HEIGHT.value || width <= MEDIUM_MAX_WIDTH.value -> NativeAdTemplate.MEDIUM
        else -> NativeAdTemplate.LARGE
    }
}

@Composable
fun SmartNativeAd(
    nativeAdManager: NativeAdManager,
    adMobConfig: AdMobConfig,
    userTier: String,
    vpnState: VpnState,
    modifier: Modifier = Modifier
) {
    val isFreeUser = userTier == "FREE"
    val isAdAllowed = isFreeUser && adMobConfig.nativeEnabled && vpnState !is VpnState.Connecting

    val adLoadedEvent by nativeAdManager.adLoadedEvent.collectAsStateWithLifecycle()
    var currentAd by remember { mutableStateOf<NativeAd?>(null) }
    var lastRefreshTime by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }

    LaunchedEffect(isAdAllowed, adMobConfig.nativeAdUnitId) {
        currentAd?.let { nativeAdManager.releaseAd(it) }
        currentAd = null
        if (isAdAllowed) {
            nativeAdManager.preloadNativeAds(adMobConfig.nativeAdUnitId, adMobConfig.nativeEnabled)
            currentAd = nativeAdManager.acquireAd()
        } else {
            currentAd?.let {
                nativeAdManager.releaseAd(it)
                currentAd = null
            }
        }
    }

    LaunchedEffect(adLoadedEvent, isAdAllowed) {
        if (isAdAllowed && currentAd == null) currentAd = nativeAdManager.acquireAd()
    }

    LaunchedEffect(currentAd, isAdAllowed) {
        if (currentAd != null && isAdAllowed) {
            while (true) {
                delay(60.seconds)
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastRefreshTime >= 60000L) {
                    lastRefreshTime = now
                    currentAd?.let { nativeAdManager.releaseAd(it) }
                    currentAd = nativeAdManager.acquireAd()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            currentAd?.let { nativeAdManager.releaseAd(it) }
        }
    }

    if (!isAdAllowed || currentAd == null) {
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
    ) {
        val widthDp = maxWidth
        val heightDp = maxHeight

        val video = currentAd?.mediaContent?.hasVideoContent() == true
        val fontScale = LocalDensity.current.fontScale
        val template = NativeAdLayoutPolicy.select(widthDp.value, heightDp.value, video, fontScale)

        if (template == NativeAdTemplate.HIDDEN) {
            return@BoxWithConstraints
        }

        val isInspection = LocalInspectionMode.current

        if (BuildConfig.DEBUG && !isInspection) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${widthDp.value.toInt()}x${heightDp.value.toInt()}dp | $template",
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        key(template, currentAd) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                val layoutRes = when (template) {
                    NativeAdTemplate.COMPACT -> R.layout.native_ad_compact
                    NativeAdTemplate.MEDIUM -> R.layout.native_ad_medium
                    NativeAdTemplate.LARGE -> R.layout.native_ad_large
                    NativeAdTemplate.HIDDEN -> R.layout.native_ad_compact
                }
                val adView = LayoutInflater.from(ctx).inflate(layoutRes, null) as NativeAdView
                currentAd?.let { populateNativeAdView(it, adView) }
                adView
            },
            update = { adView ->
                currentAd?.let { populateNativeAdView(it, adView) }
            }
        )
        }
    }
}

fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
    try {
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.iconView = adView.findViewById(R.id.ad_app_icon)
        adView.mediaView = adView.findViewById(R.id.ad_media)
        adView.mediaView?.mediaContent = nativeAd.mediaContent
        adView.advertiserView = adView.findViewById(R.id.ad_advertiser)
        adView.starRatingView = adView.findViewById(R.id.ad_stars)

        (adView.headlineView as? TextView)?.text = nativeAd.headline

        val bodyView = adView.bodyView
        if (nativeAd.body == null || bodyView == null) {
            bodyView?.visibility = android.view.View.GONE
        } else {
            bodyView.visibility = android.view.View.VISIBLE
            (bodyView as? TextView)?.text = nativeAd.body
        }

        val ctaView = adView.callToActionView
        if (nativeAd.callToAction == null || ctaView == null) {
            ctaView?.visibility = android.view.View.GONE
        } else {
            ctaView.visibility = android.view.View.VISIBLE
            (ctaView as? Button)?.text = nativeAd.callToAction
        }

        val iconView = adView.iconView
        if (nativeAd.icon == null || iconView == null) {
            iconView?.visibility = android.view.View.GONE
        } else {
            iconView.visibility = android.view.View.VISIBLE
            (iconView as? ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
        }

        val advView = adView.advertiserView
        if (nativeAd.advertiser == null || advView == null) {
            advView?.visibility = android.view.View.GONE
        } else {
            advView.visibility = android.view.View.VISIBLE
            (advView as? TextView)?.text = nativeAd.advertiser
        }

        val ratingView = adView.starRatingView
        if (nativeAd.starRating == null || ratingView == null) {
            ratingView?.visibility = android.view.View.GONE
        } else {
            ratingView.visibility = android.view.View.VISIBLE
            nativeAd.starRating?.let { (ratingView as? RatingBar)?.rating = it.toFloat() }
        }

        adView.setNativeAd(nativeAd)
    } catch (_: Exception) { adView.visibility = android.view.View.GONE }
}
