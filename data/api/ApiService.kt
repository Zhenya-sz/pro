package com.fitnesslemon.app.data.api

import com.fitnesslemon.app.data.models.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // AUTH
    @POST("auth/login")
    suspend fun login(@Body body: Map<String, Any>): Response<AuthResponse>

    @POST("auth/register")
    suspend fun register(@Body body: Map<String, Any>): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(@Body body: Map<String, Any>): Response<AuthResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<BaseResponse>

    // PROFILE / USER
    @GET("users/profile")
    suspend fun getProfile(): Response<UserProfileResponse>

    @PUT("users/profile")
    suspend fun updateProfile(@Body body: Map<String, Any>): Response<BaseResponse>

    // NEWS
    @GET("news")
    suspend fun getNews(): Response<List<NewsResponse>>

    @POST("news/{id}/like")
    suspend fun likeNews(@Path("id") id: Int): Response<BaseResponse>

    @POST("news/{id}/save")
    suspend fun saveNews(@Path("id") id: Int): Response<BaseResponse>

    // WORKOUTS
    @GET("workouts")
    suspend fun getWorkouts(): Response<List<WorkoutResponse>>

    @GET("workouts/{id}")
    suspend fun getWorkout(@Path("id") id: Int): Response<WorkoutResponse>

    // CHATS
    @GET("chats")
    suspend fun getChats(): Response<List<ChatResponse>>

    @GET("chats/{id}")
    suspend fun getChat(@Path("id") id: Int): Response<ChatResponse>

    @GET("chats/{id}/messages")
    suspend fun getMessages(
        @Path("id") chatId: Int,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 100
    ): Response<MessagePageResponse>

    @POST("chats/send")
    suspend fun sendMessage(@Body body: Map<String, Any>): Response<MessageResponse>

    @Multipart
    @POST("chats/send-image")
    suspend fun sendImage(
        @Part("chat_id") chatId: RequestBody,
        @Part("message_id") messageId: RequestBody?,
        @Part file: MultipartBody.Part
    ): Response<BaseResponse>

    @Multipart
    @POST("chats/send-audio")
    suspend fun sendAudio(
        @Part("chat_id") chatId: RequestBody,
        @Part("duration") duration: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<BaseResponse>

    @Multipart
    @POST("chats/send-video")
    suspend fun sendVideo(
        @Part("chat_id") chatId: RequestBody,
        @Part("duration") duration: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<BaseResponse>

    @POST("chats/{id}/read")
    suspend fun markChatRead(@Path("id") chatId: Int, @Body body: Map<String, Any>): Response<BaseResponse>

    @GET("chats/unread")
    suspend fun getUnreadChatsCount(): Response<UnreadCountResponse>

    // NOTIFICATIONS
    @GET("notifications")
    suspend fun getNotifications(): Response<List<NotificationResponse>>

    @GET("notifications/unread")
    suspend fun getUnreadNotificationsCount(): Response<UnreadCountResponse>

    @POST("notifications/mark-read")
    suspend fun markNotificationRead(@Body body: Map<String, Int>): Response<BaseResponse>

    // TRAINERS
    @GET("trainers")
    suspend fun getTrainers(): Response<List<TrainerResponse>>

    // BOOKING
    @GET("bookings/my-bookings")
    suspend fun getMyBookings(): Response<List<BookingResponse>>

    @POST("bookings/book-class")
    suspend fun bookClass(@Body body: Map<String, Any>): Response<BaseResponse>

    @POST("bookings/cancel-booking")
    suspend fun cancelBooking(@Body body: Map<String, Any>): Response<BaseResponse>
}
