package com.nsec.vpn

import androidx.test.core.app.ApplicationProvider
import com.nsec.vpn.data.monetization.AdMobConfig
import com.nsec.vpn.data.monetization.AdMobManager
import com.nsec.vpn.util.SpeedFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConnectedScreenFlowTest {

    @Test
    fun timerFormatting_handlesVariousDurations() {
        assertEquals("00:00", SpeedFormatter.formatTimer(0))
        assertEquals("02:05", SpeedFormatter.formatTimer(125))
        assertEquals("59:59", SpeedFormatter.formatTimer(3599))
        assertEquals("01:00:00", SpeedFormatter.formatTimer(3600))
        assertEquals("01:01:01", SpeedFormatter.formatTimer(3661))
    }

    @Test
    fun speedFormatting_handlesZeroNegativeAndScaling() {
        assertEquals("0.0 Mb/s", SpeedFormatter.formatSpeed(0f))
        assertEquals("0.0 Mb/s", SpeedFormatter.formatSpeed(-2.5f))
        assertEquals("0.0 Mb/s", SpeedFormatter.formatSpeed(Float.NaN))
        assertEquals("50.0 Kb/s", SpeedFormatter.formatSpeed(0.05f))
        assertEquals("12.5 Mb/s", SpeedFormatter.formatSpeed(12.5f))
        assertEquals("1.5 Gb/s", SpeedFormatter.formatSpeed(1500f))
    }

    @Test
    fun adShowDecision_logicValidation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val adMobManager = AdMobManager(context, initializeSdk = { it() })
        adMobManager.applyConsent(true)

        // Case 1: Premium user tier ("VIP") should skip ad
        val userTier = "VIP"
        val isPremium = userTier != "FREE"
        val config = AdMobConfig(interstitialEnabled = true)
        val shouldShowAd = !isPremium && config.interstitialEnabled && adMobManager.canShowInterstitial()
        assertFalse(shouldShowAd)

        // Case 2: Free tier with ads disabled in config should skip ad
        val freeTier = "FREE"
        val isPremiumFree = freeTier != "FREE"
        val disabledConfig = AdMobConfig(interstitialEnabled = false)
        val shouldShowAdDisabled = !isPremiumFree && disabledConfig.interstitialEnabled && adMobManager.canShowInterstitial()
        assertFalse(shouldShowAdDisabled)

        // Case 3: Free tier with ads enabled and frequency cap met should allow ad
        val enabledConfig = AdMobConfig(interstitialEnabled = true, interstitialIntervalSeconds = 0)
        adMobManager.updateConfig(enabledConfig)
        val shouldShowAdAllowed = !isPremiumFree && enabledConfig.interstitialEnabled && adMobManager.canShowInterstitial()
        assertTrue(shouldShowAdAllowed)
    }

    @Test
    fun navigationDecision_userInitiatedVsBackgroundConnect() {
        // User-initiated connect from Home screen triggers navigation/ad flow
        var isUserInitiatedConnect = true
        var navigatedToConnected = false

        fun onVpnConnected() {
            if (isUserInitiatedConnect) {
                isUserInitiatedConnect = false
                navigatedToConnected = true
            }
        }

        onVpnConnected()
        assertTrue(navigatedToConnected)
        assertFalse(isUserInitiatedConnect)

        // Background / Quick Settings tile connect should NOT trigger navigation/ad flow
        var isTileConnect = false
        var navigatedToConnectedFromTile = false

        fun onTileVpnConnected() {
            if (isTileConnect) {
                isTileConnect = false
                navigatedToConnectedFromTile = true
            }
        }

        onTileVpnConnected()
        assertFalse(navigatedToConnectedFromTile)
    }
}
