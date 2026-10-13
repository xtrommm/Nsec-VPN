package com.nsec.vpn.vpn

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Cancellation joins happen before entry; activation and cleanup cannot run concurrently. */
class TunnelOperations {
    private val mutex = Mutex()
    suspend fun <T> exclusive(work: suspend () -> T): T = mutex.withLock { work() }
}

object TunnelShutdown {
    fun stop(setDown: () -> Unit, isDown: () -> Boolean) {
        try { setDown() } catch (failure: Exception) {
            if (!isDown()) throw IllegalStateException("Couldn't Stop The VPN. Please Retry Disconnecting.", failure)
        }
        check(isDown()) { "Couldn't Stop The VPN. Please Retry Disconnecting." }
    }
}
