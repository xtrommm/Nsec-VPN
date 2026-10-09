package com.nsec.vpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nsec.vpn.data.model.Server
import com.nsec.vpn.ui.components.AdBannerView
import com.nsec.vpn.ui.components.ServerCard
import com.nsec.vpn.ui.theme.*

@Composable
fun ServerListScreen(
    servers: List<Server>,
    selectedServer: Server?,
    userTier: String,
    onSelectServer: (Server) -> Unit,
    onAutoSelectBest: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRefreshPings: () -> Unit,
    onUpgradeRequired: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }

    val tabs = listOf("All", "Free", "VIP", "Favorites")

    val filteredServers = remember(servers, searchQuery, selectedTab) {
        servers.filter { s ->
            val matchesQuery = s.name.contains(searchQuery, ignoreCase = true) ||
                    s.countryName.contains(searchQuery, ignoreCase = true) ||
                    s.city.contains(searchQuery, ignoreCase = true)

            val matchesTab = when (selectedTab) {
                1 -> !s.isPremiumOnly
                2 -> s.isPremiumOnly
                3 -> s.isFavorite
                else -> true
            }

            matchesQuery && matchesTab
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "VPN Locations",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            IconButton(onClick = onRefreshPings) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Latencies",
                    tint = PrimaryBlue
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search country or city...", color = TextTertiary, fontSize = 14.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = TextTertiary)
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = LightSurface,
                unfocusedContainerColor = LightSurface,
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = LightCardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("server_search_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(PrimaryBlueContainer)
                .border(1.dp, PrimaryBlue.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .clickable { onAutoSelectBest() }
                .padding(14.dp)
                .testTag("auto_select_best_server_card"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PrimaryBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-Select Optimal Server",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Connects to lowest latency & lowest load gateway",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { index, tabTitle ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) PrimaryBlue else LightSurface)
                        .border(
                            1.dp,
                            if (isSelected) PrimaryBlue else LightCardBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedTab = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabTitle,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredServers, key = { it.id }) { server ->
                ServerCard(
                    server = server,
                    isSelected = selectedServer?.id == server.id,
                    onSelect = {
                        if (server.isPremiumOnly && userTier == "FREE") {
                            onUpgradeRequired()
                        } else {
                            onSelectServer(server)
                        }
                    },
                    onToggleFavorite = { onToggleFavorite(server.id) }
                )
            }
        }

        if (userTier == "FREE") {
            Spacer(modifier = Modifier.height(10.dp))
            AdBannerView(onRemoveAdsClicked = onUpgradeRequired)
        }
    }
}
