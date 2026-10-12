package com.nsec.vpn.data.network

/** No active tunnel means unmeasured, never an inferred successful network test. */
object TroubleshootingEvidence {
    fun handshake(connected: Boolean, ageSeconds: Long, maximumAge: Long): Boolean? =
        if (!connected) null else ageSeconds in 0..maximumAge
    fun traffic(connected: Boolean, received: Long, sent: Long): Boolean? =
        if (!connected || received <= 0 || sent <= 0) null else true
}
