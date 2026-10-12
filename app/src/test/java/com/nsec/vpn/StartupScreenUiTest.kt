package com.nsec.vpn

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.nsec.vpn.data.model.ThemeMode
import com.nsec.vpn.ui.components.StartupScreen
import com.nsec.vpn.ui.theme.NsecVpnTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalTestApi::class)
class StartupScreenUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun lightBrandingIncludesOriginAndFlag() {
        compose.setContent { NsecVpnTheme(themeMode = ThemeMode.LIGHT) { StartupScreen() } }
        compose.onNodeWithText("Made in Europe").assertExists()
        compose.onNodeWithContentDescription("European Union Flag").assertExists()
    }
    @Test fun darkBrandingIncludesOriginAndFlag() {
        compose.setContent { NsecVpnTheme(themeMode = ThemeMode.DARK) { StartupScreen() } }
        compose.onNodeWithText("Made in Europe").assertExists()
        compose.onNodeWithContentDescription("European Union Flag").assertExists()
    }
}
