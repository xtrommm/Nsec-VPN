package com.nsec.vpn.data.api

import retrofit2.Response
import retrofit2.http.*

data class RecoveryStatusData(val configured: Boolean)
data class RecoveryStatusResponse(val success: Boolean, val data: RecoveryStatusData?)
data class SetRecoveryPinRequest(val pin: String, val currentPin: String? = null)
data class RecoveryActionResponse(val success: Boolean)
data class RecoverDeviceRequest(val deviceId: String, val pin: String, val registrationSecret: String)
interface DeviceRecoveryApi {
    @GET("auth/recovery/status")
    suspend fun status(@Header("Authorization") token: String): Response<RecoveryStatusResponse>
    @PUT("auth/recovery/pin")
    suspend fun setPin(@Header("Authorization") token: String, @Body request: SetRecoveryPinRequest): Response<RecoveryActionResponse>
    @POST("auth/recover-device")
    suspend fun recover(@Body request: RecoverDeviceRequest): Response<RegisterDeviceResponse>
}
