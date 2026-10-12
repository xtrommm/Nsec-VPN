package com.nsec.vpn

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.nsec.vpn.ui.components.RewardOffer
import com.nsec.vpn.ui.components.VipRewardCard
import com.nsec.vpn.ui.theme.NsecVpnTheme
import org.junit.Assert.assertEquals
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
        compose.onNodeWithText("45 minutes of VIP").assertExists()
        compose.onNodeWithText("Ad-free").assertExists()
        compose.onNodeWithText("VIP servers").assertExists()
        compose.onNodeWithTag("watch_vip_reward").performClick()
        assertEquals(1, taps)
    }
    @Test fun busyRewardCannotBeRequestedTwice() {
        compose.setContent { NsecVpnTheme {
            VipRewardCard(RewardOffer.from(true, true, 45, "FREE", false), true, {}, {}, compact = true)
        } }
        compose.onNodeWithTag("watch_vip_reward").assertIsNotEnabled()
    }
}
