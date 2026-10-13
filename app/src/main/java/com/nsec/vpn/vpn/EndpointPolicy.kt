package com.nsec.vpn.vpn

import java.net.Inet4Address
import java.net.InetAddress

object EndpointPolicy {
    fun validate(addresses: List<InetAddress>, publicIpv4: String) {
        val ipv4 = addresses.filterIsInstance<Inet4Address>()
        require(ipv4.isNotEmpty() && ipv4.all { !WireGuardTunnelManager.isPrivateOrDocumentationIp(it.hostAddress.orEmpty()) }) {
            "Server Endpoint Must Resolve To Public IPv4"
        }
        require(ipv4.any { it.hostAddress == publicIpv4 }) { "Node Public IP Does Not Match Endpoint" }
    }
}
