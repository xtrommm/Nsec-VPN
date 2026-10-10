package com.nsec.vpn.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

data class RegisterDeviceRequest(
    val deviceId: String,
    val deviceModel: String,
    val osVersion: String,
    val appVersion: String,
    val appVersionCode: Int
)

data class RegisterDeviceResponse(
    val success: Boolean,
    val data: AuthDataDto?
)

data class AuthDataDto(
    val accessToken: String,
    val refreshToken: String,
    val device: DeviceInfoDto
)

data class DeviceInfoDto(
    val id: String,
    val tier: String,
    val passExpiresAt: String?
)

data class RefreshTokenRequest(val refreshToken: String)
data class RefreshTokenResponse(val success: Boolean, val data: TokenDataDto?)
data class TokenDataDto(val accessToken: String, val refreshToken: String)

data class ServerListResponse(val success: Boolean, val data: ServerListDataDto?)
data class ServerListDataDto(val servers: List<ServerItemDto>)

data class ServerItemDto(
    val id: String,
    val name: String,
    val countryCode: String,
    val countryName: String,
    val city: String,
    val flagEmoji: String,
    val currentLoad: Int,
    val isPremiumOnly: Boolean,
    val isRecommended: Boolean = false,
    val totalNodes: Int = 1
)

data class ConnectVpnRequest(val serverId: String, val clientPublicKey: String)
data class ConnectVpnResponse(val success: Boolean, val data: ConnectVpnDataDto?)
data class ConnectVpnDataDto(val tunnelConfig: TunnelConfigDto, val peerId: String)

data class TunnelConfigDto(
    val serverPublicKey: String,
    val serverEndpoint: String,
    val clientAddress: String,
    val dnsServers: List<String>,
    val allowedIps: List<String>,
    val persistentKeepalive: Int = 25,
    val mtu: Int = 1420
)

data class DisconnectVpnRequest(val peerId: String, val clientPublicKey: String)
data class DisconnectVpnResponse(val success: Boolean, val message: String?)

data class ConfigResponse(val success: Boolean, val data: AppRemoteConfigDto?)
data class AppRemoteConfigDto(
    val app: AppVersionDto,
    val announcement: AnnouncementDto,
    val ads: AdsDto
)
data class AppVersionDto(val minAppVersionCode: Int, val latestAppVersionCode: Int, val forceUpdate: Boolean)
data class AnnouncementDto(val active: Boolean, val title: String?, val body: String?, val dismissible: Boolean)
data class AdsDto(
    val bannerEnabled: Boolean,
    val interstitialEnabled: Boolean,
    val interstitialIntervalSeconds: Int,
    val rewardedEnabled: Boolean
)

data class LegalResponse(val success: Boolean, val data: LegalDocDto?)
data class LegalDocDto(val type: String, val version: String, val contentMarkdown: String)

interface NsecVpnApi {
    @POST("auth/register-device")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): Response<RegisterDeviceResponse>

    @POST("auth/refresh-token")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<RefreshTokenResponse>

    @GET("servers")
    suspend fun getServers(@Header("Authorization") token: String): Response<ServerListResponse>

    @POST("vpn/connect")
    suspend fun connect(
        @Header("Authorization") token: String,
        @Body request: ConnectVpnRequest
    ): Response<ConnectVpnResponse>

    @POST("vpn/disconnect")
    suspend fun disconnect(
        @Header("Authorization") token: String,
        @Body request: DisconnectVpnRequest
    ): Response<DisconnectVpnResponse>

    @GET("config")
    suspend fun getConfig(): Response<ConfigResponse>

    @GET("legal/{type}")
    suspend fun getLegal(@Path("type") type: String): Response<LegalResponse>
}
