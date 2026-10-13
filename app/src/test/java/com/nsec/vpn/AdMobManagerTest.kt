package com.nsec.vpn

import androidx.test.core.app.ApplicationProvider
import com.nsec.vpn.data.monetization.AdMobConfig
import com.nsec.vpn.data.monetization.AdMobManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdMobManagerTest {

    @Test
    fun adsStayDisabledUntilSdkInitializationAndRemoteKillSwitchClearsThem() {
        var ready: (() -> Unit)? = null
        var initializationCount = 0
        val manager = AdMobManager(ApplicationProvider.getApplicationContext(), initializeSdk = { initializationCount++; ready = it })
        manager.updateConfig(AdMobConfig(nativeEnabled = true, rewardedEnabled = true, interstitialEnabled = true))
        assertFalse(manager.config.value.nativeEnabled)
        assertFalse(manager.config.value.rewardedEnabled)
        assertFalse(manager.canShowInterstitial())
        manager.initialize()
        manager.initialize()
        assertEquals(1, initializationCount)
        assertFalse(manager.config.value.nativeEnabled)
        ready!!.invoke()
        assertTrue(manager.config.value.nativeEnabled)
        manager.updateConfig(AdMobConfig(nativeEnabled = false, rewardedEnabled = false, interstitialEnabled = false))
        assertFalse(manager.config.value.nativeEnabled)
        assertFalse(manager.config.value.rewardedEnabled)
        assertFalse(manager.canShowInterstitial())
    }

    @Test
    fun canShowInterstitial_respectsFrequencyCapping() {
        val manager = AdMobManager(ApplicationProvider.getApplicationContext(), initializeSdk = { it() })
        manager.initialize()
        manager.updateConfig(
            AdMobConfig(
                interstitialEnabled = true,
                interstitialIntervalSeconds = 60
            )
        )

        // Initially can show interstitial
        assertTrue(manager.canShowInterstitial())

        // After presenting, frequency capping prevents immediate re-showing
        manager.recordInterstitialShown()
        assertFalse(manager.canShowInterstitial())
    }

    @Test
    fun canShowInterstitial_whenDisabled_returnsFalse() {
        val manager = AdMobManager(ApplicationProvider.getApplicationContext())
        manager.updateConfig(
            AdMobConfig(interstitialEnabled = false)
        )

        assertFalse(manager.canShowInterstitial())
    }
}
