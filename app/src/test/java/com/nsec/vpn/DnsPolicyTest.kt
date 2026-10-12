package com.nsec.vpn
import com.nsec.vpn.data.model.DnsProvider
import com.nsec.vpn.vpn.DnsPolicy
import org.junit.Assert.*
import org.junit.Test
class DnsPolicyTest {
    @Test fun automaticUsesNodeConfigurationAndPresetsUseActualAddresses() {
        assertTrue(DnsPolicy.forIpv4Tunnel(DnsProvider.AUTOMATIC, "").isEmpty())
        assertEquals(listOf("9.9.9.9", "149.112.112.112"), DnsPolicy.forIpv4Tunnel(DnsProvider.QUAD9, ""))
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), DnsPolicy.parseCustom("1.1.1.1, 8.8.8.8, 1.1.1.1"))
    }
    @Test fun validatesIpv6WithoutPretendingAnIpv4NodeCanRouteIt() {
        assertTrue(DnsPolicy.parseCustom("2606:4700:4700::1111").single().contains(':'))
        assertThrows(IllegalArgumentException::class.java) { DnsPolicy.forIpv4Tunnel(DnsProvider.CUSTOM, "2606:4700:4700::1111") }
    }
    @Test fun rejectsNamesMalformedAddressesAndUnroutableResolvers() {
        listOf("example.com", "999.1.1.1", "127.0.0.1", "10.8.0.1", "192.0.2.1", "::1", "2001:db8::1", "2001:0db8::1", "::ffff:192.0.2.1", "::::", "").forEach { bad ->
            assertThrows(Exception::class.java) { DnsPolicy.parseCustom(bad) }
        }
    }
}
