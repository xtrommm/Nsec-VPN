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
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nsec.vpn.data.model.Server
import com.nsec.vpn.data.model.VpnState
import com.nsec.vpn.data.monetization.AdMobConfig
import com.nsec.vpn.data.monetization.NativeAdManager
import com.nsec.vpn.ui.components.ServerCard
import com.nsec.vpn.ui.components.SmartNativeAd
import com.nsec.vpn.ui.theme.*

sealed class ServerListItem {
    data class ServerItem(val server: Server) : ServerListItem()
    data class AdSlot(val id: String) : ServerListItem()
}

@Composable
fun ServerListScreen(
    servers: List<Server>,
    selectedServer: Server?,
    userTier: String,
    nativeAdManager: NativeAdManager,
    onSelectServer: (Server) -> Unit,
    onAutoSelectBest: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRefreshPings: () -> Unit,
    onUpgradeRequired: () -> Unit,
    modifier: Modifier = Modifier,
    adMobConfig: AdMobConfig = AdMobConfig(),
    isLoading: Boolean = false,
    usingCache: Boolean = false,
    errorMessage: String? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }

    val favCount = remember(servers) { servers.count { it.isFavorite } }
    val tabs = remember(favCount) {
        listOf("All", "Free", "VIP", if (favCount > 0) "Favorites ($favCount)" else "Favorites")
    }

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

    val listItems = remember(filteredServers, userTier, adMobConfig.nativeEnabled) {
        val items = mutableListOf<ServerListItem>()
        var adCounter = 0
        filteredServers.forEachIndexed { index, server ->
            items.add(ServerListItem.ServerItem(server))
            if (userTier == "FREE" && adMobConfig.nativeEnabled && (index + 1) % 8 == 0 && adCounter < 3) {
                items.add(ServerListItem.AdSlot("ad_slot_$adCounter"))
                adCounter++
            }
        }
        items
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(
                onClick = onRefreshPings,
                modifier = Modifier.testTag("refresh_servers_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Servers & Latencies",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (usingCache) Text("Saved server list · refreshing availability", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        errorMessage?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
        if (isLoading && servers.isEmpty()) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) { Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) }
            Text("Loading servers…", fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search country or city...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
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
                .background(MaterialTheme.colorScheme.primaryContainer)
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
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-Select Optimal Server",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Connects to lowest latency & lowest load gateway",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEachIndexed { index, tabTitle ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedTab = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabTitle,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedTab == 3 && filteredServers.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                        .padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.StarBorder,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Favorite Servers Yet",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap the star icon on any server location to save it to your favorites list for quick access.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { selectedTab = 0 },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Explore All Locations", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(
                    items = listItems,
                    key = { item ->
                        when (item) {
                            is ServerListItem.ServerItem -> item.server.id
                            is ServerListItem.AdSlot -> item.id
                        }
                    }
                ) { item ->
                    when (item) {
                        is ServerListItem.ServerItem -> {
                            val server = item.server
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
                        is ServerListItem.AdSlot -> {
                            SmartNativeAd(
                                nativeAdManager = nativeAdManager,
                                adMobConfig = adMobConfig,
                                userTier = userTier,
                                vpnState = VpnState.Disconnected,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
