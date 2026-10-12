package com.nsec.vpn.vpn

import com.nsec.vpn.data.model.DnsProvider
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

object DnsPolicy {
    fun parseCustom(value: String): List<String> {
        val values = value.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        require(values.size in 1..4) { "Enter One To Four DNS IP Addresses." }
        return values.map { raw ->
            require(Regex("[0-9a-fA-F:.]+").matches(raw) && (raw.contains(':') || Regex("[0-9]+(\\.[0-9]+){3}").matches(raw))) { "DNS Must Be A Numeric IPv4 Or IPv6 Address, Not A Hostname." }
            if (!raw.contains(':')) require(raw.split('.').all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }) { "Invalid DNS IPv4 Address." }
            val address = try { InetAddress.getByName(raw) } catch (_: java.net.UnknownHostException) { throw IllegalArgumentException("Invalid DNS IP Address.") }
            require(!address.isAnyLocalAddress && !address.isLoopbackAddress && !address.isLinkLocalAddress && !address.isMulticastAddress && !address.isSiteLocalAddress) { "Use A Reachable Public DNS Resolver." }
            if (address is Inet4Address) require(!WireGuardTunnelManager.isPrivateOrDocumentationIp(address.hostAddress.orEmpty())) { "Private Or Documentation DNS Is Not Supported On This Node." }
            if (address is Inet6Address) {
                val bytes = address.address.map { it.toInt() and 255 }
                require(bytes.take(4) != listOf(0x20, 0x01, 0x0d, 0xb8) && (bytes[0] and 0xfe) != 0xfc) { "Use A Reachable Public IPv6 Resolver." }
            }
            address.hostAddress ?: throw IllegalArgumentException("Invalid DNS Address.")
        }
    }
    fun forIpv4Tunnel(provider: DnsProvider, custom: String): List<String> {
        if (provider == DnsProvider.AUTOMATIC) return emptyList()
        val addresses = if (provider == DnsProvider.CUSTOM) parseCustom(custom) else listOf(provider.primary, provider.secondary)
        require(addresses.none { it.contains(':') }) { "This VPN Node Supports IPv4 Only. IPv6 DNS Requires A Real IPv6 Tunnel; Enter An IPv4 Resolver." }
        return addresses
    }
}
