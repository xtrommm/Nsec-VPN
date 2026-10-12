package com.nsec.vpn

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecoveredDeviceSessionTest {
    @Test fun recoveredIdentityAndCredentialsStayConsistentWithoutImportingATunnelPrivateKey() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vault = TestVault.create(context)
        val publicKey = vault.getOrCreateWireGuardKeyPair().publicKey.toBase64()
        val originalId = java.util.UUID.randomUUID().toString()
        vault.restoreDeviceSession(originalId, "c".repeat(64), "fixture-access", "fixture-refresh")
        assertEquals(originalId, vault.getOrCreateDeviceId())
        assertEquals("c".repeat(64), vault.getOrCreateRegistrationSecret())
        assertEquals("fixture-access", vault.getAccessToken())
        assertEquals("fixture-refresh", vault.getRefreshToken())
        assertEquals(publicKey, vault.getOrCreateWireGuardKeyPair().publicKey.toBase64())
    }
    @Test fun invalidRecoveredCredentialDoesNotOverwriteTheCurrentSession() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vault = TestVault.create(context)
        val currentId = vault.getOrCreateDeviceId()
        vault.saveTokens("fixture-before", "fixture-before-refresh")
        try {
            vault.restoreDeviceSession("restored-id", "invalid-fixture", "fixture-after", "fixture-after-refresh")
            fail("Invalid proof must be rejected before writing")
        } catch (_: IllegalArgumentException) {
            assertEquals(currentId, vault.getOrCreateDeviceId())
            assertEquals("fixture-before", vault.getAccessToken())
        }
    }
}
