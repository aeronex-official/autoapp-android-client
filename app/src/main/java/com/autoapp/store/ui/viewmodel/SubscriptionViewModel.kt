package com.autoapp.store.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoapp.store.data.local.PrefsManager
import com.autoapp.store.data.model.CreateSubscriptionRequest
import com.autoapp.store.data.model.Subscription
import com.autoapp.store.data.remote.RetrofitClient
import kotlinx.coroutines.launch

class SubscriptionViewModel : ViewModel() {

    private val _subscription = MutableLiveData<Subscription?>()
    val subscription: LiveData<Subscription?> = _subscription

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun checkSubscription(token: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getSubscriptionStatus("Bearer $token")
                if (response.isSuccessful) {
                    val sub = response.body()?.subscription
                    _subscription.value = sub
                    sub?.endDate?.let {
                        try {
                            PrefsManager.subscriptionEnd = java.text.SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                                java.util.Locale.US
                            ).parse(it)?.time ?: 0
                        } catch (_: Exception) {
                            PrefsManager.subscriptionEnd = 0
                        }
                    }
                }
            } catch (e: Exception) {
                if (PrefsManager.hasActiveSubscription()) {
                    _subscription.value = Subscription(
                        id = "cached", plan = "unknown", status = "active",
                        startDate = "", endDate = ""
                    )
                }
            }
        }
    }

    fun subscribe(plan: String, token: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = RetrofitClient.apiService.createSubscription(
                    "Bearer $token",
                    CreateSubscriptionRequest(plan)
                )
                if (response.isSuccessful) {
                    val sub = response.body()?.subscription
                    _subscription.value = sub
                    sub?.endDate?.let {
                        try {
                            PrefsManager.subscriptionEnd = java.text.SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                                java.util.Locale.US
                            ).parse(it)?.time ?: 0
                        } catch (_: Exception) {}
                    }
                } else {
                    _error.value = "Subscription failed"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
}
