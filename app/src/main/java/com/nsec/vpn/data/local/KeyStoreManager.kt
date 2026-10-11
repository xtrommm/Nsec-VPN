package com.nsec.vpn.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.wireguard.crypto.KeyPair
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class KeyStoreManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("nsecvpn_secure_vault", Context.MODE_PRIVATE)

    // Master key derived per device for light vault encryption
    private val secretKey: SecretKeySpec by lazy {
        var keyB64 = prefs.getString(KEY_VAULT_SEED, null)
        if (keyB64 == null) {
            val randomBytes = ByteArray(16)
            SecureRandom().nextBytes(randomBytes)
            keyB64 = Base64.encodeToString(randomBytes, Base64.NO_WRAP)
            prefs.edit().putString(KEY_VAULT_SEED, keyB64).apply()
        }
        val keyBytes = Base64.decode(keyB64, Base64.NO_WRAP)
        SecretKeySpec(keyBytes, "AES")
    }

    private fun encryptString(plainText: String?): String? {
        if (plainText.isNullOrEmpty()) return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            plainText
        }
    }

    private fun decryptString(cipherTextB64: String?): String? {
        if (cipherTextB64.isNullOrEmpty()) return null
        return try {
            val combined = Base64.decode(cipherTextB64, Base64.NO_WRAP)
            if (combined.size <= 12) return cipherTextB64
            val iv = ByteArray(12)
            val encryptedBytes = ByteArray(combined.size - 12)
            System.arraycopy(combined, 0, iv, 0, 12)
            System.arraycopy(combined, 12, encryptedBytes, 0, encryptedBytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        } catch (_: Exception) {
            cipherTextB64
        }
    }

    fun getOrCreateDeviceId(): String {
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    fun generateEphemeralWireGuardKeyPair(): KeyPair {
        val keyPair = KeyPair()
        val encPrivate = encryptString(keyPair.privateKey.toBase64())
        val encPublic = encryptString(keyPair.publicKey.toBase64())
        prefs.edit()
            .putString(KEY_WG_PRIVATE, encPrivate)
            .putString(KEY_WG_PUBLIC, encPublic)
            .apply()
        return keyPair
    }

    fun getStoredPrivateKeyBase64(): String? = decryptString(prefs.getString(KEY_WG_PRIVATE, null))
    fun getStoredPublicKeyBase64(): String? = decryptString(prefs.getString(KEY_WG_PUBLIC, null))

    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, encryptString(accessToken))
            .putString(KEY_REFRESH_TOKEN, encryptString(refreshToken))
            .apply()
    }

    fun getAccessToken(): String? = decryptString(prefs.getString(KEY_ACCESS_TOKEN, null))
    fun getRefreshToken(): String? = decryptString(prefs.getString(KEY_REFRESH_TOKEN, null))

    fun clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }

    // Settings Persistence
    fun saveSelectedServerId(serverId: String) {
        prefs.edit().putString(KEY_SELECTED_SERVER_ID, serverId).apply()
    }
    fun getSelectedServerId(): String? = prefs.getString(KEY_SELECTED_SERVER_ID, null)

    fun saveSelectedDns(dnsName: String) {
        prefs.edit().putString(KEY_SELECTED_DNS, dnsName).apply()
    }
    fun getSelectedDns(): String? = prefs.getString(KEY_SELECTED_DNS, null)

    fun saveKillSwitchEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KILL_SWITCH, enabled).apply()
    }
    fun isKillSwitchEnabled(): Boolean = prefs.getBoolean(KEY_KILL_SWITCH, false)

    fun saveSplitTunnelingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPLIT_TUNNELING, enabled).apply()
    }
    fun isSplitTunnelingEnabled(): Boolean = prefs.getBoolean(KEY_SPLIT_TUNNELING, false)

    fun saveExcludedPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_EXCLUDED_PACKAGES, packages).apply()
    }
    fun getExcludedPackages(): Set<String> = prefs.getStringSet(KEY_EXCLUDED_PACKAGES, emptySet()) ?: emptySet()

    fun saveAutoConnectEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CONNECT, enabled).apply()
    }
    fun isAutoConnectEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_CONNECT, false)

    // VPN Prominent Disclosure Acceptance
    fun saveVpnDisclosureAccepted(accepted: Boolean) {
        prefs.edit().putBoolean(KEY_VPN_DISCLOSURE_ACCEPTED, accepted).apply()
    }
    fun isVpnDisclosureAccepted(): Boolean = prefs.getBoolean(KEY_VPN_DISCLOSURE_ACCEPTED, false)

    // Rewarded Pass Expiry
    fun saveRewardedPassExpiry(expiresAtMs: Long) {
        prefs.edit().putLong(KEY_REWARDED_EXPIRES_AT, expiresAtMs).apply()
    }
    fun getRewardedPassExpiry(): Long = prefs.getLong(KEY_REWARDED_EXPIRES_AT, 0L)

    // Favorite Servers Persistence
    fun saveFavoriteServerIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_FAVORITE_SERVERS, ids).apply()
    }
    fun getFavoriteServerIds(): Set<String> = prefs.getStringSet(KEY_FAVORITE_SERVERS, emptySet()) ?: emptySet()

    // Theme Preference Persistence
    fun saveThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
    }
    fun getThemeMode(): String = prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"

    companion object {
        private const val KEY_VAULT_SEED = "nsec_vault_seed_v1"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_WG_PRIVATE = "wg_private_key"
        private const val KEY_WG_PUBLIC = "wg_public_key"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_SELECTED_SERVER_ID = "selected_server_id"
        private const val KEY_SELECTED_DNS = "selected_dns"
        private const val KEY_KILL_SWITCH = "kill_switch_enabled"
        private const val KEY_SPLIT_TUNNELING = "split_tunneling_enabled"
        private const val KEY_EXCLUDED_PACKAGES = "excluded_packages"
        private const val KEY_AUTO_CONNECT = "auto_connect_enabled"
        private const val KEY_VPN_DISCLOSURE_ACCEPTED = "vpn_disclosure_accepted"
        private const val KEY_REWARDED_EXPIRES_AT = "rewarded_expires_at"
        private const val KEY_FAVORITE_SERVERS = "favorite_server_ids"
        private const val KEY_THEME_MODE = "app_theme_mode"
    }
}
