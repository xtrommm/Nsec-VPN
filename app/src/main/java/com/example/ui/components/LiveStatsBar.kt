package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VpnStats
import com.example.ui.theme.*

@Composable
fun LiveStatsBar(
    stats: VpnStats,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianCard)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatItem(
            icon = Icons.Default.Timer,
            iconTint = CyberCyan,
            label = "DURATION",
            value = stats.formattedDuration
        )

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(36.dp)
                .background(ObsidianCardHighlight)
        )

        StatItem(
            icon = Icons.Default.ArrowDownward,
            iconTint = NeonEmerald,
            label = "DOWNLOAD",
            value = String.format("%.1f Mb/s", stats.downloadSpeedMbps)
        )

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(36.dp)
                .background(ObsidianCardHighlight)
        )

        StatItem(
            icon = Icons.Default.ArrowUpward,
            iconTint = ElectricBlue,
            label = "UPLOAD",
            value = String.format("%.1f Mb/s", stats.uploadSpeedMbps)
        )
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                letterSpacing = 0.8.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}
