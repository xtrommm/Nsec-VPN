package com.nsec.vpn.data.local

import android.content.Context
import android.content.SharedPreferences
import com.wireguard.crypto.KeyPair
import java.util.UUID

class KeyStoreManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("nsecvpn_secure_vault", Context.MODE_PRIVATE)

    fun getOrCreateDeviceId(): String {
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    fun getOrCreateClientKeyPair(): KeyPair {
        val privateKeyHex = prefs.getString(KEY_CLIENT_PRIVATE, null)
        return if (privateKeyHex != null) {
            KeyPair(com.wireguard.crypto.Key.fromHex(privateKeyHex))
        } else {
            val keyPair = KeyPair()
            prefs.edit()
                .putString(KEY_CLIENT_PRIVATE, keyPair.privateKey.toHex())
                .putString(KEY_CLIENT_PUBLIC, keyPair.publicKey.toHex())
                .apply()
            keyPair
        }
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_CLIENT_PRIVATE = "client_priv_key"
        private const val KEY_CLIENT_PUBLIC = "client_pub_key"
        private const val KEY_ACCESS_TOKEN = "jwt_access"
        private const val KEY_REFRESH_TOKEN = "jwt_refresh"
    }
}
