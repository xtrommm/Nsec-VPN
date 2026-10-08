package com.example

import android.app.Application
import com.example.core.network.ServiceLocator

class AuraVpnApp : Application() {
    lateinit var serviceLocator: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        serviceLocator = ServiceLocator.getInstance(this)
    }
}
