package com.nsec.vpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nsec.vpn.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zero-Logs Privacy Policy", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.emeraldBadgeContainer)
                    .border(1.dp, EmeraldGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Nsec VPN Does Not Store Browsing Activity, DNS Queries, Traffic Content, Or Originating Client IP Addresses In Its App, Backend, Or Agent.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            PolicySection("1. Information We Do NOT Collect", "We Do Not Persist Originating Client IP Addresses, DNS Queries, Browsing History, Or Traffic Contents. WireGuard Keeps Current Handshake And Transfer Statistics In Kernel Memory. Device Registration And Expiring Peer Configuration Are Persisted To Authenticate Access And Restore Peers After Reboot; These Are Service Records, Not Browsing Logs.")

            PolicySection("2. WireGuard Cryptographic Key Exchange", "Your Curve25519 Private Key Is Generated Locally Inside Your Device's Secure Environment. Only Your Public Key Is Sent To The Server For Tunnel Handshake Configuration.")

            PolicySection("3. Anonymous Device Authentication", "You Do Not Need An Email Address Or Password To Use Nsec VPN. A Randomly Generated Installation ID And A Device-Held Registration Secret Authorize Access. Android Keystore Protects The Local Encryption Key; Hardware-Backed Storage Depends On The Device. Clearing Local App Data Does Not Delete Existing Backend Registration Or Billing Records. Optional Recovery Uses Your Saved Device ID And A Private PIN; The Backend Stores A Salted PIN Hash And Attempt/Lock Metadata, Not The Raw PIN. Set Recovery Before Clearing Data.")

            PolicySection("4. Google Play Compliance", "Nsec VPN Strictly Utilizes The Android VpnService API Solely For User-Initiated VPN Tunnel Encapsulation Per nsecvpn.com Policies.")
        }
    }
}

@Composable
private fun PolicySection(title: String, content: String) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(content, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 19.sp)
    }
}
