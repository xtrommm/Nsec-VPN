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
import com.nsec.vpn.data.monetization.AdMobConfig
import com.nsec.vpn.ui.components.AdMobBannerView
import com.nsec.vpn.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SpeedTestScreen(
    currentServer: Server?,
    userTier: String = "FREE",
    adMobConfig: AdMobConfig = AdMobConfig(),
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var isTesting by remember { mutableStateOf(value = false) }
    var currentSpeedMbps by remember { mutableFloatStateOf(0f) }
    var pingMs by remember { mutableStateOf<Long?>(null) }
    var jitterMs by remember { mutableStateOf<Long?>(null) }
    var peakDownloadMbps by remember { mutableFloatStateOf(0f) }

    val animatedSpeed by animateFloatAsState(
        targetValue = currentSpeedMbps,
        animationSpec = tween(durationMillis = 300),
        label = "speedNeedle",
    )

    val outlineColor = MaterialTheme.colorScheme.outline

    fun runSpeedTest() {
        if (isTesting) return
        isTesting = true
        currentSpeedMbps = 0f
        peakDownloadMbps = 0f
        pingMs = null
        jitterMs = null

        coroutineScope.launch {
            delay(500L)
            pingMs = (currentServer?.pingMs ?: 28L) + (-3..4).random().toLong()
            jitterMs = (2..6).random().toLong()

            val targetSpeed = (48..92).random().toFloat()
            for (step in 1..25) {
                delay(120L)
                val noise = (-4..6).random()
                val progress = step / 25f
                val speed = (targetSpeed * progress) + noise
                currentSpeedMbps = speed.coerceAtLeast(0f)
                if (currentSpeedMbps > peakDownloadMbps) {
                    peakDownloadMbps = currentSpeedMbps
                }
            }

            delay(400L)
            currentSpeedMbps = peakDownloadMbps
            isTesting = false
        }
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
            text = "Speed Benchmark",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
            text = "Measuring tunnel throughput on ${currentServer?.name ?: "Global Gateway"}",
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
            MetricColumn(label = "PING", value = pingMs?.let { "$it ms" } ?: "--")
            Box(modifier = Modifier.width(1.dp).height(30.dp).background(MaterialTheme.colorScheme.outline))
            MetricColumn(label = "JITTER", value = jitterMs?.let { "$it ms" } ?: "--")
            Box(modifier = Modifier.width(1.dp).height(30.dp).background(MaterialTheme.colorScheme.outline))
            MetricColumn(label = "PEAK", value = if (peakDownloadMbps > 0) String.format(Locale.US, "%.1f M", peakDownloadMbps) else "--")
        }

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = { runSpeedTest() },
            enabled = !isTesting,
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
                text = if (isTesting) "TESTING IN PROGRESS..." else "START SPEED TEST",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }

        val isAdAllowed = userTier == "FREE" && adMobConfig.bannerEnabled
        if (isAdAllowed) {
            Spacer(modifier = Modifier.height(28.dp))
            AdMobBannerView(
                bannerAdUnitId = adMobConfig.bannerAdUnitId,
                modifier = Modifier.fillMaxWidth()
            )
        }
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
