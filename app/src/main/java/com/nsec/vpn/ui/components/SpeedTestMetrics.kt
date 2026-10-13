package com.nsec.vpn.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Three equal cells remain in one row; values may wrap at accessibility font sizes. */
@Composable
fun SpeedTestMetrics(pingMs: Long?, jitterMs: Long?, peakMbps: Float?, modifier: Modifier = Modifier) {
    val metrics = listOf(
        Triple("PING", pingMs?.let { "$it ms" } ?: "N/A", "ping"),
        Triple("JITTER", jitterMs?.let { "$it ms" } ?: "N/A", "jitter"),
        Triple("PEAK", peakMbps?.let { String.format(Locale.US, "%.1f Mbps", it) } ?: "N/A", "peak")
    )
    Surface(modifier.fillMaxWidth().testTag("speed_metrics"), shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            metrics.forEach { (label, value, tag) ->
                Column(Modifier.weight(1f).testTag("speed_metric_$tag"), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
