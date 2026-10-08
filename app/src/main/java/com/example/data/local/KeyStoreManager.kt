package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.wireguard.crypto.KeyPair
import java.util.UUID

class KeyStoreManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("auravpn_secure_vault", Context.MODE_PRIVATE)

    /**
     * Retrieves or generates a persistent device UUID.
     */
    fun getOrCreateDeviceId(): String {
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    /**
     * Generates a fresh ephemeral Curve25519 WireGuard keypair ON DEVICE.
     * The private key is held strictly in memory or local secure storage, NEVER sent over network.
     */
    fun generateEphemeralWireGuardKeyPair(): KeyPair {
        val keyPair = KeyPair()
        prefs.edit()
            .putString(KEY_WG_PRIVATE, keyPair.privateKey.toBase64())
            .putString(KEY_WG_PUBLIC, keyPair.publicKey.toBase64())
            .apply()
        return keyPair
    }

    fun getStoredPrivateKeyBase64(): String? {
        return prefs.getString(KEY_WG_PRIVATE, null)
    }

    fun getStoredPublicKeyBase64(): String? {
        return prefs.getString(KEY_WG_PUBLIC, null)
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_WG_PRIVATE = "wg_private_key"
        private const val KEY_WG_PUBLIC = "wg_public_key"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
