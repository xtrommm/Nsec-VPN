package com.example.core.network

import android.content.Context
import com.example.data.api.AuraVpnApi
import com.example.data.local.KeyStoreManager
import com.example.data.repository.VpnRepository
import com.example.vpn.WireGuardTunnelManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    private const val BASE_URL = "https://api.nsecvpn.com/api/v1/"

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    val api: AuraVpnApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(AuraVpnApi::class.java)
    }
}

class ServiceLocator(val context: Context) {
    val keyStoreManager by lazy { KeyStoreManager(context) }
    val vpnRepository by lazy { VpnRepository(context, NetworkModule.api, keyStoreManager) }
    val tunnelManager by lazy { WireGuardTunnelManager.getInstance(context) }

    companion object {
        @Volatile
        private var instance: ServiceLocator? = null

        fun getInstance(context: Context): ServiceLocator {
            return instance ?: synchronized(this) {
                instance ?: ServiceLocator(context.applicationContext).also { instance = it }
            }
        }
    }
}
