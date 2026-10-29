package com.example.myapplication.security

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Never restored from intents or saved navigation state. Process death requires authentication. */
object AuthSession {
    private val authenticated = MutableStateFlow(false)
    val isAuthenticated = authenticated.asStateFlow()
    private val reset = MutableStateFlow(0L)
    val resetAuthorization = reset.asStateFlow()
    private var resetExpiresAt: Long
        get() = reset.value
        set(value) { reset.value = value }
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    val hasResetAuthorization: Boolean get() = SystemClock.elapsedRealtime() < resetExpiresAt
    val canReset: Boolean get() = authenticated.value || SystemClock.elapsedRealtime() < resetExpiresAt
    fun unlock() { authenticated.value = true }
    fun lock() { authenticated.value = false; resetExpiresAt = 0 }
    fun authorizeReset() {
        val deadline = SystemClock.elapsedRealtime() + 120_000L
        resetExpiresAt = deadline
        scope.launch {
            kotlinx.coroutines.delay(120_000L)
            if (resetExpiresAt == deadline) resetExpiresAt = 0
        }
    }
    fun finishReset() { resetExpiresAt = 0; unlock() }
}
