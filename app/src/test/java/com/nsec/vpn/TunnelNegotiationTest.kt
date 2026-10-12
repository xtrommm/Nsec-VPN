package com.nsec.vpn

import com.nsec.vpn.vpn.TunnelNegotiation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException

class TunnelNegotiationTest {
    @Test fun peerApiFailureNeverStartsTheTunnel() = runTest {
        var starts = 0
        try {
            TunnelNegotiation.establish<String>(
                prepare = { throw IllegalStateException("AGENT_UNREACHABLE") },
                activate = { starts++ }, handshake = { true })
            fail("Failed peer registration must propagate")
        } catch (_: IllegalStateException) { assertEquals(0, starts) }
    }
    @Test fun connectionOnlyCompletesAfterRealHandshakeConfirmation() = runTest {
        val order = mutableListOf<String>()
        val result = TunnelNegotiation.establish(
            prepare = { order.add("registered"); "test-config" },
            activate = { order.add("activated") },
            handshake = { order.add("handshake"); true })
        assertEquals("test-config", result)
        assertEquals(listOf("registered", "activated", "handshake"), order)
    }
    @Test fun handshakeTimeoutCannotReturnAConnectedConfiguration() = runTest {
        try {
            TunnelNegotiation.establish(prepare = { "test-config" }, activate = {}, handshake = { false })
            fail("No handshake must be a timeout")
        } catch (error: SocketTimeoutException) { assertEquals("Couldn't reach the server", error.message) }
    }
}
