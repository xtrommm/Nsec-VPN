package com.nsec.vpn
import com.nsec.vpn.vpn.HandshakeGate
import org.junit.Assert.*
import org.junit.Test
class HandshakeGateTest {
    @Test fun neverConnectedWithoutAHandshake() { assertFalse(HandshakeGate.confirmed(0, 15000)) }
    @Test fun acceptsRecentRealHandshake() { assertTrue(HandshakeGate.confirmed(10000, 10500)) }
    @Test fun rejectsOldOrFutureHandshake() {
        assertFalse(HandshakeGate.confirmed(10000, 30001))
        assertFalse(HandshakeGate.confirmed(30000, 10000))
    }
}
