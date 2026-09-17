// app/src/main/java/com/fitnesslemon/app/data/api/DeviceApiService.kt

package com.fitnesslemon.app.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

interface DeviceApiService {

    @POST("device/register")
    suspend fun registerToken(
        @Header("Authorization") token: String,
        @Body request: RegisterDeviceRequest
    ): Response<ApiResponse>

    @DELETE("device/unregister")
    suspend fun unregisterToken(
        @Header("Authorization") token: String,
        @Body request: UnregisterDeviceRequest
    ): Response<ApiResponse>

    @GET("device/tokens")
    suspend fun getTokens(
        @Header("Authorization") token: String
    ): Response<DeviceTokensResponse>
}

// ===== DATA CLASSES =====

data class RegisterDeviceRequest(
    @SerializedName("fcm_token")
    val fcmToken: String,
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("platform")
    val platform: String = "android"
)

data class UnregisterDeviceRequest(
    @SerializedName("device_id")
    val deviceId: String
)

data class DeviceTokensResponse(
    val success: Boolean,
    val message: String,
    val data: DeviceTokensData?
)

data class DeviceTokensData(
    val tokens: List<String>
)