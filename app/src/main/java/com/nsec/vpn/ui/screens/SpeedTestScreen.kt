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
import com.nsec.vpn.data.network.NodeLatencyProbe
import com.nsec.vpn.data.network.VpnSpeedTest
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    var confirmBrowserTest by remember { mutableStateOf(false) }

    val animatedSpeed by animateFloatAsState(
        targetValue = currentSpeedMbps,
        animationSpec = tween(durationMillis = 300),
        label = "speedNeedle",
    )

    val outlineColor = MaterialTheme.colorScheme.outline

    fun runSpeedTest() {
        if (isTesting) return
        errorMessage = null
        if (!VpnSpeedTest.openBrowser(context, manager)) {
            errorMessage = "No browser included in the VPN is available. Include a browser in split tunneling and reconnect."
            return
        }
        val node = manager.connectedNode.value
        val start = manager.vpnStats.value
        val startedAt = SystemClock.elapsedRealtime()
        isTesting = true
        currentSpeedMbps = 0f
        averageDownloadMbps = null
        averageUploadMbps = null
        pingMs = null
        jitterMs = null
        coroutineScope.launch {
            val latency = launch {
                val samples = mutableListOf<Long>()
                repeat(3) {
                    NodeLatencyProbe.measure(node?.publicIpv4 ?: currentServer?.publicIpv4)?.let(samples::add)
                    delay(100)
                }
                pingMs = samples.takeIf { it.isNotEmpty() }?.average()?.toLong()
                jitterMs = VpnSpeedTest.jitter(samples)
            }
            try {
                var previous = start
                var previousTime = startedAt
                repeat(30) {
                    delay(1000L)
                    if (manager.vpnState.value !is VpnState.Connected || manager.connectedNode.value != node) {
                        errorMessage = "VPN changed or disconnected. Reconnect and run the test again."
                        return@launch
                    }
                    val now = SystemClock.elapsedRealtime()
                    val stats = manager.vpnStats.value
                    val speed = VpnSpeedTest.megabitsPerSecond(previous.totalBytesReceived,
                        stats.totalBytesReceived, now - previousTime)
                    val down = VpnSpeedTest.megabitsPerSecond(start.totalBytesReceived,
                        stats.totalBytesReceived, now - startedAt)
                    val up = VpnSpeedTest.megabitsPerSecond(start.totalBytesSent,
                        stats.totalBytesSent, now - startedAt)
                    if (speed == null || down == null || up == null) {
                        errorMessage = "Tunnel counters reset. Run the test again."
                        return@launch
                    }
                    currentSpeedMbps = speed
                    averageDownloadMbps = down
                    averageUploadMbps = up
                    previous = stats
                    previousTime = now
                }
                if (averageDownloadMbps == 0f && averageUploadMbps == 0f)
                    errorMessage = "No tunnel traffic measured. Start the test in the browser and try again."
            } finally {
                latency.cancel()
                isTesting = false
            }
        }
    }

    if (confirmBrowserTest) {
        AlertDialog(
            onDismissRequest = { confirmBrowserTest = false },
            title = { Text("Run VPN speed test") },
            text = { Text("Opens Cloudflare in a browser included in the VPN. The test uses mobile or Wi-Fi data and shares your VPN exit IP with Cloudflare. Return here to see real tunnel traffic.") },
            confirmButton = { TextButton(onClick = { confirmBrowserTest = false; runSpeedTest() }) { Text("START") } },
            dismissButton = { TextButton(onClick = { confirmBrowserTest = false }) { Text("CANCEL") } }
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
            text = "The browser shows tested download and upload speeds. This gauge measures all tunnel traffic for 30 seconds. Node ping and jitter measure your direct route to the node; ads remain outside the VPN.",
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
                    color = PrimaryBlue,
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
            MetricColumn(label = "DOWN AVG", value = averageDownloadMbps?.let { String.format(Locale.US, "%.1f M", it) } ?: "—")
            Box(modifier = Modifier.width(1.dp).height(30.dp).background(MaterialTheme.colorScheme.outline))
            MetricColumn(label = "UP AVG", value = averageUploadMbps?.let { String.format(Locale.US, "%.1f M", it) } ?: "—")
        }

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = { confirmBrowserTest = true },
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
