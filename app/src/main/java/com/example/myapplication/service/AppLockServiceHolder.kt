package com.example.myapplication.service

import android.util.Log

object AppLockServiceHolder {

    private const val TAG = "APPLOCK_STATE"

    @Volatile
    var isLockScreenOpen: Boolean = false
        set(value) {
            Log.d(TAG, "isLockScreenOpen: $field -> $value")
            field = value
        }

    @Volatile
    var currentUnlockedApp: String? = null
        set(value) {
            Log.d(TAG, "currentUnlockedApp: $field -> $value")
            field = value
        }

    @Volatile
    var lastUnlockTime: Long = 0L
        set(value) {
            Log.d(TAG, "lastUnlockTime: $field -> $value")
            field = value
        }

    @Volatile
    var suppressedPackage: String? = null
        set(value) {
            Log.d(TAG, "suppressedPackage: $field -> $value")
            field = value
        }

    fun suppressPackage(packageName: String?) {
        Log.d(TAG, "suppressPackage($packageName)")
        suppressedPackage = packageName
    }

    fun clearSuppressedPackage() {
        Log.d(TAG, "clearSuppressedPackage()")
        suppressedPackage = null
    }

    fun isPackageSuppressed(packageName: String?): Boolean {
        val result = !packageName.isNullOrEmpty() &&
                suppressedPackage == packageName

        Log.d(TAG, "isPackageSuppressed($packageName) = $result")
        return result
    }

    fun clearUnlockedApp() {
        Log.d(TAG, "clearUnlockedApp()")
        currentUnlockedApp = null
        lastUnlockTime = 0L
        suppressedPackage = null
    }

    fun clear() {
        Log.d(TAG, "clear()")

        isLockScreenOpen = false
        currentUnlockedApp = null
        lastUnlockTime = 0L
        suppressedPackage = null
    }
}