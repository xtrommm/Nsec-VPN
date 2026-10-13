package com.nsec.vpn

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.nsec.vpn.data.model.Server
import com.nsec.vpn.data.model.DnsProvider
import com.nsec.vpn.data.model.ThemeMode
import com.nsec.vpn.ui.components.*
import com.nsec.vpn.ui.screens.SettingsScreen
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
class UiAlignmentRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fastestBadgeFitsBesideNameWithoutLeadingSpacer() {
        val node = Server("fixture", "Virginia", "US", "United States", "Virginia", "", 1, false, isRecommended = true, pingMs = 129)
        compose.setContent { NsecVpnTheme { ServerCard(node, true, {}, onToggleFavorite = {}) } }
        val title = compose.onNodeWithTag("server_title_fixture", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val badge = compose.onNodeWithTag("server_fastest_fixture", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(badge.left >= title.right)
        assertTrue(badge.top < title.bottom && badge.bottom > title.top)
    }

    @Test fun speedMetricsFormTwoEqualRowsOnPhone() {
        compose.setContent { NsecVpnTheme { Box(Modifier.width(380.dp)) { SpeedTestMetrics(12, 2, 8.5f, 6.2f) } } }
        val ping = compose.onNodeWithTag("speed_metric_ping").getUnclippedBoundsInRoot()
        val jitter = compose.onNodeWithTag("speed_metric_jitter").getUnclippedBoundsInRoot()
        val down = compose.onNodeWithTag("speed_metric_download").getUnclippedBoundsInRoot()
        val up = compose.onNodeWithTag("speed_metric_upload").getUnclippedBoundsInRoot()
        assertEquals(ping.top.value, jitter.top.value, 0.1f)
        assertEquals(down.top.value, up.top.value, 0.1f)
        assertEquals(ping.left.value, down.left.value, 0.1f)
        assertEquals(jitter.left.value, up.left.value, 0.1f)
        assertEquals(ping.width.value, up.width.value, 0.1f)
        assertTrue(down.top > ping.bottom)
    }

    @Test fun largeFontsUseOneReadableMetricColumn() {
        compose.setContent { NsecVpnTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.width(280.dp)) { SpeedTestMetrics(null, null, null, null) }
            }
        } }
        val ping = compose.onNodeWithTag("speed_metric_ping").getUnclippedBoundsInRoot()
        val jitter = compose.onNodeWithTag("speed_metric_jitter").getUnclippedBoundsInRoot()
        assertEquals(ping.left.value, jitter.left.value, 0.1f)
        assertTrue(jitter.top > ping.bottom)
    }

    @Test fun recoverySheetCopiesTheDisplayedDeviceId() {
        val id = "fixture-device-id-for-copy"
        compose.setContent { NsecVpnTheme {
            DeviceRecoverySheet(id, {}, { false }, { _, _ -> }, { _, _ -> })
        } }
        compose.onNodeWithTag("recovery_device_copy").performScrollTo().performClick()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(id, clipboard.primaryClip?.getItemAt(0)?.text.toString())
        compose.onNodeWithText("Copied").assertExists()
    }

    @Test fun versionAppearsAfterDeveloperHeaderAndRecoveryHasSeparateSection() {
        compose.setContent { NsecVpnTheme {
            SettingsScreen(ThemeMode.LIGHT, {}, DnsProvider.AUTOMATIC, {}, false, {}, false, {}, {}, {}, {}, {},
                onRequestServer = { _, _, _ -> "fixture" }, onCustomDns = {}, deviceId = "fixture-device")
        } }
        compose.onNodeWithText("App Version").performScrollTo().assertIsDisplayed()
        val header = compose.onNodeWithText("DEVELOPER / DEBUG").getUnclippedBoundsInRoot()
        val version = compose.onNodeWithText("App Version").getUnclippedBoundsInRoot()
        assertTrue(version.top > header.bottom)
        compose.onNodeWithText("ACCOUNT & RECOVERY").assertExists()
    }

    @Test fun refreshDisablesDuplicatesUntilRequestFinishes() {
        var refreshing by androidx.compose.runtime.mutableStateOf(false)
        var taps = 0
        compose.setContent { NsecVpnTheme { ServerRefreshButton(refreshing) { taps++; refreshing = true } } }
        compose.onNodeWithTag("refresh_servers_button").performClick().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, taps); refreshing = false }
        compose.onNodeWithTag("refresh_servers_button").assertIsEnabled()
    }
}
