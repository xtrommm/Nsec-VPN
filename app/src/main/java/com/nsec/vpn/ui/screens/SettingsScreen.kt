package com.nsec.vpn.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nsec.vpn.data.model.DnsProvider
import com.nsec.vpn.data.model.ThemeMode
import com.nsec.vpn.ui.theme.*

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    killSwitchEnabled: Boolean,
    onToggleKillSwitch: (Boolean) -> Unit,
    selectedDns: DnsProvider,
    onSelectDns: (DnsProvider) -> Unit,
    splitTunnelingEnabled: Boolean,
    onToggleSplitTunneling: (Boolean) -> Unit,
    autoConnectEnabled: Boolean,
    onToggleAutoConnect: (Boolean) -> Unit,
    onOpenSplitTunnelApps: () -> Unit,
    onOpenTroubleshooting: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onRedeemPromoCode: () -> Unit,
    onOpenDebugDiagnostics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }
    var showDnsDialog by remember { mutableStateOf(false) }
    var showKillSwitchDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Settings",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(18.dp))

        SectionHeader("APPEARANCE & THEME")

        SettingsClickableItem(
            icon = when (themeMode) {
                ThemeMode.LIGHT -> Icons.Default.LightMode
                ThemeMode.DARK -> Icons.Default.DarkMode
                ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
            },
            title = "App Theme",
            subtitle = themeMode.displayName,
            onClick = { showThemeDialog = true }
        )

        Spacer(modifier = Modifier.height(20.dp))

        SectionHeader("SECURITY & PROTOCOL")

        SettingsToggleItem(
            icon = Icons.Default.Shield,
            title = "Kill Switch",
            subtitle = "Block all internet traffic if VPN drops unexpectedly",
            isChecked = killSwitchEnabled,
            onCheckedChange = { checked ->
                if (checked) {
                    showKillSwitchDialog = true
                } else {
                    onToggleKillSwitch(false)
                }
            }
        )

        SettingsClickableItem(
            icon = Icons.Default.Dns,
            title = "Encrypted DNS",
            subtitle = selectedDns.displayName,
            onClick = { showDnsDialog = true }
        )

        SettingsToggleItem(
            icon = Icons.AutoMirrored.Filled.AltRoute,
            title = "Split Tunneling",
            subtitle = "Allow select apps to bypass the VPN tunnel",
            isChecked = splitTunnelingEnabled,
            onCheckedChange = { onToggleSplitTunneling(it) },
            trailingAction = if (splitTunnelingEnabled) {
                {
                    Text(
                        text = "MANAGE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        modifier = Modifier.clickable { onOpenSplitTunnelApps() }
                    )
                }
            } else null
        )

        Spacer(modifier = Modifier.height(20.dp))

        SectionHeader("CONNECTION")

        SettingsToggleItem(
            icon = Icons.Default.PowerSettingsNew,
            title = "Auto-Connect on Launch",
            subtitle = "Immediately secure your network when opening Nsec VPN",
            isChecked = autoConnectEnabled,
            onCheckedChange = onToggleAutoConnect
        )

        SettingsClickableItem(
            icon = Icons.Default.Build,
            title = "Troubleshooting Assistant",
            subtitle = "Diagnose MTU, handshake timeout, or ISP blocks",
            onClick = onOpenTroubleshooting
        )

        Spacer(modifier = Modifier.height(20.dp))

        SectionHeader("ABOUT")

        SettingsClickableItem(
            icon = Icons.Default.CardGiftcard,
            title = "Redeem Promo Code",
            subtitle = "Enter code to unlock free Premium VIP access",
            onClick = onRedeemPromoCode
        )

        SettingsClickableItem(
            icon = Icons.Default.Language,
            title = "Official Website",
            subtitle = "nsecvpn.com",
            onClick = {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://nsecvpn.com"))
                    context.startActivity(browserIntent)
                } catch (_: Exception) {}
            }
        )

        SettingsClickableItem(
            icon = Icons.Default.Description,
            title = "Zero-Logs Privacy Policy",
            subtitle = "Verified no-log WireGuard infrastructure",
            onClick = onOpenPrivacyPolicy
        )

        SettingsClickableItem(
            icon = Icons.Default.Info,
            title = "App Version",
            subtitle = "Nsec VPN v1.0.0",
            onClick = {}
        )

        if (com.nsec.vpn.BuildConfig.DEBUG) {
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader("DEVELOPER / DEBUG")
            SettingsClickableItem(
                icon = Icons.Default.BugReport,
                title = "WireGuard & Peer Diagnostics",
                subtitle = "View live handshake, peer API status, and endpoint",
                onClick = onOpenDebugDiagnostics
            )
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Choose Theme Mode", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    onSelectThemeMode(mode)
                                    showThemeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(mode.displayName, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close", color = PrimaryBlue)
                }
            }
        )
    }

    if (showDnsDialog) {
        AlertDialog(
            onDismissRequest = { showDnsDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Select Encrypted DNS", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DnsProvider.entries.forEach { provider ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectDns(provider)
                                    showDnsDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = selectedDns == provider,
                                onClick = {
                                    onSelectDns(provider)
                                    showDnsDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(provider.displayName, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                                Text("${provider.primary}, ${provider.secondary}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDnsDialog = false }) {
                    Text("Close", color = PrimaryBlue)
                }
            }
        )
    }

    if (showKillSwitchDialog) {
        AlertDialog(
            onDismissRequest = { showKillSwitchDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Enable Android System Kill Switch", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "For full hardware-level kill switch protection against packet leaks, Android provides the 'Always-on VPN' and 'Block connections without VPN' system setting.\n\nTap below to open system VPN settings.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showKillSwitchDialog = false
                        onToggleKillSwitch(true)
                        try {
                            context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
                        } catch (_: Exception) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Open VPN Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showKillSwitchDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    trailingAction: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailingAction != null) {
            trailingAction()
            Spacer(modifier = Modifier.width(10.dp))
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryBlue,
                uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun SettingsClickableItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
}
