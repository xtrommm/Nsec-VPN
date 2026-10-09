package com.nsec.vpn

import androidx.test.core.app.ApplicationProvider
import com.nsec.vpn.data.monetization.AdMobConfig
import com.nsec.vpn.data.monetization.AdMobManager
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
    fun canShowInterstitial_respectsFrequencyCapping() {
        val manager = AdMobManager(ApplicationProvider.getApplicationContext())
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
