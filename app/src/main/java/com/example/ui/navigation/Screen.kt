package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Servers : Screen("servers", "Servers", Icons.Default.Dns)
    object SpeedTest : Screen("speed_test", "Speed", Icons.Default.Speed)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}
