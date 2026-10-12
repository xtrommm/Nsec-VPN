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
                    rows.add(DiagnosticObservation("API / ad network", if (validated) "Android reports a validated internet connection. This app’s own traffic stays outside the VPN." else "Android has not validated the ordinary internet connection. Check Wi-Fi or mobile data.", validated))
                    try {
                        val response = withTimeout(8_000) { NetworkModule.api.getConfig() }
                        val ok = response.isSuccessful && response.body()?.success == true
                        rows.add(DiagnosticObservation("Backend API", if (ok) "The actual HTTPS configuration request succeeded." else "API returned HTTP ${response.code()}.", ok))
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) { rows.add(DiagnosticObservation("Backend API", "Request timed out. Check connectivity and backend availability.", false)) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { rows.add(DiagnosticObservation("Backend API", "Could not reach the HTTPS API. Check internet access, date/time and backend availability.", false)) }
                    val connected = manager.vpnState.value is VpnState.Connected
                    val age = manager.latestHandshakeAgeSeconds
                    rows.add(DiagnosticObservation("WireGuard handshake / UDP", if (connected && age <= WireGuardTunnelManager.STALE_HANDSHAKE_SECONDS) "Actual WireGuard handshake: ${age}s ago. UDP transport worked for that handshake." else "No current verified tunnel. Connect first. A failed handshake alone cannot identify an ISP block or cloud firewall.", TroubleshootingEvidence.handshake(connected, age, WireGuardTunnelManager.STALE_HANDSHAKE_SECONDS)))
                    val stats = manager.vpnStats.value
                    rows.add(DiagnosticObservation("Tunnel traffic", if (connected) "WireGuard counters: RX ${stats.totalBytesReceived} bytes, TX ${stats.totalBytesSent} bytes. Use another included app to confirm exit IP." else "Not measured: no active tunnel.", TroubleshootingEvidence.traffic(connected, stats.totalBytesReceived, stats.totalBytesSent)))
                    rows.add(DiagnosticObservation("DNS configuration", manager.activeDns.value.takeIf { it.isNotEmpty() }?.let { "Active VPN DNS: ${it.joinToString()}. This excluded app cannot prove DNS resolution inside the tunnel; use an included browser DNS leak test." } ?: "Not measured: connect first. VPN DNS is ordinary DNS carried inside encrypted WireGuard; it is not DNS-over-TLS."))
                    rows.add(DiagnosticObservation("MTU", manager.activeTunnelConfig?.let { "Configured MTU: ${it.mtu}. Path MTU and fragmentation have not been measured. Do not infer that MTU is optimal from a successful API request." } ?: "No active configuration. MTU cannot be tested before connecting."))
                    rows
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { observations = listOf(DiagnosticObservation("Diagnostics", "Could not complete checks. Retry without changing network settings.", false)) }
            finally { isRunning = false }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Troubleshooting Assistant", fontSize = 18.sp) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Checks report real observations. Unmeasured checks remain unverified; no client IPs, keys or traffic content are logged.", fontSize = 13.sp)
            if (observations.isEmpty()) Text("Run checks to inspect network access, the API and the current WireGuard tunnel.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            observations.forEach { row ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)).padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(if (row.passed == true) Icons.Default.CheckCircle else if (row.passed == false) Icons.Default.Warning else Icons.Default.Info, contentDescription = null, tint = if (row.passed == true) EmeraldGreen else if (row.passed == false) AmberWarning else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp)); Column { Text(row.title, fontWeight = FontWeight.SemiBold); Text(row.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Button(onClick = { runDiagnostics() }, enabled = !isRunning, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Refresh, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(if (isRunning) "Running checks…" else "Run diagnostics") }
        }
    }
}
