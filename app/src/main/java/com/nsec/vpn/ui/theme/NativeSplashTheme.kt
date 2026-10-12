package com.nsec.vpn.ui.theme

import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat

object NativeSplashTheme {
    private var lastApplied: Int? = null
    private var observing = false

    fun apply(context: Context, mode: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val manager = context.getSystemService(UiModeManager::class.java) ?: return
        val nightMode = when (mode) {
            "DARK" -> UiModeManager.MODE_NIGHT_YES
            "LIGHT" -> UiModeManager.MODE_NIGHT_NO
            else -> manager.nightMode
        }
        if (lastApplied == nightMode) return
        manager.setApplicationNightMode(nightMode)
        lastApplied = nightMode
    }

    fun initialize(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val app = context.applicationContext
        fun sync() {
            val mode = app.getSharedPreferences("nsecvpn_secure_vault", Context.MODE_PRIVATE)
                .getString("app_theme_mode", "SYSTEM") ?: "SYSTEM"
            apply(app, mode)
        }
        sync()
        if (observing) return
        observing = true
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { sync() }
        }, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}
