package com.nsec.vpn

import com.nsec.vpn.data.network.NodeLatencyProbe
import org.junit.Assert.*
import org.junit.Test

class NodeLatencyProbeTest {
    @Test fun acceptsOnlyPublicNodeAddresses() {
        assertTrue(NodeLatencyProbe.validPublicIpv4("47.85.109.170"))
        listOf(null, "10.8.0.1", "172.18.0.1", "192.168.1.1", "127.0.0.1", "203.0.113.1",
            "198.51.100.1", "192.0.2.1", "256.1.1.1", "47.85.109.170;id", "-c 5").forEach {
            assertFalse(NodeLatencyProbe.validPublicIpv4(it))
        }
    }
    @Test fun parsesRealRepliesOnly() {
        assertEquals(43L, NodeLatencyProbe.parseReply("64 bytes from 47.85.109.170: icmp_seq=1 ttl=48 time=42.7 ms"))
        assertEquals(1L, NodeLatencyProbe.parseReply("64 bytes from 47.85.109.170: icmp_seq=1 ttl=48 time<1 ms"))
        assertNull(NodeLatencyProbe.parseReply("100% packet loss"))
        assertNull(NodeLatencyProbe.parseReply("rtt min/avg/max/mdev = 4.0/5.0/6.0/1.0 ms"))
    }
}
