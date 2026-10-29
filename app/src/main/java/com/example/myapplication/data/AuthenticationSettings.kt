package com.example.myapplication.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

/** One coherent UI snapshot. Credentials themselves never reach the presentation layer. */
data class AuthenticationSettings(
    val type: String,
    val pinLength: Int,
    val fingerprint: Boolean,
    val vibration: Boolean,
    val hideTrack: Boolean
)

object AuthenticationSettingsCache {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val snapshot = MutableStateFlow<AuthenticationSettings?>(null)
    val settings = snapshot.asStateFlow()
    private val failed = MutableStateFlow(false)
    val unavailable = failed.asStateFlow()
    private var started = false

    @Synchronized fun start(context: Context) {
        if (started) return
        started = true
        val store = DataStoreManager(context.applicationContext)
        scope.launch {
            store.authenticationSettings.retryWhen { error, _ ->
                snapshot.value = null
                failed.value = true
                Log.e("AuthenticationSettings", "Settings unavailable; retrying", error)
                delay(1_000)
                true
            }.collect {
                failed.value = false
                snapshot.value = it
            }
        }
    }
}
