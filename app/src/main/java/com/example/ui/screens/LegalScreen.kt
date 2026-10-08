package com.example.ui.screens

import androidx.compose.foundation.background
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
import com.example.ui.theme.*

import androidx.compose.foundation.border

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zero-Logs Privacy Policy", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightBackground)
            )
        },
        containerColor = LightBackground
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
                    .background(EmeraldContainer)
                    .border(1.dp, EmeraldGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Strict No-Logging Guarantee: Nsec VPN (nsecvpn.com) never logs, stores, or shares your personal network traffic.",
                    fontSize = 12.sp,
                    color = TextPrimary,
                    lineHeight = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            PolicySection("1. Information We Do NOT Collect", "We operate an absolute zero-logs service:\n• No originating IP addresses\n• No connection timestamps correlated with users\n• No DNS queries or web browsing history\n• No transmitted data payloads or contents\n\nAll WireGuard cryptographic handshakes reside in volatile kernel memory and are immediately cleared when disconnected.")

            PolicySection("2. WireGuard Cryptographic Key Exchange", "Your Curve25519 private key is generated locally inside your device's secure environment. Only your public key is sent to the server for tunnel handshake configuration.")

            PolicySection("3. Anonymous Device Authentication", "You do not need an email address or password to use Nsec VPN. Each installation is identified solely by an anonymous hardware UUID stored locally.")

            PolicySection("4. Google Play Compliance", "Nsec VPN strictly utilizes the Android VpnService API solely for user-initiated VPN tunnel encapsulation per nsecvpn.com policies.")
        }
    }
}

@Composable
private fun PolicySection(title: String, content: String) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
        Spacer(modifier = Modifier.height(6.dp))
        Text(content, fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp)
    }
}
