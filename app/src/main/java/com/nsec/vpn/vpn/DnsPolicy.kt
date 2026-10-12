package com.nsec.vpn.vpn

import com.nsec.vpn.data.model.DnsProvider
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

object DnsPolicy {
    fun parseCustom(value: String): List<String> {
        val values = value.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        require(values.size in 1..4) { "Enter one to four DNS IP addresses." }
        return values.map { raw ->
            require(Regex("[0-9a-fA-F:.]+").matches(raw) && (raw.contains(':') || Regex("[0-9]+(\\.[0-9]+){3}").matches(raw))) { "DNS must be a numeric IPv4 or IPv6 address, not a hostname." }
            if (!raw.contains(':')) require(raw.split('.').all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }) { "Invalid DNS IPv4 address." }
            val address = try { InetAddress.getByName(raw) } catch (_: java.net.UnknownHostException) { throw IllegalArgumentException("Invalid DNS IP address.") }
            require(!address.isAnyLocalAddress && !address.isLoopbackAddress && !address.isLinkLocalAddress && !address.isMulticastAddress && !address.isSiteLocalAddress) { "Use a reachable public DNS resolver." }
            if (address is Inet4Address) require(!WireGuardTunnelManager.isPrivateOrDocumentationIp(address.hostAddress.orEmpty())) { "Private or documentation DNS is not supported on this node." }
            if (address is Inet6Address) {
                val bytes = address.address.map { it.toInt() and 255 }
                require(bytes.take(4) != listOf(0x20, 0x01, 0x0d, 0xb8) && (bytes[0] and 0xfe) != 0xfc) { "Use a reachable public IPv6 resolver." }
            }
            address.hostAddress ?: throw IllegalArgumentException("Invalid DNS address.")
        }
    }
    fun forIpv4Tunnel(provider: DnsProvider, custom: String): List<String> {
        if (provider == DnsProvider.AUTOMATIC) return emptyList()
        val addresses = if (provider == DnsProvider.CUSTOM) parseCustom(custom) else listOf(provider.primary, provider.secondary)
        require(addresses.none { it.contains(':') }) { "This VPN node supports IPv4 only. IPv6 DNS requires a real IPv6 tunnel; enter an IPv4 resolver." }
        return addresses
    }
}
