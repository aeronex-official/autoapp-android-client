package com.autoapp.store.data.remote

import com.autoapp.store.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @GET("auth/me")
    suspend fun getMe(@Header("Authorization") token: String): Response<UserResponse>

    @GET("apps")
    suspend fun getApps(
        @Query("category") category: String? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<AppsResponse>

    @GET("apps/{id}")
    suspend fun getAppDetail(@Path("id") id: String): Response<AppDetailResponse>

    @GET("apps/{id}/download")
    suspend fun getDownloadUrl(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<DownloadUrlResponse>

    @GET("subscriptions/status")
    suspend fun getSubscriptionStatus(
        @Header("Authorization") token: String
    ): Response<SubscriptionStatusResponse>

    @POST("subscriptions/create")
    suspend fun createSubscription(
        @Header("Authorization") token: String,
        @Body request: CreateSubscriptionRequest
    ): Response<SubscriptionResponse>

    /**
     * 上报下载结果状态（success / failed）到后端。
     * 后端凭此更新 Download 记录的 status 字段，供管理台展示。
     */
    @POST("apps/{appId}/download-status")
    suspend fun reportDownloadStatus(
        @Path("appId") appId: String,
        @Body body: Map<String, String>
    ): Response<Unit>
}
