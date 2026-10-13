package com.nsec.vpn.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Balanced rows: two by two on phones, four across on wide screens, one at large text sizes. */
@Composable
fun SpeedTestMetrics(pingMs: Long?, jitterMs: Long?, downloadMbps: Float?, uploadMbps: Float?,
                     modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().testTag("speed_metrics"), shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        BoxWithConstraints(Modifier.padding(12.dp)) {
            val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            val columns = when {
                maxWidth >= 520.dp * scale -> 4
                maxWidth >= 240.dp * scale -> 2
                else -> 1
            }
            val metrics = listOf(
                Triple("PING", pingMs?.let { "$it ms" } ?: "N/A", "ping"),
                Triple("JITTER", jitterMs?.let { "$it ms" } ?: "N/A", "jitter"),
                Triple("DOWN AVG", downloadMbps?.let { String.format(Locale.US, "%.1f M", it) } ?: "N/A", "download"),
                Triple("UP AVG", uploadMbps?.let { String.format(Locale.US, "%.1f M", it) } ?: "N/A", "upload")
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                metrics.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (label, value, tag) ->
                            Column(Modifier.weight(1f).testTag("speed_metric_$tag"), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(4.dp))
                                Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}
