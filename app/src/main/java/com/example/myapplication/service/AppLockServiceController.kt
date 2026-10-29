package com.example.myapplication.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.myapplication.data.DataStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object AppLockServiceController {
    suspend fun sync(context: Context) {
        val app = context.applicationContext
        val config = DataStoreManager(app).protectionSettings.first()
        val access = withContext(Dispatchers.IO) { AppLockPermissions.read(app) }
        withContext(Dispatchers.Main.immediate) {
            if (config.enabled && config.lockedApps.isNotEmpty() && access.ready) {
                if (AppLockServiceHolder.service == null) {
                    try {
                        ContextCompat.startForegroundService(app, Intent(app, AppLockService::class.java))
                    } catch (error: Exception) {
                        AppLockServiceHolder.setStatus(ProtectionStatus.START_FAILED)
                        Log.w("AppLockService", "Unable to start protection", error)
                    }
                }
            } else {
                app.stopService(Intent(app, AppLockService::class.java))
            }
        }
    }
}
