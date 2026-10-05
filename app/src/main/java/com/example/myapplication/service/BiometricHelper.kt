package com.example.myapplication.service

import android.content.Context
import androidx.biometric.BiometricManager

object BiometricHelper {

    fun isBiometricAvailable(
        context: Context
    ): Boolean {

        val biometricManager =
            BiometricManager.from(context)

        val result =
            biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
            )

        return result ==
                BiometricManager.BIOMETRIC_SUCCESS
    }
}