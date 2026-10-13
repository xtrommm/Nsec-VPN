package com.nsec.vpn

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nsec.vpn.data.model.VpnState
import com.nsec.vpn.data.model.VpnStats
import com.nsec.vpn.ui.components.ConnectButton
import com.nsec.vpn.ui.components.HomeConnectionHost
import com.nsec.vpn.ui.screens.ConnectedScreen
import com.nsec.vpn.ui.theme.NsecVpnTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device UI presentation test. Actual handshake/routing is covered by manual server verification. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ConnectionUiDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun connectionAndDisconnectFeedbackNeverEnablesADuplicateOperation() {
        var state: VpnState by mutableStateOf(VpnState.Disconnected)
        var details by mutableStateOf(false)
        compose.setContent { NsecVpnTheme {
            HomeConnectionHost(details,
                home = { visible -> ConnectButton(state, onToggle = { state = VpnState.Connecting }, animationsEnabled = visible) },
                connection = {
                    ConnectedScreen(state, VpnStats(), null, "VIP", onDisconnect = {
                        state = VpnState.Disconnecting; details = false
                    }, onBackToHome = { details = false })
                })
        } }
        compose.onNodeWithTag("vpn_connect_button").performClick().assertIsNotEnabled()
        compose.runOnIdle { state = VpnState.Connected; details = true }
        compose.onNodeWithTag("disconnect_button").performScrollTo().performClick()
        compose.onNodeWithTag("vpn_connect_button").assertIsNotEnabled()
        compose.runOnIdle { state = VpnState.Disconnected }
        compose.onNodeWithTag("vpn_connect_button").assertIsEnabled()
    }
}
