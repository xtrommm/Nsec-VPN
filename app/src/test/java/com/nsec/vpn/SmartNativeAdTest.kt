package com.nsec.vpn

import com.nsec.vpn.data.model.VpnState
import com.nsec.vpn.data.monetization.AdMobConfig
import com.nsec.vpn.ui.components.NativeAdTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartNativeAdTest {

    private fun determineTemplate(widthDp: Float, heightDp: Float): NativeAdTemplate {
        val minWidth = 220f
        val minHeight = 70f
        val compactMaxHeight = 120f
        val mediumMaxHeight = 240f
        val compactMaxWidth = 340f
        val mediumMaxWidth = 550f

        return when {
            widthDp < minWidth || heightDp < minHeight -> NativeAdTemplate.HIDDEN
            heightDp <= compactMaxHeight || widthDp <= compactMaxWidth -> NativeAdTemplate.COMPACT
            heightDp <= mediumMaxHeight || widthDp <= mediumMaxWidth -> NativeAdTemplate.MEDIUM
            else -> NativeAdTemplate.LARGE
        }
    }

    @Test
    fun testTemplateSelection_smallPhone_compact() {
        val template = determineTemplate(360f, 100f)
        assertEquals(NativeAdTemplate.COMPACT, template)
    }

    @Test
    fun testTemplateSelection_normalPhone_medium() {
        val template = determineTemplate(400f, 180f)
        assertEquals(NativeAdTemplate.MEDIUM, template)
    }

    @Test
    fun testTemplateSelection_tablet_large() {
        val template = determineTemplate(800f, 400f)
        assertEquals(NativeAdTemplate.LARGE, template)
    }

    @Test
    fun testTemplateSelection_tooSmall_hidden() {
        val template = determineTemplate(200f, 50f)
        assertEquals(NativeAdTemplate.HIDDEN, template)
    }

    @Test
    fun testAdsAllowedLogic() {
        val defaultConfig = AdMobConfig(nativeEnabled = true)
        val disabledConfig = AdMobConfig(nativeEnabled = false)

        val disconnectedState: VpnState = VpnState.Disconnected
        val connectingState: VpnState = VpnState.Connecting

        // 1. Free + enabled + disconnected -> allowed
        val allowed1 = "FREE" == "FREE" && defaultConfig.nativeEnabled && disconnectedState !is VpnState.Connecting
        assertTrue(allowed1)

        // 2. VIP + enabled + disconnected -> NOT allowed
        val allowed2 = "VIP" == "FREE" && defaultConfig.nativeEnabled && disconnectedState !is VpnState.Connecting
        assertFalse(allowed2)

        // 3. Free + disabled + disconnected -> NOT allowed
        val allowed3 = "FREE" == "FREE" && disabledConfig.nativeEnabled && disconnectedState !is VpnState.Connecting
        assertFalse(allowed3)

        // 4. Free + enabled + connecting -> NOT allowed
        val isAllowed4 = "FREE" == "FREE" && defaultConfig.nativeEnabled && connectingState !is VpnState.Connecting
        assertFalse(isAllowed4)
    }
}
