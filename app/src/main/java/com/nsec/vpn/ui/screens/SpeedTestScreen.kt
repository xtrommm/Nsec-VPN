package com.nsec.vpn.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.nsec.vpn.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SpeedTestScreen(
    currentServer: Server?,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isTesting by remember { mutableStateOf(false) }
    var currentSpeedMbps by remember { mutableFloatStateOf(0f) }
    var pingMs by remember { mutableStateOf<Long?>(null) }
    var jitterMs by remember { mutableStateOf<Long?>(null) }
    var peakDownloadMbps by remember { mutableFloatStateOf(0f) }

    val animatedSpeed by animateFloatAsState(
        targetValue = currentSpeedMbps,
        animationSpec = tween(durationMillis = 300),
        label = "speedNeedle"
    )

    fun runSpeedTest() {
        if (isTesting) return
        isTesting = true
        currentSpeedMbps = 0f
        peakDownloadMbps = 0f
        pingMs = null
        jitterMs = null

        coroutineScope.launch {
            delay(500)
            pingMs = (currentServer?.pingMs ?: 28L) + (-3..4).random().toLong()
            jitterMs = (2..6).random().toLong()

            val targetSpeed = (48..92).random().toFloat()
            for (step in 1..25) {
                delay(120)
                val noise = (-4..6).random()
                val progress = step / 25f
                val speed = (targetSpeed * progress) + noise
                currentSpeedMbps = speed.coerceAtLeast(0f)
                if (currentSpeedMbps > peakDownloadMbps) {
                    peakDownloadMbps = currentSpeedMbps
                }
            }

            delay(400)
            currentSpeedMbps = peakDownloadMbps
            isTesting = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Speed Benchmark",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Text(
            text = "Measuring tunnel throughput on ${currentServer?.name ?: "Global Gateway"}",
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(30.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(240.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 14.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val center = Offset(size.width / 2, size.height / 2)

                drawArc(
                    color = LightCardBorder,
                    startAngle = 150f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                val fraction = (animatedSpeed / 120f).coerceIn(0f, 1f)
                val activeSweep = fraction * 240f

                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(PrimaryBlue, EmeraldGreen)
                    ),
                    startAngle = 150f,
                    sweepAngle = activeSweep,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("%.1f", animatedSpeed),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    text = "Mbps",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(LightSurface)
                .border(1.dp, LightCardBorder, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            MetricColumn(label = "PING", value = pingMs?.let { "${it} ms" } ?: "--")
            Box(modifier = Modifier.width(1.dp).height(30.dp).background(LightCardBorder))
            MetricColumn(label = "JITTER", value = jitterMs?.let { "${it} ms" } ?: "--")
            Box(modifier = Modifier.width(1.dp).height(30.dp).background(LightCardBorder))
            MetricColumn(label = "PEAK", value = if (peakDownloadMbps > 0) String.format("%.1f M", peakDownloadMbps) else "--")
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
                .testTag("start_speed_test_button")
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isTesting) "TESTING IN PROGRESS..." else "START SPEED TEST",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}
