package com.nsec.vpn.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nsec.vpn.data.model.Server

object VipAccessGate {
    fun requiresVip(server: Server, tier: String): Boolean =
        server.isPremiumOnly && tier !in setOf("VIP", "REWARDED_PASS")
}

@Composable
fun VipRequiredDialog(rewardEnabled: Boolean, onDismiss: () -> Unit,
                      onPurchase: () -> Unit, onWatchAd: () -> Unit, rewardDetail: String? = null) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxWidth().testTag("vip_access_popup"), shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), tonalElevation = 2.dp) {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(Icons.Default.Star, null, Modifier.padding(12.dp).size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("vip_popup_close")) {
                        Icon(Icons.Default.Close, "Close VIP Options")
                    }
                }
                Text("Unlock VIP Servers", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Purchase VIP Plan Or Watch An Ad To Unlock VIP Servers.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onWatchAd, enabled = rewardEnabled, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Watch An Ad", fontWeight = FontWeight.SemiBold)
                }
                Text(rewardDetail ?: if (rewardEnabled) "Unlock Ad-Free Access And VIP Servers." else "Rewarded Ads Are Unavailable Right Now.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onPurchase, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(14.dp)) { Text("Purchase VIP Plan") }
            }
        }
    }
}
