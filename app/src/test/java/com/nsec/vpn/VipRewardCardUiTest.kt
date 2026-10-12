package com.nsec.vpn

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.nsec.vpn.ui.components.RewardOffer
import com.nsec.vpn.ui.components.VipRewardCard
import com.nsec.vpn.ui.theme.NsecVpnTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp-xhdpi")
@OptIn(ExperimentalTestApi::class)
class VipRewardCardUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun fortyFiveMinuteOfferShowsBothBenefitsAndRunsTheRewardAction() {
        var taps = 0
        compose.setContent { NsecVpnTheme {
            VipRewardCard(RewardOffer.from(true, true, 45, "FREE", false), false, { taps++ }, {}, compact = true)
        } }
        compose.onNodeWithText("45 Minutes Of VIP").assertExists()
        compose.onNodeWithText("Ad-Free + VIP Servers").assertExists()
        val height = compose.onNodeWithTag("home_reward_card").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        assertTrue("Default Home offer must stay compact", height.value <= 104f)
        compose.onNodeWithTag("watch_vip_reward").performClick()
        assertEquals(1, taps)
    }
    @Test fun compactCardOpensFullOptions() {
        var opens = 0
        compose.setContent { NsecVpnTheme {
            VipRewardCard(RewardOffer.from(true, true, 45, "FREE", false), false, {}, { opens++ }, compact = true)
        } }
        compose.onNodeWithTag("home_reward_card").performClick()
        assertEquals(1, opens)
    }
    @Test fun busyRewardCannotBeRequestedTwice() {
        compose.setContent { NsecVpnTheme {
            VipRewardCard(RewardOffer.from(true, true, 45, "FREE", false), true, {}, {}, compact = true)
        } }
        compose.onNodeWithTag("watch_vip_reward").assertIsNotEnabled()
    }
}
