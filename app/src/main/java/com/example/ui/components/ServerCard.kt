package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Server
import com.example.ui.theme.*

@Composable
fun ServerCard(
    server: Server,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) CyberCyan else ObsidianCardHighlight
    val backgroundColor = if (isSelected) ObsidianCardHighlight else ObsidianCard

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect)
            .padding(14.dp)
            .testTag("server_item_${server.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Country Flag Emoji
        Text(
            text = server.flagEmoji,
            fontSize = 28.sp,
            modifier = Modifier.padding(end = 12.dp)
        )

        // Server Info
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = server.city,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (server.isRecommended) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "FASTEST",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonEmerald,
                        modifier = Modifier
                            .background(NeonEmerald.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                if (server.isPremiumOnly) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "VIP",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberWarning,
                        modifier = Modifier
                            .background(AmberWarning.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = server.countryName,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        // Latency Signal & Load
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(end = 8.dp)
        ) {
            val ping = server.pingMs ?: 45L
            val pingColor = when {
                ping < 50 -> NeonEmerald
                ping < 90 -> AmberWarning
                else -> CrimsonError
            }

            Text(
                text = "${ping}ms",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = pingColor
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${server.currentLoad}% load",
                fontSize = 11.sp,
                color = TextTertiary
            )
        }

        if (onToggleFavorite != null) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (server.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (server.isFavorite) AmberWarning else TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
