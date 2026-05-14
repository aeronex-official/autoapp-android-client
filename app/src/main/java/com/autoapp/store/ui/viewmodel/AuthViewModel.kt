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

    private val _authState = MutableLiveData<AuthState>()
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
                    _authState.value = AuthState.Success
                } else {
                    _authState.value = AuthState.Error("Login failed: ${response.message()}")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Unknown error")
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
                    _authState.value = AuthState.Success
                } else {
                    _authState.value = AuthState.Error("Registration failed: ${response.message()}")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun logout() {
        PrefsManager.clear()
        _authState.value = AuthState.LoggedOut
    }

    private fun saveAuth(response: AuthResponse) {
        PrefsManager.token = response.token
        PrefsManager.userId = response.user.id
    }

    sealed class AuthState {
        object Idle : AuthState()
        object Loading : AuthState()
        object Success : AuthState()
        object LoggedOut : AuthState()
        data class Error(val message: String) : AuthState()
    }
}
