package com.autoapp.store.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoapp.store.data.local.PrefsManager
import com.autoapp.store.data.model.AuthResponse
import com.autoapp.store.data.model.LoginRequest
import com.autoapp.store.data.model.RegisterRequest
import com.autoapp.store.data.remote.RetrofitClient
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val _authState = MutableLiveData<AuthState>(AuthState.Idle)
    val authState: LiveData<AuthState> = _authState

    fun login(phoneOrEmail: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val request = if (phoneOrEmail.contains("@")) {
                    LoginRequest(email = phoneOrEmail, password = password)
                } else {
                    LoginRequest(phone = phoneOrEmail, password = password)
                }
                val response = RetrofitClient.apiService.login(request)
                if (response.isSuccessful) {
                    response.body()?.let { saveAuth(it) }
                    // 登录成功后立即同步订阅状态，写入本地缓存
                    // 这样 AppsFragment 无需二次请求就能判断 hasActiveSubscription()
                    syncSubscriptionAfterLogin()
                    _authState.value = AuthState.Success
                } else {
                    _authState.value = AuthState.Error("登录失败: ${response.message()}")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "未知错误")
            }
        }
    }

    fun register(phoneOrEmail: String, password: String, name: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val request = if (phoneOrEmail.contains("@")) {
                    RegisterRequest(email = phoneOrEmail, password = password, name = name)
                } else {
                    RegisterRequest(phone = phoneOrEmail, password = password, name = name)
                }
                val response = RetrofitClient.apiService.register(request)
                if (response.isSuccessful) {
                    response.body()?.let { saveAuth(it) }
                    // 新注册用户一般无订阅，同步一次确保 subscriptionEnd = 0
                    syncSubscriptionAfterLogin()
                    _authState.value = AuthState.Success
                } else {
                    _authState.value = AuthState.Error("注册失败: ${response.message()}")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "未知错误")
            }
        }
    }

    fun logout() {
        PrefsManager.clear()
        _authState.value = AuthState.LoggedOut
    }

    private fun saveAuth(response: AuthResponse) {
        PrefsManager.token  = response.token
        PrefsManager.userId = response.user.id
    }

    /**
     * 登录/注册成功后，立即向服务器查询订阅状态并写入 PrefsManager.subscriptionEnd。
     * 这样 AppsFragment 点击下载时，hasActiveSubscription() 能直接命中本地缓存，
     * 无需再发一次网络请求。
     */
    private suspend fun syncSubscriptionAfterLogin() {
        val token = PrefsManager.token ?: return
        try {
            val response = RetrofitClient.apiService.getSubscriptionStatus("Bearer $token")
            if (response.isSuccessful) {
                val sub = response.body()?.subscription
                sub?.endDate?.let { endDate ->
                    val epochMs = runCatching {
                        java.text.SimpleDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                            java.util.Locale.US
                        ).parse(endDate)?.time ?: 0L
                    }.getOrDefault(0L)
                    PrefsManager.subscriptionEnd = epochMs
                } ?: run {
                    // 无订阅，重置为 0
                    PrefsManager.subscriptionEnd = 0L
                }
            }
        } catch (_: Exception) {
            // 网络失败不影响登录流程，subscriptionEnd 保持原值
        }
    }

    sealed class AuthState {
        object Idle      : AuthState()
        object Loading   : AuthState()
        object Success   : AuthState()
        object LoggedOut : AuthState()
        data class Error(val message: String) : AuthState()
    }
}
