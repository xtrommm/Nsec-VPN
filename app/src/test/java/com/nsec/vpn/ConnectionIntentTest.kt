package com.nsec.vpn

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ConnectionIntentTest {
    @Test
    fun resumeIntentIsEncryptedAndManualDisconnectRemovesIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = TestVault.create(context)
        val record = "{\"nodeId\":\"real-node-id-from-api\",\"countryCode\":\"US\",\"city\":\"Virginia\"}"
        vault.saveConnectionIntent(record)
        val stored = context.getSharedPreferences("nsecvpn_secure_vault", Context.MODE_PRIVATE)
            .getString("connection_intent_v1", null)!!
        assertTrue(stored.startsWith("v2:"))
        assertFalse(stored.contains("real-node-id-from-api"))
        assertEquals(record, vault.getConnectionIntent())
        assertEquals(record, TestVault.create(context).getConnectionIntent())
        vault.saveConnectionIntent(null)
        assertNull(TestVault.create(context).getConnectionIntent())
    }
}
