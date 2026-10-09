package com.nsec.vpn.core.network

import android.content.Context
import com.nsec.vpn.data.api.NsecVpnApi
import com.nsec.vpn.data.local.KeyStoreManager
import com.nsec.vpn.data.repository.VpnRepository
import com.nsec.vpn.vpn.WireGuardTunnelManager
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

    val api: NsecVpnApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(NsecVpnApi::class.java)
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
