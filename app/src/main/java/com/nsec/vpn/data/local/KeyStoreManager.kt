package com.nsec.vpn.data.local

import android.annotation.SuppressLint
import androidx.core.content.edit
import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.wireguard.crypto.KeyPair
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class KeyStoreManager(context: Context, private val vaultKeyProvider: (() -> javax.crypto.SecretKey)? = null) {
    private val prefs: SharedPreferences = context.getSharedPreferences("nsecvpn_secure_vault", Context.MODE_PRIVATE)

    private val secretKey: javax.crypto.SecretKey by lazy { synchronized(vaultLock) {
        if (vaultKeyProvider != null) return@synchronized vaultKeyProvider.invoke()
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("nsec_vault_v2", null) as? javax.crypto.SecretKey) ?: run {
            val generator = javax.crypto.KeyGenerator.getInstance("AES", "AndroidKeyStore")
            generator.init(android.security.keystore.KeyGenParameterSpec.Builder("nsec_vault_v2",
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generator.generateKey()
        }
    } }
    // KTX edit returns Unit and discards commit failures; credentials require the Boolean result.
    @SuppressLint("UseKtx")
    private fun editVerified(block: SharedPreferences.Editor.() -> Unit) {
        val editor = prefs.edit()
        editor.block()
        require(editor.commit()) { "Credential storage failed" }
    }

    private fun encryptString(plainText: String?): String? {
        if (plainText.isNullOrEmpty()) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return "v2:" + Base64.encodeToString(cipher.iv + cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    private fun decryptString(stored: String?): String? {
        if (stored.isNullOrEmpty()) return null
        return try {
            val modern = stored.startsWith("v2:")
            val bytes = Base64.decode(if (modern) stored.substring(3) else stored, Base64.NO_WRAP)
            require(bytes.size > 12)
            val key = if (modern) secretKey else {
                val seed = prefs.getString(KEY_VAULT_SEED, null) ?: return null
                SecretKeySpec(Base64.decode(seed, Base64.NO_WRAP), "AES")
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }

    fun getOrCreateRegistrationSecret(): String = synchronized(vaultLock) {
        val stored = decryptString(prefs.getString("registration_secret_v2", null))
        require(!prefs.contains("registration_secret_v2") || stored != null) { "Stored device credential could not be decrypted" }
        if (stored != null) return@synchronized stored
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val secret = bytes.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        editVerified { putString("registration_secret_v2", encryptString(secret)) }
        secret
    }

    fun getOrCreateDeviceId(): String = synchronized(vaultLock) {
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also { id ->
            editVerified { putString(KEY_DEVICE_ID, id) }
        }
    }

    fun getOrCreateWireGuardKeyPair(): KeyPair = synchronized(vaultLock) {
        val existing = getStoredPrivateKeyBase64()
        require(!prefs.contains(KEY_WG_PRIVATE) || existing != null) { "Stored VPN key could not be decrypted" }
        val pair = if (existing == null) KeyPair() else KeyPair(com.wireguard.crypto.Key.fromBase64(existing))
        editVerified {
            putString(KEY_WG_PRIVATE, encryptString(pair.privateKey.toBase64()))
            putString(KEY_WG_PUBLIC, encryptString(pair.publicKey.toBase64()))
        }
        pair
    }

    fun getStoredPrivateKeyBase64(): String? = decryptString(prefs.getString(KEY_WG_PRIVATE, null))
    fun getStoredPublicKeyBase64(): String? = decryptString(prefs.getString(KEY_WG_PUBLIC, null))

    fun saveTokens(accessToken: String, refreshToken: String) = synchronized(vaultLock) {
        editVerified {
            putString(KEY_ACCESS_TOKEN, encryptString(accessToken))
            putString(KEY_REFRESH_TOKEN, encryptString(refreshToken))
        }
    }

    fun getAccessToken(): String? = decryptString(prefs.getString(KEY_ACCESS_TOKEN, null))
    fun getRefreshToken(): String? = decryptString(prefs.getString(KEY_REFRESH_TOKEN, null))

    fun clearTokens() {
        prefs.edit { remove(KEY_ACCESS_TOKEN); remove(KEY_REFRESH_TOKEN) }
    }

    // Settings Persistence
    fun saveSelectedServerId(serverId: String) {
        prefs.edit { putString(KEY_SELECTED_SERVER_ID, serverId) }
    }
    fun getSelectedServerId(): String? = prefs.getString(KEY_SELECTED_SERVER_ID, null)

    fun saveSelectedDns(dnsName: String) {
        prefs.edit { putString(KEY_SELECTED_DNS, dnsName) }
    }
    fun getSelectedDns(): String? = prefs.getString(KEY_SELECTED_DNS, null)

    fun saveKillSwitchEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_KILL_SWITCH, enabled) }
    }
    fun isKillSwitchEnabled(): Boolean = prefs.getBoolean(KEY_KILL_SWITCH, false)

    fun saveSplitTunnelingEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SPLIT_TUNNELING, enabled) }
    }
    fun isSplitTunnelingEnabled(): Boolean = prefs.getBoolean(KEY_SPLIT_TUNNELING, false)

    fun saveExcludedPackages(packages: Set<String>) {
        prefs.edit { putStringSet(KEY_EXCLUDED_PACKAGES, packages) }
    }
    fun getExcludedPackages(): Set<String> = prefs.getStringSet(KEY_EXCLUDED_PACKAGES, emptySet()) ?: emptySet()

    fun saveAutoConnectEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_CONNECT, enabled) }
    }
    fun isAutoConnectEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_CONNECT, false)

    // VPN Prominent Disclosure Acceptance
    fun saveVpnDisclosureAccepted(accepted: Boolean) {
        prefs.edit { putBoolean(KEY_VPN_DISCLOSURE_ACCEPTED, accepted) }
    }
    fun isVpnDisclosureAccepted(): Boolean = prefs.getBoolean(KEY_VPN_DISCLOSURE_ACCEPTED, false)

    // Rewarded Pass Expiry
    fun saveRewardedPassExpiry(expiresAtMs: Long) {
        prefs.edit { putLong(KEY_REWARDED_EXPIRES_AT, expiresAtMs) }
    }
    fun getRewardedPassExpiry(): Long = prefs.getLong(KEY_REWARDED_EXPIRES_AT, 0L)

    // Favorite Servers Persistence
    fun saveFavoriteServerIds(ids: Set<String>) {
        prefs.edit { putStringSet(KEY_FAVORITE_SERVERS, ids) }
    }
    fun getFavoriteServerIds(): Set<String> = prefs.getStringSet(KEY_FAVORITE_SERVERS, emptySet()) ?: emptySet()

    // Theme Preference Persistence
    fun saveThemeMode(mode: String) {
        prefs.edit { putString(KEY_THEME_MODE, mode) }
    }
    fun getThemeMode(): String = prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"

    companion object {
        private val vaultLock = Any()
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
