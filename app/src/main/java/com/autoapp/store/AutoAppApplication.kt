package com.autoapp.store

import android.app.Application
import com.autoapp.store.data.local.PrefsManager

class AutoAppApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PrefsManager.init(this)
    }
}
