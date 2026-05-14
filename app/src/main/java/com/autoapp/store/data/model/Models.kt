package com.autoapp.store.data.model

data class RegisterRequest(
    val phone: String? = null,
    val email: String? = null,
    val password: String,
    val name: String? = null
)

data class LoginRequest(
    val phone: String? = null,
    val email: String? = null,
    val password: String
)

data class AuthResponse(
    val token: String,
    val user: User
)

data class User(
    val id: String,
    val phone: String?,
    val email: String?,
    val name: String?
)

data class UserResponse(
    val id: String,
    val phone: String?,
    val email: String?,
    val name: String?,
    val subscription: Subscription?
)

data class Subscription(
    val id: String,
    val plan: String,
    val status: String,
    val startDate: String,
    val endDate: String
)

data class AppsResponse(
    val data: List<AppItem>,
    val pagination: Pagination
)

data class Pagination(
    val page: Int,
    val limit: Int,
    val total: Int
)

data class AppItem(
    val id: String,
    val packageName: String,
    val name: String,
    val description: String?,
    val category: String,
    val iconUrl: String?,
    val versions: List<AppVersion>?
)

data class AppDetailResponse(
    val id: String,
    val packageName: String,
    val name: String,
    val description: String?,
    val category: String,
    val iconUrl: String?,
    val versions: List<AppVersion>
)

data class AppVersion(
    val id: String,
    val versionName: String,
    val versionCode: Int,
    val apkPath: String,
    val apkSize: Int,
    val changelog: String?,
    val isLatest: Boolean,
    val createdAt: String
)

data class DownloadUrlResponse(
    val appId: String,
    val version: String,
    val versionCode: Int,
    val downloadUrl: String,
    val size: Int
)

data class SubscriptionStatusResponse(
    val hasActiveSubscription: Boolean,
    val subscription: Subscription?
)

data class CreateSubscriptionRequest(
    val plan: String
)

data class SubscriptionResponse(
    val success: Boolean,
    val subscription: Subscription
)
