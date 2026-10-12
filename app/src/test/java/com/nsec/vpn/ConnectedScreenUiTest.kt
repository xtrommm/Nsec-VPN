package com.nsec.vpn

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.nsec.vpn.data.model.Server
import com.nsec.vpn.data.model.VpnState
import com.nsec.vpn.data.model.VpnStats
import com.nsec.vpn.data.model.TunnelConfig
import com.nsec.vpn.ui.screens.ConnectedScreen
import com.nsec.vpn.ui.theme.NsecVpnTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp-xhdpi")
@OptIn(ExperimentalTestApi::class)
class ConnectedScreenUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun connectedScreen_displaysTimerAndSpeeds_andDisconnects() {
        var disconnectedClicked = false
        var backClicked = false

        val sampleServer = Server(
            id = "srv_us_nyc",
            name = "United States - New York",
            countryCode = "US",
            countryName = "United States",
            city = "New York",
            flagEmoji = "ðŸ‡ºðŸ‡¸",
            currentLoad = 35,
            isPremiumOnly = false
        )

        val sampleStats = VpnStats(
            connectionDurationSeconds = 125,
            downloadSpeedMbps = 45.2f,
            uploadSpeedMbps = 12.8f
        )

        composeTestRule.setContent {
            NsecVpnTheme {
                ConnectedScreen(
                    vpnState = VpnState.Connected,
                    vpnStats = sampleStats,
                    selectedServer = sampleServer,
                    userTier = "FREE",
                    connectedNode = TunnelConfig(com.wireguard.crypto.KeyPair().publicKey.toBase64(),"47.85.109.170:51820","10.8.0.2/32",nodeId="test-node",nodeName="Test",locationName="Test",publicIpv4="47.85.109.170"),
                    onDisconnect = { disconnectedClicked = true },
                    onBackToHome = { backClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("vpn_server_ip").assertTextEquals("VPN Server IP: 47.85.109.170")
        composeTestRule.onNodeWithText("10.8.0.2/32", substring = true).assertDoesNotExist()

        // Verify timer is displayed ("02:05")
        composeTestRule.onNodeWithTag("connection_timer_text").assertExists().assertTextEquals("02:05")

        // Verify download and upload speeds are displayed
        composeTestRule.onNodeWithTag("download_speed_text").assertExists().assertTextEquals("45.2 Mb/s")
        composeTestRule.onNodeWithTag("upload_speed_text").assertExists().assertTextEquals("12.8 Mb/s")

        // Click Disconnect button
        composeTestRule.onNodeWithTag("disconnect_button").assertExists().performClick()
        assert(disconnectedClicked)

        // Click Back button
        composeTestRule.onNodeWithTag("connected_back_button").assertExists().performClick()
        assert(backClicked)
    }
}
