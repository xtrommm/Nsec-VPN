package com.nsec.vpn.ui.components

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nsec.vpn.data.model.Server
import com.nsec.vpn.ui.theme.*

@Composable
fun ServerCard(
    server: Server,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavorite: (() -> Unit)? = null
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect)
            .padding(14.dp)
            .testTag("server_item_${server.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = server.flagEmoji,
            fontSize = 28.sp,
            modifier = Modifier.padding(end = 12.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = server.city,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.alignByBaseline().testTag("server_title_${server.id}")
                )
                if (server.totalNodes > 1) ServerBadge(
                    "${server.totalNodes} NODES", MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.primaryContainer,
                    Modifier.alignByBaseline().testTag("server_nodes_${server.id}")
                )
                if (server.isRecommended) ServerBadge(
                    "FASTEST", MaterialTheme.colorScheme.statusSuccess,
                    MaterialTheme.colorScheme.emeraldBadgeContainer,
                    Modifier.alignByBaseline().testTag("server_fastest_${server.id}")
                )
                if (server.isPremiumOnly) ServerBadge(
                    "VIP", MaterialTheme.colorScheme.statusWarning,
                    MaterialTheme.colorScheme.amberBadgeContainer,
                    Modifier.alignByBaseline().testTag("server_vip_${server.id}")
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = if (server.totalNodes > 1) "${server.countryName} • Load Balanced Cluster" else server.countryName,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(end = 8.dp)
        ) {
            val ping = server.pingMs
            val pingColor = when {
                ping == null -> MaterialTheme.colorScheme.onSurfaceVariant
                ping < 50 -> MaterialTheme.colorScheme.statusSuccess
                ping < 90 -> MaterialTheme.colorScheme.statusWarning
                else -> CrimsonError
            }

            Text(
                text = ping?.let { "${it}ms" } ?: "Unavailable",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = pingColor
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${server.currentLoad}% Load",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (onToggleFavorite != null) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (server.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (server.isFavorite) "Remove ${server.city} From Favorites" else "Add ${server.city} To Favorites",
                    tint = if (server.isFavorite) AmberWarning else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ServerBadge(text: String, foreground: androidx.compose.ui.graphics.Color,
                        background: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Text(text, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = foreground,
        modifier = modifier.background(background, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp))
}
