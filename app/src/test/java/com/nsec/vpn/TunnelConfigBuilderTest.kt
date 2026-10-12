package com.nsec.vpn
import com.nsec.vpn.data.model.TunnelConfig
import com.nsec.vpn.vpn.TunnelConfigBuilder
import com.wireguard.crypto.KeyPair
import org.junit.Assert.*
import org.junit.Test
class TunnelConfigBuilderTest {
    private fun config() = TunnelConfig(KeyPair().publicKey.toBase64(), "47.85.109.170:51820", "10.8.0.2/32",
        nodeId="test-node", nodeName="Test", locationName="Test", publicIpv4="47.85.109.170")
    @Test fun realBuilderPreservesClientKeyAndExcludesOwnAppWithoutIncludeMode() {
        val client = KeyPair()
        val result = TunnelConfigBuilder.build(config(),client.privateKey.toBase64(),"com.nsec.vpn",emptySet())
        assertEquals(client.publicKey,result.`interface`.keyPair.publicKey)
        assertTrue(result.`interface`.excludedApplications.contains("com.nsec.vpn"))
        assertTrue(result.`interface`.includedApplications.isEmpty())
        assertEquals(setOf("10.8.0.2/32"),result.`interface`.addresses.map {it.toString()}.toSet())
        assertEquals(setOf("0.0.0.0/0"),result.peers.single().allowedIps.map {it.toString()}.toSet())
        assertEquals("47.85.109.170:51820",result.peers.single().endpoint.get().toString())
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsAgentPort() {
        TunnelConfigBuilder.build(config().copy(serverEndpoint="47.85.109.170:51821"),KeyPair().privateKey.toBase64(),"com.nsec.vpn",emptySet())
    }
    @Test fun hostnameConfigurationPinsTheConfirmedNodeIpv4() {
        val result = TunnelConfigBuilder.build(config().copy(serverEndpoint="node.nsecvpn.com:51820"),
            KeyPair().privateKey.toBase64(),"com.nsec.vpn",emptySet())
        assertEquals("47.85.109.170:51820",result.peers.single().endpoint.get().toString())
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsUnverifiedPrivateDns() {
        TunnelConfigBuilder.build(config(),KeyPair().privateKey.toBase64(),"com.nsec.vpn",emptySet(),listOf("10.8.0.1"))
    }
}
