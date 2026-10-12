package com.nsec.vpn.vpn

import java.net.SocketTimeoutException

/** Shared ordering contract: successful peer registration precedes tunnel activation. */
object TunnelNegotiation {
    suspend fun <T> establish(
        prepare: suspend () -> T,
        activate: suspend (T) -> Unit,
        handshake: suspend (T) -> Boolean
    ): T {
        val config = prepare()
        activate(config)
        if (!handshake(config)) throw SocketTimeoutException("Couldn't Reach The Server")
        return config
    }
}
