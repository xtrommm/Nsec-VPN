package com.nsec.vpn.ui.screens

import com.nsec.vpn.data.network.TroubleshootingEvidence
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nsec.vpn.core.network.NetworkModule
import com.nsec.vpn.data.model.VpnState
import com.nsec.vpn.ui.theme.*
import com.nsec.vpn.vpn.WireGuardTunnelManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private data class DiagnosticObservation(val title: String, val detail: String, val passed: Boolean? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TroubleshootScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { WireGuardTunnelManager.getInstance(context) }
    var isRunning by remember { mutableStateOf(false) }
    var observations by remember { mutableStateOf<List<DiagnosticObservation>>(emptyList()) }
    fun runDiagnostics() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            try {
                observations = withContext(Dispatchers.IO) {
                    val rows = mutableListOf<DiagnosticObservation>()
                    val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
                    val validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                    rows.add(DiagnosticObservation("API / Ad Network", if (validated) "Android Reports A Validated Internet Connection. This App’s Own Traffic Stays Outside The VPN." else "Android Has Not Validated The Ordinary Internet Connection. Check Wi-Fi Or Mobile Data.", validated))
                    try {
                        val response = withTimeout(8_000) { NetworkModule.api.getConfig() }
                        val ok = response.isSuccessful && response.body()?.success == true
                        rows.add(DiagnosticObservation("Backend API", if (ok) "The Actual HTTPS Configuration Request Succeeded." else "API Returned HTTP ${response.code()}.", ok))
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) { rows.add(DiagnosticObservation("Backend API", "Request Timed Out. Check Connectivity And Backend Availability.", false)) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { rows.add(DiagnosticObservation("Backend API", "Could Not Reach The HTTPS API. Check Internet Access, Date/Time And Backend Availability.", false)) }
                    val connected = manager.vpnState.value is VpnState.Connected
                    val age = manager.latestHandshakeAgeSeconds
                    rows.add(DiagnosticObservation("WireGuard Handshake / UDP", if (connected && age <= WireGuardTunnelManager.STALE_HANDSHAKE_SECONDS) "Actual WireGuard Handshake: ${age}s Ago. UDP Transport Worked For That Handshake." else "No Current Verified Tunnel. Connect First. A Failed Handshake Alone Cannot Identify An ISP Block Or Cloud Firewall.", TroubleshootingEvidence.handshake(connected, age, WireGuardTunnelManager.STALE_HANDSHAKE_SECONDS)))
                    val stats = manager.vpnStats.value
                    rows.add(DiagnosticObservation("Tunnel Traffic", if (connected) "WireGuard Counters: RX ${stats.totalBytesReceived} Bytes, TX ${stats.totalBytesSent} Bytes. Use Another Included App To Confirm Exit IP." else "Not Measured: No Active Tunnel.", TroubleshootingEvidence.traffic(connected, stats.totalBytesReceived, stats.totalBytesSent)))
                    rows.add(DiagnosticObservation("DNS Configuration", manager.activeDns.value.takeIf { it.isNotEmpty() }?.let { "Active VPN DNS: ${it.joinToString()}. This Excluded App Cannot Prove DNS Resolution Inside The Tunnel; Use An Included Browser DNS Leak Test." } ?: "Not Measured: Connect First. VPN DNS Is Ordinary DNS Carried Inside Encrypted WireGuard; It Is Not DNS-Over-TLS."))
                    rows.add(DiagnosticObservation("MTU", manager.activeTunnelConfig?.let { "Configured MTU: ${it.mtu}. Path MTU And Fragmentation Have Not Been Measured. Do Not Infer That MTU Is Optimal From A Successful API Request." } ?: "No Active Configuration. MTU Cannot Be Tested Before Connecting."))
                    rows
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { observations = listOf(DiagnosticObservation("Diagnostics", "Could Not Complete Checks. Retry Without Changing Network Settings.", false)) }
            finally { isRunning = false }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Troubleshooting Assistant", fontSize = 18.sp) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Checks Report Real Observations. Unmeasured Checks Remain Unverified; No Client IPs, Keys Or Traffic Content Are Logged.", fontSize = 13.sp)
            if (observations.isEmpty()) Text("Run Checks To Inspect Network Access, The API And The Current WireGuard Tunnel.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            observations.forEach { row ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)).padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(if (row.passed == true) Icons.Default.CheckCircle else if (row.passed == false) Icons.Default.Warning else Icons.Default.Info, contentDescription = null, tint = if (row.passed == true) EmeraldGreen else if (row.passed == false) AmberWarning else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp)); Column { Text(row.title, fontWeight = FontWeight.SemiBold); Text(row.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Button(onClick = { runDiagnostics() }, enabled = !isRunning, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Refresh, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(if (isRunning) "Running Checks…" else "Run Diagnostics") }
        }
    }
}
