package com.autoapp.store.data.local

import android.content.Context
import android.content.SharedPreferences

object PrefsManager {
    private const val PREFS_NAME = "autoapp_prefs"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_SUBSCRIPTION_END = "subscription_end"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var subscriptionEnd: Long
        get() = prefs.getLong(KEY_SUBSCRIPTION_END, 0)
        set(value) = prefs.edit().putLong(KEY_SUBSCRIPTION_END, value).apply()

    fun isLoggedIn(): Boolean = !token.isNullOrBlank()

    fun hasActiveSubscription(): Boolean {
        return subscriptionEnd > System.currentTimeMillis()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
