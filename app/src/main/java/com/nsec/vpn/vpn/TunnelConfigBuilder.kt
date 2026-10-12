package com.nsec.vpn.vpn

import com.nsec.vpn.data.model.TunnelConfig
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.config.InetEndpoint
import com.wireguard.config.InetNetwork
import java.net.InetAddress

object TunnelConfigBuilder {
    fun build(tc: TunnelConfig, privateKey: String, ownPackage: String, excludedApps: Set<String>, dnsOverride: List<String> = emptyList()): Config {
        require(WireGuardTunnelManager.validateTunnelConfig(tc).isValid) { "Invalid server configuration" }
        require(ownPackage.isNotBlank())
        val dns = dnsOverride.ifEmpty { tc.dnsServers }
        val iface = Interface.Builder().parsePrivateKey(privateKey).addAddress(InetNetwork.parse(tc.clientAddress))
            .setMtu(tc.mtu).excludeApplications(excludedApps + ownPackage)
        dns.forEach {
            require(it.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) && !WireGuardTunnelManager.isPrivateOrDocumentationIp(it)) { "Invalid DNS address" }
            InetNetwork.parse("$it/32")
            iface.addDnsServer(InetAddress.getByName(it))
        }
        // Use the agent-confirmed IPv4 rather than resolving a multi-address hostname again.
        val endpoint = InetEndpoint.parse(tc.serverEndpoint)
        val peer = Peer.Builder().parsePublicKey(tc.serverPublicKey).setEndpoint(InetEndpoint.parse("${tc.publicIpv4}:${endpoint.port}"))
            .setPersistentKeepalive(tc.persistentKeepalive)
        tc.allowedIps.forEach { peer.addAllowedIp(InetNetwork.parse(it)) }
        return Config.Builder().setInterface(iface.build()).addPeer(peer.build()).build()
    }
}
