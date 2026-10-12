package com.nsec.vpn.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VipRewardCard(offer: RewardOffer, busy: Boolean, onWatchReward: () -> Unit,
                  onMoreOptions: () -> Unit, modifier: Modifier = Modifier,
                  compact: Boolean = false, showMoreOptions: Boolean = true) {
    if (compact && showMoreOptions) {
        CompactVipOffer(offer, busy, onWatchReward, onMoreOptions, modifier)
        return
    }
    Card(modifier = modifier.fillMaxWidth().testTag(if (showMoreOptions) "home_reward_card" else "vip_sheet_reward_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))) {
        Column(Modifier.padding(if (compact) 16.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            if (!compact) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Text(if (offer.available) "WATCH AN AD. GET VIP." else "NSEC VIP", fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    letterSpacing = 1.sp)
            }
            Text(offer.title, fontSize = if (compact) 22.sp else 27.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Benefit("Ad-free", Modifier.weight(1f))
                Benefit("VIP servers", Modifier.weight(1f))
            }
            Text(offer.detail, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp)
            if (offer.available || busy) {
                Button(onClick = onWatchReward, enabled = offer.available && !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag(if (showMoreOptions) "watch_vip_reward" else "sheet_vip_reward"),
                    shape = RoundedCornerShape(14.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (busy) "Preparing your reward…" else "Watch ad & unlock", fontWeight = FontWeight.Bold)
                }
            }
            if (showMoreOptions) TextButton(onClick = onMoreOptions, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Explore VIP options")
            }
        }
    }
}

/** Home keeps only the offer and action; the sheet retains the full explanation. */
@Composable
private fun CompactVipOffer(offer: RewardOffer, busy: Boolean, onWatchReward: () -> Unit,
                            onMoreOptions: () -> Unit, modifier: Modifier) {
    Card(onClick = onMoreOptions,
        modifier = modifier.fillMaxWidth().testTag("home_reward_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(offer.title, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(if (offer.available) "Ad-free + VIP servers" else offer.detail,
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (offer.available || busy) {
                Button(onClick = onWatchReward, enabled = offer.available && !busy,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("watch_vip_reward"),
                    contentPadding = PaddingValues(horizontal = 12.dp), shape = RoundedCornerShape(12.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Watch ad", fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = onMoreOptions, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Details")
                }
            }
        }
    }
}

@Composable
private fun Benefit(label: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipAccessSheet(offer: RewardOffer, busy: Boolean, onDismiss: () -> Unit,
                   onWatchReward: () -> Unit, onPurchase: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("A better way to VIP", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
            Text("Choose a rewarded pass or a paid plan. Both include ad-free access and VIP servers.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            VipRewardCard(offer, busy, onWatchReward, onDismiss, showMoreOptions = false)
            Text("PREFER A PAID PLAN?", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
            Plan("Monthly VIP", "nsecvpn_premium_monthly", onPurchase)
            Plan("Yearly VIP", "nsecvpn_premium_yearly", onPurchase)
            Text("Google Play shows the exact price and purchase terms before you pay. Availability and network performance depend on the node.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Plan(label: String, product: String, onPurchase: (String) -> Unit) {
    OutlinedButton(onClick = { onPurchase(product) }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontWeight = FontWeight.Bold)
            Text("View plan →")
        }
    }
}
