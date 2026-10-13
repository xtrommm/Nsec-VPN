package com.nsec.vpn

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.nsec.vpn.data.model.DnsProvider
import com.nsec.vpn.data.model.Server
import com.nsec.vpn.data.model.VpnState
import com.nsec.vpn.data.model.VpnStats
import com.nsec.vpn.data.monetization.AdMobConfig
import com.nsec.vpn.data.monetization.NativeAdManager
import com.nsec.vpn.ui.components.DnsFeedback
import com.nsec.vpn.ui.components.DnsSelectionSheet
import com.nsec.vpn.ui.components.LiveTunnelStats
import com.nsec.vpn.ui.screens.ServerListScreen
import com.nsec.vpn.ui.screens.SplitTunnelScreen
import com.nsec.vpn.ui.theme.NsecVpnTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp-xhdpi")
@OptIn(ExperimentalTestApi::class)
class UiSmoothnessRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun liveStatisticsDoNotRecomposeTheirParent() {
        val stats = MutableStateFlow(VpnStats())
        var parentCompositions = 0
        compose.setContent {
            SideEffect { parentCompositions++ }
            LiveTunnelStats(stats) { Text("${it.connectionDurationSeconds}") }
        }
        val before = parentCompositions
        compose.runOnIdle { stats.value = VpnStats(connectionDurationSeconds = 2) }
        compose.onNodeWithText("2").assertExists()
        compose.runOnIdle { assertEquals(before, parentCompositions) }
    }

    @Test fun backgroundLifecycleStopsVisualWork() {
        val owner = object : androidx.lifecycle.LifecycleOwner {
            val registry = androidx.lifecycle.LifecycleRegistry(this)
            override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
        }
        owner.registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED
        compose.setContent {
            CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                Text(if (com.nsec.vpn.ui.components.isUiResumed()) "Visible" else "Paused")
            }
        }
        compose.onNodeWithText("Visible").assertExists()
        compose.runOnIdle { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.CREATED }
        compose.onNodeWithText("Paused").assertExists()
        compose.runOnIdle { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        compose.onNodeWithText("Visible").assertExists()
    }

    @Test fun dnsMessagesDistinguishSavedApplyingAndActive() {
        assertEquals("DNS Saved. Applying Automatically.", DnsFeedback.saved(VpnState.Connected))
        assertEquals("DNS Saved For Your Next Connection.", DnsFeedback.saved(VpnState.Disconnected))
        assertEquals("Applying Connection Settings…", DnsFeedback.status(VpnState.Connecting, listOf("1.1.1.1")))
        assertEquals("Active DNS: 9.9.9.9", DnsFeedback.status(VpnState.Connected, listOf("9.9.9.9")))
        assertFalse(DnsFeedback.status(VpnState.Disconnected, listOf("9.9.9.9")).contains("Active DNS"))
    }

    @Test fun dnsPickerUsesActualProviderAndNoManualReconnectMessage() {
        var selected: DnsProvider? = null
        var dismissed = false
        compose.setContent { NsecVpnTheme {
            DnsSelectionSheet(DnsProvider.AUTOMATIC, "", listOf("1.1.1.1"), VpnState.Connected,
                onSelect = { selected = it }, onCustom = {}, onDismiss = { dismissed = true })
        } }
        compose.onNodeWithText("Use The Connected Node’s DNS Configuration").assertExists()
        compose.onNodeWithTag("dns_QUAD9").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DnsProvider.QUAD9, selected); assertTrue(dismissed) }
    }

    @Test fun invalidCustomDnsKeepsPickerOpenAndShowsValidationError() {
        var dismissed = false
        compose.setContent { NsecVpnTheme {
            DnsSelectionSheet(DnsProvider.CUSTOM, "", emptyList(), VpnState.Disconnected,
                onSelect = {}, onCustom = { throw IllegalArgumentException("Invalid DNS IP Address.") },
                onDismiss = { dismissed = true })
        } }
        compose.onNodeWithTag("custom_dns_input").performScrollTo().performTextInput("invalid")
        compose.onNodeWithTag("apply_custom_dns").performScrollTo().performClick()
        compose.onNodeWithTag("dns_error").performScrollTo().assertTextEquals("Invalid DNS IP Address.")
        compose.runOnIdle { assertFalse(dismissed) }
    }

    @Test fun serverSearchSurvivesLeavingAndReturningToTab() {
        var visible by mutableStateOf(true)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val ads = NativeAdManager(context)
        val server = Server("fixture-node", "Singapore", "SG", "Singapore", "Singapore", "", 20, false)
        compose.setContent { NsecVpnTheme {
            val holder = rememberSaveableStateHolder()
            if (visible) holder.SaveableStateProvider("servers") {
                ServerListScreen(listOf(server), null, "VIP", ads, {}, {}, {}, {}, {},
                    adMobConfig = AdMobConfig(nativeEnabled = false))
            } else Text("Another Tab")
        } }
        compose.onNodeWithTag("server_search_input").performTextInput("Singapore")
        compose.runOnIdle { visible = false }
        compose.onNodeWithText("Another Tab").assertExists()
        compose.runOnIdle { visible = true }
        compose.onNodeWithTag("server_search_input").assertTextContains("Singapore")
    }

    @Test fun missingLauncherAppsNeverProduceInventedChoices() {
        compose.setContent { NsecVpnTheme { SplitTunnelScreen(emptySet(), {}, {}) } }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("No Other Launchable Apps Found.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Netflix").assertDoesNotExist()
        compose.onNodeWithText("Banking App").assertDoesNotExist()
    }
}
