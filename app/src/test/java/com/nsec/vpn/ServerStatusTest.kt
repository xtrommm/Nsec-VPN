package com.nsec.vpn

import com.nsec.vpn.data.model.Server
import com.nsec.vpn.data.model.ServerStatus
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ServerStatusTest {
    private fun node(id: String, load: Int, stamp: String? = null) = Server(id, id, "DE", "Germany", "Frankfurt", "🇩🇪", load, false, loadUpdatedAt = stamp)
    @Test fun connectedNodeUsesFreshLoadRatherThanTunnelSnapshot() {
        val stale = node("node-a", 1)
        val fresh = node("node-a", 70)
        assertEquals(fresh, ServerStatus.current(listOf(fresh), "node-a", stale))
    }
    @Test fun failoverUsesActualConnectedId() {
        val a = node("node-a", 10); val b = node("node-b", 80)
        assertEquals(b, ServerStatus.current(listOf(a, b), "node-b", a))
        assertNull(ServerStatus.current(listOf(a), "node-b", a))
    }
    @Test fun missingAndStaleLoadAreUnavailable() {
        val now = 1_800_000_000_000L
        assertEquals("42%", ServerStatus.loadText(node("node-a", 42, Instant.ofEpochMilli(now).toString()), now))
        assertEquals("Unavailable", ServerStatus.loadText(node("node-a", 42), now))
        assertEquals("Unavailable", ServerStatus.loadText(node("node-a", 42, Instant.ofEpochMilli(now - 90001).toString()), now))
        assertEquals("Unavailable", ServerStatus.loadText(node("node-a", 42, "invalid-test-time"), now))
    }
}
