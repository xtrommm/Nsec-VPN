package com.nsec.vpn
import android.content.Context
import com.nsec.vpn.data.local.KeyStoreManager
object TestVault {
    private val key = javax.crypto.KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()
    fun create(context: Context) = KeyStoreManager(context) { key }
}
