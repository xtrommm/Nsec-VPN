package com.nsec.vpn
import com.nsec.vpn.data.network.TroubleshootingEvidence
import org.junit.Assert.*
import org.junit.Test
class TroubleshootingEvidenceTest {
    @Test fun absentAndStaleHandshakeNeverPassUdpCheck() {
        assertNull(TroubleshootingEvidence.handshake(false, 0, 180))
        assertEquals(false, TroubleshootingEvidence.handshake(true, Long.MAX_VALUE, 180))
        assertEquals(false, TroubleshootingEvidence.handshake(true, -1, 180))
        assertEquals(false, TroubleshootingEvidence.handshake(true, 181, 180))
        assertEquals(true, TroubleshootingEvidence.handshake(true, 10, 180))
    }
    @Test fun zeroTrafficRemainsUnmeasured() {
        assertNull(TroubleshootingEvidence.traffic(true, 0, 0))
        assertNull(TroubleshootingEvidence.traffic(false, 100, 100))
        assertNull(TroubleshootingEvidence.traffic(true, 100, 0))
        assertEquals(true, TroubleshootingEvidence.traffic(true, 100, 200))
    }
}
