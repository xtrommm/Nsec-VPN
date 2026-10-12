package com.nsec.vpn.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class RegisterDeviceRequest(
    val deviceId: String,
    val deviceModel: String,
    val osVersion: String,
    val appVersion: String,
    val appVersionCode: Int,
    val registrationSecret: String? = null,
    val registrationProof: String? = null
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
    val totalNodes: Int = 1,
    val publicIpv4: String? = null
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
    val mtu: Int = 1420,
    val nodeId: String? = null,
    val nodeName: String? = null,
    val locationName: String? = null,
    val publicIpv4: String? = null
)

data class DisconnectVpnRequest(val peerId: String, val clientPublicKey: String)
data class DisconnectVpnResponse(val success: Boolean, val message: String?)

data class ConfigResponse(val success: Boolean, val data: AppRemoteConfigDto?)
data class AppRemoteConfigDto(
    val app: AppVersionDto,
    val announcement: AnnouncementDto,
    val ads: AdsDto,
    val support: SupportDto? = null,
    val updatedAt: String? = null
)
data class SupportDto(val email: String? = null)
data class AppVersionDto(val minAppVersionCode: Int, val latestAppVersionCode: Int, val forceUpdate: Boolean, val maintenanceMode: Boolean = false)
data class AnnouncementDto(val active: Boolean, val title: String?, val body: String?, val dismissible: Boolean)
data class AdsDto(
    val bannerEnabled: Boolean,
    val interstitialEnabled: Boolean,
    val interstitialIntervalSeconds: Int,
    val rewardedEnabled: Boolean,
    val nativeEnabled: Boolean? = null,
    val bannerAdUnitId: String? = null,
    val nativeAdUnitId: String? = null,
    val interstitialAdUnitId: String? = null,
    val rewardedAdUnitId: String? = null,
    val rewardedPassMinutes: Int = 30,
    val enabled: Boolean? = null
)

data class LegalResponse(val success: Boolean, val data: LegalDocDto?)
data class LegalDocDto(val type: String, val version: String, val contentMarkdown: String)

data class VerifyPurchaseRequest(val packageName: String, val productId: String, val purchaseToken: String)
data class VerifyPurchaseResponse(val success: Boolean)

data class DeviceProfileResponse(val success: Boolean, val data: DeviceInfoDto?)
data class RewardTicketData(val ticketId: String)
data class RewardTicketResponse(val success: Boolean, val data: RewardTicketData?)
data class ClaimRewardRequest(val ticketId: String)

data class RedeemPromoRequest(val code: String)
data class RedeemPromoData(val tier: String, val passExpiresAt: String?)
data class RedeemPromoResponse(val success: Boolean, val data: RedeemPromoData?)

data class ServerRequestBody(val country: String, val city: String, val note: String? = null)
data class ServerRequestData(val id: String, val country: String, val city: String, val status: String, val createdAt: String)
data class ServerRequestResponse(val success: Boolean, val data: ServerRequestData?)

interface NsecVpnApi {
    @POST("server-requests")
    suspend fun submitServerRequest(@Header("Authorization") token: String, @Body request: ServerRequestBody): Response<ServerRequestResponse>

    @POST("billing/rewarded/prepare")
    suspend fun prepareReward(@Header("Authorization") token: String): Response<RewardTicketResponse>
    @POST("billing/rewarded/claim")
    suspend fun claimReward(@Header("Authorization") token: String, @Body request: ClaimRewardRequest): Response<RedeemPromoResponse>

    @POST("promos/redeem")
    suspend fun redeemPromo(@Header("Authorization") token: String, @Body request: RedeemPromoRequest): Response<RedeemPromoResponse>

    @GET("auth/me")
    suspend fun getDeviceProfile(@Header("Authorization") token: String): Response<DeviceProfileResponse>

    @POST("billing/verify")
    suspend fun verifyPurchase(@Header("Authorization") token: String, @Body request: VerifyPurchaseRequest): Response<VerifyPurchaseResponse>

    @POST("auth/register-device-v2")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): Response<RegisterDeviceResponse>

    @POST("auth/refresh-token")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<RefreshTokenResponse>

    @GET("servers")
    suspend fun getServers(
        @Header("Authorization") token: String,
        @Query("grouped") grouped: Boolean = true
    ): Response<ServerListResponse>

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
