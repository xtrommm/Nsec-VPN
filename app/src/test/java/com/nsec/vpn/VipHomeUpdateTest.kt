package com.nsec.vpn

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.nsec.vpn.data.model.Server
import com.nsec.vpn.data.monetization.AdMobConfig
import com.nsec.vpn.data.monetization.NativeAdManager
import com.nsec.vpn.ui.components.*
import com.nsec.vpn.ui.screens.ServerListScreen
import com.nsec.vpn.ui.theme.NsecVpnTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w420dp-h850dp-xhdpi")
@OptIn(ExperimentalTestApi::class)
class VipHomeUpdateTest {
    @get:Rule val compose = createComposeRule()
    private val node = Server("fixture-node", "Singapore", "SG", "Singapore", "Singapore", "", 20, true)

    @Test fun premiumAccessIsCheckedWithoutStartingATunnel() {
        assertTrue(VipAccessGate.requiresVip(node, "FREE"))
        assertTrue(VipAccessGate.requiresVip(node, "UNKNOWN"))
        assertFalse(VipAccessGate.requiresVip(node, "VIP"))
        assertFalse(VipAccessGate.requiresVip(node, "REWARDED_PASS"))
        assertFalse(VipAccessGate.requiresVip(node.copy(isPremiumOnly = false), "FREE"))
    }

    @Test fun vipPromptHasPurchaseAndRewardActions() {
        var purchases = 0
        var rewards = 0
        compose.setContent { NsecVpnTheme { VipRequiredDialog(true, {}, { purchases++ }, { rewards++ }) } }
        compose.onNodeWithText("Purchase VIP Plan").performClick()
        compose.onNodeWithText("Watch An Ad").performClick()
        compose.runOnIdle { assertEquals(1, purchases); assertEquals(1, rewards) }
    }

    @Test fun dismissedAnnouncementReopensOnlyForNewContent() {
        var title by mutableStateOf("First Announcement")
        compose.setContent { NsecVpnTheme { HomeAnnouncement(title, "Announcement Body") } }
        compose.onNodeWithText("First Announcement").assertIsDisplayed()
        compose.onNodeWithTag("announcement_close").performClick()
        compose.onNodeWithTag("home_announcement").assertDoesNotExist()
        compose.runOnIdle { title = "Updated Announcement" }
        compose.onNodeWithText("Updated Announcement").assertIsDisplayed()
    }

    @Test fun filtersFillTheWholeRowAndPremiumTapOpensPrompt() {
        val ads = NativeAdManager(ApplicationProvider.getApplicationContext())
        var prompts = 0
        var selections = 0
        compose.setContent { NsecVpnTheme {
            ServerListScreen(listOf(node), null, "FREE", ads, { selections++ }, {}, {}, {}, { prompts++ },
                adMobConfig = AdMobConfig(nativeEnabled = false))
        } }
        val row = compose.onNodeWithTag("server_filter_row").getUnclippedBoundsInRoot()
        val first = compose.onNodeWithTag("server_filter_0").getUnclippedBoundsInRoot()
        val last = compose.onNodeWithTag("server_filter_3").getUnclippedBoundsInRoot()
        assertEquals(row.left.value, first.left.value, 0.1f)
        assertEquals(row.right.value, last.right.value, 0.1f)
        assertEquals((first.right - first.left).value, (last.right - last.left).value, 0.1f)
        compose.onNodeWithTag("server_title_fixture-node", useUnmergedTree = true).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, prompts); assertEquals(0, selections) }
    }
    @Test fun vipPopupCloseWorksAndUnavailableRewardExplainsWhy() {
        var dismissed = false
        compose.setContent { NsecVpnTheme {
            VipRequiredDialog(false, { dismissed = true }, {}, {}, rewardDetail = "Waiting For Signed Verification")
        } }
        compose.onNodeWithText("Watch An Ad").assertIsNotEnabled()
        compose.onNodeWithText("Waiting For Signed Verification").assertIsDisplayed()
        compose.onNodeWithTag("vip_popup_close").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun recoveryHasAnExplicitCloseButton() {
        var dismissed = false
        compose.setContent { NsecVpnTheme {
            DeviceRecoverySheet("fixture-device", { dismissed = true }, { false }, { _, _ -> }, { _, _ -> })
        } }
        compose.onNodeWithTag("recovery_close").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

}
