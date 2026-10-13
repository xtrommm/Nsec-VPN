package com.nsec.vpn

import com.nsec.vpn.vpn.TunnelOperations
import com.nsec.vpn.vpn.TunnelShutdown
import com.nsec.vpn.vpn.RemotePeerCleanup
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TunnelOperationsTest {
    @Test
    fun remotePeerRemovalCannotDelayLocalDisconnectedState() = runTest {
        val release = CompletableDeferred<Unit>()
        var removed = false
        val cleanup = RemotePeerCleanup.enqueue(this) { release.await(); removed = true }
        TunnelShutdown.stop(setDown = {}, isDown = { true })
        assertTrue(cleanup.isActive)
        assertFalse(removed)
        release.complete(Unit)
        cleanup.join()
        assertTrue(removed)
    }
    @Test
    fun stalledRemoteRemovalHasABoundedLifetime() = runTest {
        val cleanup = RemotePeerCleanup.enqueue(this) { awaitCancellation() }
        cleanup.join()
        assertEquals(RemotePeerCleanup.TIMEOUT_MS, testScheduler.currentTime)
    }
    @Test
    fun failedShutdownCannotRunPeerDeletionOrPretendDown() {
        var removed = false
        try {
            TunnelShutdown.stop(setDown = { throw IllegalStateException("backend failed") }, isDown = { false })
            removed = true
            fail("An active tunnel must remain tracked")
        } catch (_: IllegalStateException) { assertFalse(removed) }
        TunnelShutdown.stop(setDown = { throw IllegalStateException("already down") }, isDown = { true })
    }
    @Test
    fun cancellationCleanupFinishesBeforeTheNextActivation() = runTest {
        val operations = TunnelOperations()
        val started = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val original = launch {
            operations.exclusive {
                started.complete(Unit)
                try { awaitCancellation() } finally {
                    withContext(NonCancellable) { delay(100); order.add("cleanup") }
                }
            }
        }
        started.await()
        original.cancel()
        val next = launch { operations.exclusive { order.add("activate") } }
        joinAll(original, next)
        assertEquals(listOf("cleanup", "activate"), order)
    }
    @Test
    fun cancelledQueuedOperationCannotStartATunnel() = runTest {
        val operations = TunnelOperations()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var activated = false
        val first = launch { operations.exclusive { started.complete(Unit); release.await() } }
        started.await()
        val queued = launch { operations.exclusive { activated = true } }
        queued.cancelAndJoin()
        release.complete(Unit); first.join()
        assertFalse(activated)
    }
}
