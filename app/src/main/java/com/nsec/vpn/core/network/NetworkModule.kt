package com.nsec.vpn.core.network

import android.content.Context
import com.nsec.vpn.data.api.NsecVpnApi
import com.nsec.vpn.data.local.KeyStoreManager
import com.nsec.vpn.data.monetization.AdMobManager
import com.nsec.vpn.data.repository.VpnRepository
import com.nsec.vpn.vpn.WireGuardTunnelManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    private const val BASE_URL = "https://api.nsecvpn.com/api/v1/"

    private val moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                // A location may try several agents; keep ordinary catalog requests fast.
                val timed = if (request.url.encodedPath.endsWith("/vpn/connect"))
                    chain.withReadTimeout(45, TimeUnit.SECONDS) else chain
                timed.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.NONE
            })
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
    val api: NsecVpnApi by lazy { retrofit.create(NsecVpnApi::class.java) }
    val recoveryApi: com.nsec.vpn.data.api.DeviceRecoveryApi by lazy { retrofit.create(com.nsec.vpn.data.api.DeviceRecoveryApi::class.java) }
}

class ServiceLocator(context: Context) {
    val appContext: Context = context.applicationContext

    val keyStoreManager by lazy { KeyStoreManager(appContext) }
    val vpnRepository by lazy { VpnRepository(appContext, NetworkModule.api, keyStoreManager) }
    val tunnelManager by lazy { WireGuardTunnelManager.getInstance(appContext) }
    val adMobManager by lazy { AdMobManager(appContext) }
    val remoteConfigManager by lazy { com.nsec.vpn.data.config.RemoteConfigManager(NetworkModule.api, adMobManager, appContext) }
    val playBillingManager by lazy { com.nsec.vpn.data.billing.PlayBillingManager(appContext, NetworkModule.api) }

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
