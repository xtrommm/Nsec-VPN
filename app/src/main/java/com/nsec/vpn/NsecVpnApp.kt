package com.nsec.vpn

import android.app.Application
import com.nsec.vpn.core.network.ServiceLocator

class NsecVpnApp : Application() {
    lateinit var serviceLocator: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        com.nsec.vpn.ui.theme.NativeSplashTheme.initialize(this)
        try {
            serviceLocator = ServiceLocator.getInstance(this)
        } catch (_: Throwable) {
            serviceLocator = ServiceLocator(this)
        }
        com.wireguard.android.backend.GoBackend.setAlwaysOnCallback {
            serviceLocator.tunnelManager.restoreConnection()
        }
        serviceLocator.tunnelManager.restoreConnection()
    }
}
