package com.nsec.vpn

import com.nsec.vpn.data.model.DnsProvider
import com.nsec.vpn.data.model.VpnState
import com.nsec.vpn.data.model.VpnStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun vpnStats_durationFormatting_isAccurate() {
        val stats1 = VpnStats(connectionDurationSeconds = 125)
        assertEquals("02:05", stats1.formattedDuration)

        val statsZero = VpnStats(connectionDurationSeconds = 0)
        assertEquals("00:00", statsZero.formattedDuration)

        val statsMultiHour = VpnStats(connectionDurationSeconds = 3661)
        assertEquals("01:01:01", statsMultiHour.formattedDuration)
    }

    @Test
    fun vpnState_stateMachineValidation() {
        val states: List<VpnState> = listOf(
            VpnState.Disconnected,
            VpnState.Connecting,
            VpnState.Connected,
            VpnState.Disconnecting,
            VpnState.Error("Handshake timed out")
        )

        assertEquals(5, states.size)
        assertTrue(states[0] is VpnState.Disconnected)
        assertTrue(states[1] is VpnState.Connecting)
        assertTrue(states[2] is VpnState.Connected)
        assertTrue(states[3] is VpnState.Disconnecting)
        assertTrue(states[4] is VpnState.Error)
        assertEquals("Handshake timed out", (states[4] as VpnState.Error).message)
    }

    @Test
    fun dnsProvider_returnsValidIpAddresses() {
        val cloudflare = DnsProvider.CLOUDFLARE
        assertEquals("1.1.1.1", cloudflare.primary)
        assertEquals("1.0.0.1", cloudflare.secondary)

        val google = DnsProvider.GOOGLE
        assertEquals("8.8.8.8", google.primary)
        assertEquals("8.8.4.4", google.secondary)
    }
}
