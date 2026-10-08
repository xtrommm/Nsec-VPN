package com.example

import android.app.Application
import com.example.core.network.ServiceLocator

class AuraVpnApp : Application() {
    lateinit var serviceLocator: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        try {
            serviceLocator = ServiceLocator.getInstance(this)
        } catch (_: Throwable) {
            serviceLocator = ServiceLocator(this)
        }
    }
}
