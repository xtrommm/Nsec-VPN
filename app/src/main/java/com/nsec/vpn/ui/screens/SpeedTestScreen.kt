package com.nsec.vpn.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nsec.vpn.data.model.Server
import com.nsec.vpn.data.model.VpnState
import com.nsec.vpn.data.monetization.AdMobConfig
import com.nsec.vpn.data.monetization.NativeAdManager
import com.nsec.vpn.ui.components.SmartNativeAd
import com.nsec.vpn.ui.theme.*
import com.nsec.vpn.data.network.SpeedTestClient
import com.nsec.vpn.data.local.SpeedTestHistory
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.util.Locale

@Composable
fun SpeedTestScreen(
    currentServer: Server?,
    nativeAdManager: NativeAdManager,
    modifier: Modifier = Modifier,
    userTier: String = "FREE",
    adMobConfig: AdMobConfig = AdMobConfig(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = com.nsec.vpn.vpn.WireGuardTunnelManager.getInstance(context)
    val tunnelState by manager.vpnState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var isTesting by remember { mutableStateOf(value = false) }
    var currentSpeedMbps by remember { mutableFloatStateOf(0f) }
    var pingMs by remember { mutableStateOf<Long?>(null) }
    var jitterMs by remember { mutableStateOf<Long?>(null) }
    var averageDownloadMbps by remember { mutableStateOf<Float?>(null) }
    var averageUploadMbps by remember { mutableStateOf<Float?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var confirmTest by remember { mutableStateOf(false) }
    var testJob by remember { mutableStateOf<Job?>(null) }
    var phase by remember { mutableStateOf("Ready") }
    val historyStore = remember(context) { SpeedTestHistory(context) }
    var history by remember { mutableStateOf(historyStore.read()) }
    DisposableEffect(Unit) { onDispose { testJob?.cancel() } }
    LaunchedEffect(tunnelState) {
        if (tunnelState !is VpnState.Connected && isTesting) {
            testJob?.cancel(); errorMessage = "VPN disconnected. Reconnect and retry."
        }
    }

    val animatedSpeed by animateFloatAsState(
        targetValue = currentSpeedMbps,
        animationSpec = tween(durationMillis = 300),
        label = "speedNeedle",
    )

    val outlineColor = MaterialTheme.colorScheme.outline

    fun runSpeedTest() {
        if (isTesting) return
        errorMessage = null
        val nodeName = manager.connectedNode.value?.nodeName
        if (nodeName.isNullOrBlank()) { errorMessage = "The connected node has no verified name. Reconnect and retry."; return }
        isTesting = true
        currentSpeedMbps = 0f; pingMs = null; jitterMs = null
        averageDownloadMbps = null; averageUploadMbps = null
        testJob = coroutineScope.launch {
            try {
                withTimeout(65000) {
                    SpeedTestClient.measure(context, manager).collect { result ->
                        phase = result.phase; currentSpeedMbps = result.speed
                        if (result.ping > 0) { pingMs = result.ping.toLong(); jitterMs = result.jitter.toLong() }
                        if (result.download > 0) averageDownloadMbps = result.download
                        if (result.upload > 0) averageUploadMbps = result.upload
                        result.error?.let { errorMessage = it }
                        if (result.completed) {
                            history = historyStore.append(SpeedTestHistory.Result(
                                nodeName,
                                System.currentTimeMillis(), result.ping, result.jitter, result.download, result.upload))
                            phase = "Complete"
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                errorMessage = "Speed test timed out. Retry when the connection is stable."
            } catch (cancelled: CancellationException) {
                phase = "Cancelled"; throw cancelled
            } catch (error: Exception) {
                errorMessage = error.message ?: "Speed test failed. Please retry."
            } finally { isTesting = false }
        }
    }

    if (confirmTest) {
        AlertDialog(
            onDismissRequest = { confirmTest = false },
            title = { Text("Run VPN speed test") },
            text = { Text("Tests inside this app using the separately installed Nsec VPN Speed Helper. Up to 192 MiB of test payload plus network overhead may be used. Cloudflare sees the VPN node's exit IP. Ads remain outside the VPN. HTTP latency includes provider response time.") },
            confirmButton = { TextButton(onClick = { confirmTest = false; runSpeedTest() }) { Text("START") } },
            dismissButton = { TextButton(onClick = { confirmTest = false }) { Text("CANCEL") } }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "SPEED TEST",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
            text = "Test your Connected VPN server Speed ",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(modifier = Modifier.height(30.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(240.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 14.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val center = Offset(size.width / 2, size.height / 2)

                drawArc(
                    color = outlineColor,
                    startAngle = 150f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )

                val fraction = (animatedSpeed / 120f).coerceIn(0f, 1f)
                val activeSweep = fraction * 240f

                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(PrimaryBlue, EmeraldGreen),
                    ),
                    startAngle = 150f,
                    sweepAngle = activeSweep,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format(Locale.US, "%.1f", animatedSpeed),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Mbps",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            MetricColumn(label = "PING", value = pingMs?.let { "$it ms" } ?: "N/A")
            Box(modifier = Modifier.width(1.dp).height(30.dp).background(MaterialTheme.colorScheme.outline))
            MetricColumn(label = "JITTER", value = jitterMs?.let { "$it ms" } ?: "N/A")
            Box(modifier = Modifier.width(1.dp).height(30.dp).background(MaterialTheme.colorScheme.outline))
            MetricColumn(label = "DOWN AVG", value = averageDownloadMbps?.let { String.format(Locale.US, "%.1f M", it) } ?: "N/A")
            Box(modifier = Modifier.width(1.dp).height(30.dp).background(MaterialTheme.colorScheme.outline))
            MetricColumn(label = "UP AVG", value = averageUploadMbps?.let { String.format(Locale.US, "%.1f M", it) } ?: "N/A")
        }

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = { confirmTest = true },
            enabled = !isTesting && tunnelState is VpnState.Connected,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("start_speed_test_button"),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isTesting) "TESTING IN PROGRESS..." else "TEST NOW",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }

        Text(phase, modifier = Modifier.padding(top = 8.dp))
        if (isTesting) OutlinedButton(onClick = { testJob?.cancel() }) { Text("CANCEL TEST") }
        if (history.isNotEmpty()) {
            Text("Recent results", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            history.forEach { result ->
                Text("${result.nodeName} · ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(result.timestamp))} · " +
                    String.format(Locale.US, "↓ %.1f / ↑ %.1f Mbps", result.download, result.upload),
                    fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        errorMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        }

        Spacer(modifier = Modifier.height(28.dp))

        SmartNativeAd(
            nativeAdManager = nativeAdManager,
            adMobConfig = adMobConfig,
            userTier = userTier,
            vpnState = tunnelState,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
