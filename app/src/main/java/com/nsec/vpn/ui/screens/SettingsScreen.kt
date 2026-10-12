package com.nsec.vpn.ui.screens

import androidx.core.net.toUri
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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ContentCopy

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
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
    onRequestServer: suspend (String, String, String?) -> String,
    onCustomDns: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenDebugDiagnostics: () -> Unit = {},
    supportEmail: String? = null,
    deviceId: String = "",
    customDns: String = "",
    activeDns: List<String> = emptyList()
) {
    val context = LocalContext.current
    var showRequestDialog by remember { mutableStateOf(false) }
    fun copy(value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Nsec VPN", value))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCustomDns by remember { mutableStateOf(false) }
    var customDnsText by remember(customDns) { mutableStateOf(customDns) }
    var dnsError by remember { mutableStateOf<String?>(null) }
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

        SettingsClickableItem(
            icon = Icons.Default.Shield,
            title = "Android VPN Lockdown",
            subtitle = "Configure Always-on VPN and blocking in Android settings",
            onClick = { showKillSwitchDialog = true }
        )

        SettingsClickableItem(
            icon = Icons.Default.Dns,
            title = "DNS Resolver Through VPN",
            subtitle = selectedDns.displayName + if (activeDns.isNotEmpty()) "\nActive: ${activeDns.joinToString()}" else "\nApplied on next connection",
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
                        color = MaterialTheme.colorScheme.primary,
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
            subtitle = "Check API access, handshake and tunnel observations",
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

        SettingsClickableItem(icon = Icons.Default.Email, title = "Support Email",
            subtitle = supportEmail ?: "Support address has not been configured", onClick = {
                if (supportEmail == null) Toast.makeText(context, "The operator has not configured support email yet.", Toast.LENGTH_LONG).show()
                else try { context.startActivity(Intent(Intent.ACTION_SENDTO, ("mailto:" + android.net.Uri.encode(supportEmail, "@.")).toUri())) }
                catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, "No email app installed. Use Copy email.", Toast.LENGTH_LONG).show() }
            })
        if (supportEmail != null) TextButton(onClick = { copy(supportEmail) }) { Text("Copy email") }
        SettingsClickableItem(icon = Icons.Default.Public, title = "Request Server", subtitle = "Suggest a country and city", onClick = { showRequestDialog = true })

        SettingsClickableItem(
            icon = Icons.Default.Language,
            title = "Official Website",
            subtitle = "nsecvpn.com",
            onClick = {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, "https://nsecvpn.com".toUri())
                    context.startActivity(browserIntent)
                } catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, "No browser installed.", Toast.LENGTH_SHORT).show() }
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
            subtitle = "Nsec VPN v${com.nsec.vpn.BuildConfig.VERSION_NAME}",
            onClick = { Toast.makeText(context, "Version ${com.nsec.vpn.BuildConfig.VERSION_NAME} (${com.nsec.vpn.BuildConfig.VERSION_CODE})", Toast.LENGTH_SHORT).show() }
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
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("YOUR DEVICE ID")
        Text(deviceId, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(enabled = deviceId.isNotBlank(), onClick = { copy(deviceId) }) {
            Icon(Icons.Default.ContentCopy, contentDescription = null)
            Spacer(Modifier.width(6.dp)); Text("Copy device ID")
        }

    }

    if (showCustomDns) AlertDialog(
        onDismissRequest = { showCustomDns = false }, title = { Text("Custom DNS") },
        text = { Column { Text("Enter public DNS IPs separated by commas. IPv4/IPv6 syntax is validated, but this IPv4-only node cannot route IPv6 resolvers.", fontSize = 12.sp)
            OutlinedTextField(value = customDnsText, onValueChange = { customDnsText = it.take(200); dnsError = null }, label = { Text("DNS IP addresses") })
            dnsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(onClick = {
            try { onCustomDns(customDnsText); showCustomDns = false; Toast.makeText(context, "DNS saved. Reconnecting if active.", Toast.LENGTH_SHORT).show() }
            catch (e: IllegalArgumentException) { dnsError = e.message ?: "Invalid DNS resolver." }
        }) { Text("Apply DNS") } }, dismissButton = { TextButton(onClick = { showCustomDns = false }) { Text("Cancel") } }
    )

    if (showRequestDialog) ServerRequestSheet(
        onDismiss = { showRequestDialog = false },
        onSubmit = onRequestServer
    )

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
                    Text("Close", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    if (showDnsDialog) {
        AlertDialog(
            onDismissRequest = { showDnsDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Select DNS resolver", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DnsProvider.entries.forEach { provider ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (provider == DnsProvider.CUSTOM) { dnsError = null; showCustomDns = true } else onSelectDns(provider)
                                    showDnsDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = selectedDns == provider,
                                onClick = {
                                    if (provider == DnsProvider.CUSTOM) { dnsError = null; showCustomDns = true } else onSelectDns(provider)
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
                    Text("Close", color = MaterialTheme.colorScheme.primary)
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
                    "Android's 'Always-on VPN' and 'Block connections without VPN' settings can block traffic when the VPN is unavailable. Lockdown can also block ads because Nsec stays excluded. Confirm the actual setting in Android; opening this page does not enable protection.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showKillSwitchDialog = false
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
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
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
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
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
