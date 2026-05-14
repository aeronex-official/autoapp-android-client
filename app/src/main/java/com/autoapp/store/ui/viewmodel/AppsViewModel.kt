package com.autoapp.store.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoapp.store.data.model.AppItem
import com.autoapp.store.data.model.DownloadUrlResponse
import com.autoapp.store.data.remote.RetrofitClient
import kotlinx.coroutines.launch

class AppsViewModel : ViewModel() {

    private val _apps = MutableLiveData<List<AppItem>>()
    val apps: LiveData<List<AppItem>> = _apps

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _downloadUrl = MutableLiveData<DownloadUrlResponse?>()
    val downloadUrl: LiveData<DownloadUrlResponse?> = _downloadUrl

    fun loadApps(category: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = RetrofitClient.apiService.getApps(category = category)
                if (response.isSuccessful) {
                    _apps.value = response.body()?.data ?: emptyList()
                } else {
                    _error.value = "Failed to load apps"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun requestDownload(appId: String, token: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getDownloadUrl("Bearer $token", appId)
                if (response.isSuccessful) {
                    _downloadUrl.value = response.body()
                } else {
                    _error.value = "Download not available. Check subscription."
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun clearDownloadUrl() {
        _downloadUrl.value = null
    }
}
