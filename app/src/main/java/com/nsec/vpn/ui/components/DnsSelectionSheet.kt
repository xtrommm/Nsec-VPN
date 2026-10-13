package com.nsec.vpn.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nsec.vpn.data.model.DnsProvider
import com.nsec.vpn.data.model.VpnState

/** Presentation only: validation and tunnel application stay in the existing repository/manager. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsSelectionSheet(
    selectedDns: DnsProvider,
    customDns: String,
    activeDns: List<String>,
    vpnState: VpnState,
    onSelect: (DnsProvider) -> Unit,
    onCustom: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var editingCustom by rememberSaveable { mutableStateOf(selectedDns == DnsProvider.CUSTOM) }
    var customText by rememberSaveable(customDns) { mutableStateOf(customDns) }
    var error by remember { mutableStateOf<String?>(null) }
    val providers = remember { DnsProvider.entries.filter { it != DnsProvider.CUSTOM } + DnsProvider.CUSTOM }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("VPN DNS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close DNS Settings") }
            }
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
                Text(DnsFeedback.status(vpnState, activeDns), color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(14.dp).testTag("dns_active_status"))
            }
            Text("Changes Apply Automatically. DNS Goes Through The VPN For Apps Included In The Tunnel.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            providers.forEach { provider ->
                val chosen = if (editingCustom) provider == DnsProvider.CUSTOM else provider == selectedDns
                Surface(shape = RoundedCornerShape(14.dp), color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .selectable(selected = chosen, role = Role.RadioButton, onClick = {
                            error = null
                            if (provider == DnsProvider.CUSTOM) editingCustom = true
                            else try { onSelect(provider); onDismiss() }
                            catch (failure: IllegalArgumentException) { error = failure.message ?: "Could Not Apply DNS." }
                        }).padding(horizontal = 12.dp, vertical = 10.dp).testTag("dns_${provider.name}"),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = chosen, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(provider.displayName, fontWeight = FontWeight.SemiBold)
                            Text(when (provider) {
                                DnsProvider.AUTOMATIC -> "Use The Connected Node’s DNS Configuration"
                                DnsProvider.CUSTOM -> "Choose Your Own Reachable IPv4 Resolvers"
                                else -> "${provider.primary} • ${provider.secondary}"
                            }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (editingCustom) {
                OutlinedTextField(value = customText, onValueChange = { customText = it.take(200); error = null },
                    modifier = Modifier.fillMaxWidth().testTag("custom_dns_input"), label = { Text("DNS IP Addresses") },
                    supportingText = { Text("Separate Addresses With Commas. IPv6 Resolvers Are Not Supported By This IPv4-Only Tunnel.") },
                    isError = error != null, maxLines = 3)
                Button(onClick = {
                    try { onCustom(customText); onDismiss() }
                    catch (failure: IllegalArgumentException) { error = failure.message ?: "Invalid DNS Resolver." }
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("apply_custom_dns"), enabled = customText.isNotBlank()) {
                    Text("Save Custom DNS")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("dns_error")) }
        }
    }
}
