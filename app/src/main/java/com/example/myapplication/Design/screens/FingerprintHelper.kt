package com.example.myapplication.Design.screens

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.myapplication.R
import java.util.WeakHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.myapplication.service.AppLockServiceHolder

private val prompts = WeakHashMap<FragmentActivity, BiometricPrompt>()
private val activePrompts = MutableStateFlow<Set<Int>>(emptySet())
val fingerprintPromptActivities = activePrompts.asStateFlow()
fun isFingerprintPromptShowing(activity: FragmentActivity) = prompts.containsKey(activity)
private fun setPromptVisible(activity: FragmentActivity, visible: Boolean) {
    val key = System.identityHashCode(activity)
    activePrompts.value = if (visible) activePrompts.value + key else activePrompts.value - key
    AppLockServiceHolder.onAuthenticationUiChanged()
}
fun cancelFingerprintPrompt(activity: FragmentActivity) {
    prompts.remove(activity)?.cancelAuthentication()
    setPromptVisible(activity, false)
}

fun showFingerprintPrompt(activity: FragmentActivity, onSuccess: () -> Unit) {
    if (prompts.isNotEmpty() || activity.isFinishing || activity.isDestroyed ||
        !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
        !activity.window.decorView.hasWindowFocus()) return
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK
    if (BiometricManager.from(activity).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
        Toast.makeText(activity, R.string.biometric_unavailable, Toast.LENGTH_LONG).show()
        return
    }
    val observer = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            cancelFingerprintPrompt(activity)
            owner.lifecycle.removeObserver(this)
        }
    }
    var prompt: BiometricPrompt? = null
    fun cleanup() {
        if (prompts[activity] === prompt) {
            prompts.remove(activity)
            setPromptVisible(activity, false)
        }
        activity.lifecycle.removeObserver(observer)
    }
    prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val current = prompts[activity] === prompt
                cleanup()
                if (current && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    activity.lifecycleScope.launch {
                        runCatching { com.example.myapplication.data.DataStoreManager(activity).resetIntruderWrongAttempts() }
                    }
                    onSuccess()
                }
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { cleanup() }
        })
    prompts[activity] = prompt
    setPromptVisible(activity, true)
    activity.lifecycle.addObserver(observer)
    try {
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(activity.getString(R.string.fingerprint_lock))
            .setAllowedAuthenticators(authenticators).setNegativeButtonText(activity.getString(R.string.cancel)).build())
    } catch (_: Exception) { cleanup() }
}
