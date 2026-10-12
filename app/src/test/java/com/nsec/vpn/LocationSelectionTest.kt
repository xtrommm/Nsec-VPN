package com.nsec.vpn

import com.nsec.vpn.data.model.LocationSelection
import com.nsec.vpn.data.model.Server
import org.junit.Assert.*
import org.junit.Test

class LocationSelectionTest {
    private val node = Server("node-0", "Node", "SG", "Singapore", "Singapore", "SG", 0, false, pingMs = 20)
    @Test fun tenNodesBecomeOneLocationWithoutInflatingTheCount() {
        val nodes = (0..9).map { node.copy(id = "node-$it", totalNodes = 10) }
        val locations = LocationSelection.group(nodes, "FREE")
        assertEquals(1, locations.size)
        assertEquals(10, locations.single().totalNodes)
    }
    @Test fun bestBalancesLatencyAndLoadAndAvoidsFullNodes() {
        val nodes = listOf(node.copy(currentLoad = 90), node.copy(id = "fast", currentLoad = 10, pingMs = 30),
            node.copy(id = "full", currentLoad = 100, pingMs = 1))
        assertEquals("fast", LocationSelection.ranked(nodes, "FREE").first().id)
    }
    @Test fun freeDevicesNeverSelectPremiumAndMixedTierLocationsStaySingle() {
        val nodes = listOf(node.copy(currentLoad = 20), node.copy(id = "vip", isPremiumOnly = true, pingMs = 1))
        assertEquals("node-0", LocationSelection.group(nodes, "FREE").single().id)
        assertEquals("vip", LocationSelection.group(nodes, "VIP").single().id)
    }
    @Test fun locationCaseAndFavoritesRemainStableAcrossRepresentativeChanges() {
        val other = node.copy(id = "other", countryCode = "sg", city = "singapore", isFavorite = true)
        assertTrue(LocationSelection.sameLocation(node, other))
        assertTrue(LocationSelection.group(listOf(node, other), "FREE").single().isFavorite)
    }
}
