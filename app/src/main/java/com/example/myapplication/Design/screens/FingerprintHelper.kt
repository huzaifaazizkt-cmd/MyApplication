package com.example.myapplication.Design.screens

import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private const val FP_TAG = "FINGERPRINT_HELPER"
private var fingerprintRunning = false

fun showFingerprintPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit
) {
    if (fingerprintRunning) return

    val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK

    val canAuth = BiometricManager.from(activity).canAuthenticate(authenticators)
    Log.d(FP_TAG, "canAuthenticate = $canAuth (0 = success)")

    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) return

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Fingerprint Lock")
        .setSubtitle("Use your fingerprint to unlock")
        .setAllowedAuthenticators(authenticators)
        .setConfirmationRequired(false)
        .setNegativeButtonText("Use PIN / Pattern")
        .build()

    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {

            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult
            ) {
                fingerprintRunning = false
                Log.d(FP_TAG, "SUCCESS")
                onSuccess()
            }

            override fun onAuthenticationError(
                errorCode: Int,
                errString: CharSequence
            ) {
                fingerprintRunning = false
                Log.d(FP_TAG, "ERROR code=$errorCode msg=$errString")
            }
        }
    )

    fingerprintRunning = true
    try {
        prompt.authenticate(promptInfo)
    } catch (e: Exception) {
        fingerprintRunning = false
        Log.e(FP_TAG, "AUTHENTICATE EXCEPTION", e)
    }
}