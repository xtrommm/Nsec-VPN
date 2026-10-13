package com.nsec.vpn

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.nsec.vpn.data.model.VpnState
import com.nsec.vpn.ui.components.ConnectButton
import com.nsec.vpn.ui.components.ConnectionPresentation
import com.nsec.vpn.ui.components.HomeConnectionHost
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
class HomeConnectionHostTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun returningHomeRetainsStateAndNeverEnablesReconnectBeforeDown() {
        var showConnection by mutableStateOf(false)
        var state: VpnState by mutableStateOf(VpnState.Connected)
        var mounts = 0
        var disposals = 0
        compose.setContent {
            NsecVpnTheme {
                HomeConnectionHost(
                    showConnection = showConnection,
                    home = { visible ->
                        var count by remember { mutableStateOf(0) }
                        DisposableEffect(Unit) {
                            mounts++
                            onDispose { disposals++ }
                        }
                        Column {
                            Button(onClick = { count++ }, modifier = Modifier.testTag("home_counter")) { Text("$count") }
                            Text(ConnectionPresentation.homeTitle(state), Modifier.testTag("home_status"))
                            ConnectButton(state, onToggle = {}, animationsEnabled = visible, compact = true)
                        }
                    },
                    connection = {
                        Button(onClick = {
                            state = VpnState.Disconnecting
                            showConnection = false
                        }, modifier = Modifier.testTag("disconnect")) { Text("Disconnect") }
                    }
                )
            }
        }
        compose.onNodeWithTag("home_counter").performClick()
        compose.runOnIdle { showConnection = true }
        compose.onNodeWithTag("home_counter").assertDoesNotExist()
        compose.onNodeWithTag("disconnect").performClick()
        compose.onNodeWithTag("home_counter").assertIsDisplayed().assertTextEquals("1")
        compose.onNodeWithTag("home_status").assertTextEquals("VPN DISCONNECTING")
        compose.onNodeWithTag("vpn_connect_button").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(1, mounts)
            assertEquals(0, disposals)
            state = VpnState.Disconnected
        }
        compose.onNodeWithTag("home_status").assertTextEquals("VPN NOT CONNECTED")
    }
}
