package com.nsec.vpn.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.nsec.vpn.data.model.Server

object VipAccessGate {
    fun requiresVip(server: Server, tier: String): Boolean =
        server.isPremiumOnly && tier !in setOf("VIP", "REWARDED_PASS")
}

@Composable
fun VipRequiredDialog(rewardEnabled: Boolean, onDismiss: () -> Unit,
                      onPurchase: () -> Unit, onWatchAd: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("VIP Server") },
        text = { Text("Purchase VIP Plan Or Watch An Ad To Unlock VIP Servers.") },
        confirmButton = { TextButton(onClick = onPurchase) { Text("Purchase VIP Plan") } },
        dismissButton = { TextButton(onClick = onWatchAd, enabled = rewardEnabled) { Text("Watch An Ad") } })
}
