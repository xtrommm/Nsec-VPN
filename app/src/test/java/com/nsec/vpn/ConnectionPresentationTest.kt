package com.nsec.vpn

import com.nsec.vpn.data.model.VpnState
import com.nsec.vpn.ui.components.ConnectionPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionPresentationTest {
    @Test
    fun disconnectingNeverClaimsConnectingOrConfirmedDown() {
        assertEquals("Disconnecting", ConnectionPresentation.status(VpnState.Disconnecting))
        assertEquals("VPN DISCONNECTING", ConnectionPresentation.homeTitle(VpnState.Disconnecting))
        assertFalse(ConnectionPresentation.homeDetail(VpnState.Disconnecting, "").contains("UNENCRYPTED"))
        assertFalse(ConnectionPresentation.canToggle(VpnState.Disconnecting))
        assertTrue(ConnectionPresentation.canToggle(VpnState.Disconnected))
        assertEquals("Disconnected", ConnectionPresentation.status(VpnState.Disconnected))
    }
}
