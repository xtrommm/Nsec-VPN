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
import kotlinx.coroutines.launch
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
    activeDns: List<String> = emptyList(),
    vpnState: com.nsec.vpn.data.model.VpnState = com.nsec.vpn.data.model.VpnState.Disconnected,
    onRestorePurchases: (suspend () -> Unit)? = null,
    onRecoveryStatus: suspend () -> Boolean = { error("Recovery Unavailable") },
    onSetRecoveryPin: suspend (String, String?) -> Unit = { _, _ -> error("Recovery Unavailable") },
    onRecoverDevice: suspend (String, String) -> Unit = { _, _ -> error("Recovery Unavailable") }
) {
    val context = LocalContext.current
    var showRecovery by remember { mutableStateOf(false) }
    var restoringPurchases by remember { mutableStateOf(false) }
    val settingsScope = rememberCoroutineScope()
    var showRequestDialog by remember { mutableStateOf(false) }
    fun copy(value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Nsec VPN", value))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
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

        SettingsClickableItem(
            icon = Icons.Default.Shield,
            title = "Android VPN Lockdown",
            subtitle = "Configure Always-On VPN And Blocking In Android Settings",
            onClick = { showKillSwitchDialog = true }
        )

        SettingsClickableItem(
            icon = Icons.Default.Dns,
            title = "DNS Resolver Through VPN",
            subtitle = selectedDns.displayName + "\n" + com.nsec.vpn.ui.components.DnsFeedback.status(vpnState, activeDns),
            onClick = { showDnsDialog = true }
        )

        SettingsToggleItem(
            icon = Icons.AutoMirrored.Filled.AltRoute,
            title = "Split Tunneling",
            subtitle = "Allow Select Apps To Bypass The VPN Tunnel",
            isChecked = splitTunnelingEnabled,
            onCheckedChange = { onToggleSplitTunneling(it) },
            trailingAction = if (splitTunnelingEnabled) {
                {
                    Text(
                        text = "MANAGE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.heightIn(min = 48.dp).clickable { onOpenSplitTunnelApps() }.padding(vertical = 12.dp)
                    )
                }
            } else null
        )

        Spacer(modifier = Modifier.height(20.dp))

        SectionHeader("CONNECTION")

        SettingsToggleItem(
            icon = Icons.Default.PowerSettingsNew,
            title = "Auto-Connect On Launch",
            subtitle = "Immediately Secure Your Network When Opening Nsec VPN",
            isChecked = autoConnectEnabled,
            onCheckedChange = onToggleAutoConnect
        )

        SettingsClickableItem(
            icon = Icons.Default.Build,
            title = "Troubleshooting Assistant",
            subtitle = "Check API Access, Handshake And Tunnel Observations",
            onClick = onOpenTroubleshooting
        )

        Spacer(modifier = Modifier.height(20.dp))

        SectionHeader("ABOUT")

        SettingsClickableItem(
            icon = Icons.Default.CardGiftcard,
            title = "Redeem Promo Code",
            subtitle = "Enter Code To Unlock Free Premium VIP Access",
            onClick = onRedeemPromoCode
        )

        SettingsClickableItem(icon = Icons.Default.Email, title = "Support Email",
            subtitle = supportEmail ?: "Support Address Has Not Been Configured", onClick = {
                if (supportEmail == null) Toast.makeText(context, "The Operator Has Not Configured Support Email Yet.", Toast.LENGTH_LONG).show()
                else try { context.startActivity(Intent(Intent.ACTION_SENDTO, ("mailto:" + android.net.Uri.encode(supportEmail, "@.")).toUri())) }
                catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, "No Email App Installed. Use Copy Email.", Toast.LENGTH_LONG).show() }
            })
        if (supportEmail != null) TextButton(onClick = { copy(supportEmail) }) { Text("Copy Email") }
        SettingsClickableItem(icon = Icons.Default.Public, title = "Request Server", subtitle = "Suggest A Country And City", onClick = { showRequestDialog = true })

        SettingsClickableItem(
            icon = Icons.Default.Language,
            title = "Official Website",
            subtitle = "nsecvpn.com",
            onClick = {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, "https://nsecvpn.com".toUri())
                    context.startActivity(browserIntent)
                } catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, "No Browser Installed.", Toast.LENGTH_SHORT).show() }
            }
        )

        SettingsClickableItem(
            icon = Icons.Default.Description,
            title = "Zero-Logs Privacy Policy",
            subtitle = "Traffic Privacy And Operational Data Policy",
            onClick = onOpenPrivacyPolicy
        )

        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader("ACCOUNT & RECOVERY")
        if (onRestorePurchases != null) SettingsClickableItem(icon = Icons.Default.Restore,
            title = if (restoringPurchases) "Restoring Purchases…" else "Restore Google Play Purchases",
            subtitle = "Recheck Existing Subscriptions Without Making A Purchase", onClick = {
                if (!restoringPurchases) {
                    restoringPurchases = true
                    settingsScope.launch {
                        try {
                            onRestorePurchases()
                            Toast.makeText(context, "Purchases Verified And VIP Refreshed.", Toast.LENGTH_LONG).show()
                        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                            Toast.makeText(context, "Purchase Restore Timed Out. Please Retry.", Toast.LENGTH_LONG).show()
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (error: Exception) { Toast.makeText(context, error.message ?: "Purchase Restore Failed.", Toast.LENGTH_LONG).show() }
                        finally { restoringPurchases = false }
                    }
                }
            })
        SettingsClickableItem(icon = Icons.Default.Lock, title = "Device VIP Recovery",
            subtitle = "Protect Or Restore VIP With Device ID And A Private PIN", onClick = { showRecovery = true })
        Spacer(Modifier.height(12.dp))
        com.nsec.vpn.ui.components.DeviceIdCard(deviceId, tagPrefix = "settings_device")
        Spacer(Modifier.height(20.dp))
        SectionHeader("DEVELOPER / DEBUG")
        SettingsClickableItem(
            icon = Icons.Default.Info,
            title = "App Version",
            subtitle = "Nsec VPN V${com.nsec.vpn.BuildConfig.VERSION_NAME}",
            onClick = { Toast.makeText(context, "Version ${com.nsec.vpn.BuildConfig.VERSION_NAME} (${com.nsec.vpn.BuildConfig.VERSION_CODE})", Toast.LENGTH_SHORT).show() }
        )

        if (com.nsec.vpn.BuildConfig.DEBUG) {
            SettingsClickableItem(
                icon = Icons.Default.BugReport,
                title = "WireGuard & Peer Diagnostics",
                subtitle = "View Live Handshake, Peer API Status, And Endpoint",
                onClick = onOpenDebugDiagnostics
            )
        }

    }

    if (showRecovery) com.nsec.vpn.ui.components.DeviceRecoverySheet(deviceId,
        onDismiss = { showRecovery = false }, onStatus = onRecoveryStatus,
        onSetPin = onSetRecoveryPin, onRecover = onRecoverDevice)

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
                                    showThemeDialog = false
                                    onSelectThemeMode(mode)
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    showThemeDialog = false
                                    onSelectThemeMode(mode)
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

    if (showDnsDialog) com.nsec.vpn.ui.components.DnsSelectionSheet(
        selectedDns = selectedDns, customDns = customDns, activeDns = activeDns, vpnState = vpnState,
        onSelect = onSelectDns, onCustom = onCustomDns, onDismiss = { showDnsDialog = false }
    )

    if (showKillSwitchDialog) {
        AlertDialog(
            onDismissRequest = { showKillSwitchDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Enable Android System Kill Switch", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Android's 'Always-On VPN' And 'Block Connections Without VPN' Settings Can Block Traffic When The VPN Is Unavailable. Lockdown Can Also Block Ads Because Nsec Stays Excluded. Confirm The Actual Setting In Android; Opening This Page Does Not Enable Protection.",
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
                        } catch (_: Exception) {
                            Toast.makeText(context, "Android VPN Settings Are Unavailable On This Device.", Toast.LENGTH_LONG).show()
                        }
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
