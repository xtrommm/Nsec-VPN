package com.nsec.vpn

import com.nsec.vpn.data.billing.OwnedReceipt
import com.nsec.vpn.data.billing.restoreOwnedReceipts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PurchaseRestoreTest {
    @Test
    fun restoreRequiresBackendVerificationAndDeduplicatesOwnedReceipts() = runTest {
        val receipt = OwnedReceipt("test-product", "test-receipt")
        val verified = mutableListOf<OwnedReceipt>()
        restoreOwnedReceipts(listOf(receipt, receipt)) { verified.add(it) }
        assertEquals(listOf(receipt), verified)
    }
    @Test
    fun missingReceiptsAndVerificationFailureCannotReportSuccess() = runTest {
        try { restoreOwnedReceipts(emptyList()) {}; fail("Missing purchase must fail") }
        catch (_: IllegalStateException) {}
        try { restoreOwnedReceipts(listOf(OwnedReceipt("test-product", "test-receipt"))) { error("verification failed") }; fail("Unverified purchase must fail") }
        catch (error: IllegalStateException) { assertEquals("verification failed", error.message) }
    }
    @Test
    fun restorePreservesCancellation() = runTest {
        try { restoreOwnedReceipts(listOf(OwnedReceipt("test-product", "test-receipt"))) { throw CancellationException() }; fail("Cancellation must propagate") }
        catch (_: CancellationException) {}
    }
}
