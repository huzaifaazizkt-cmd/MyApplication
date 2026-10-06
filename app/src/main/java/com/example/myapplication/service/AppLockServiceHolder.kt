package com.example.myapplication.service

import android.util.Log

object AppLockServiceHolder {

    private const val TAG = "APPLOCK_STATE"

    // =========================================================
    // LOCK SCREEN STATE
    // =========================================================

    @Volatile
    var isLockScreenOpen: Boolean = false
        set(value) {

            Log.d(
                TAG,
                "isLockScreenOpen: $field -> $value"
            )

            field = value
        }

    // =========================================================
    // CURRENT UNLOCKED APP
    // =========================================================

    @Volatile
    var currentUnlockedApp: String? = null
        set(value) {

            Log.d(
                TAG,
                "currentUnlockedApp: $field -> $value"
            )

            field = value
        }

    // =========================================================
    // LAST UNLOCK TIME
    // =========================================================

    @Volatile
    var lastUnlockTime: Long = 0L
        set(value) {

            Log.d(
                TAG,
                "lastUnlockTime: $field -> $value"
            )

            field = value
        }

    // =========================================================
    // SUPPRESSED PACKAGE
    // =========================================================

    @Volatile
    var suppressedPackage: String? = null
        set(value) {

            Log.d(
                TAG,
                "suppressedPackage: $field -> $value"
            )

            field = value
        }

    // =========================================================
    // SUPPRESS PACKAGE
    // =========================================================

    fun suppressPackage(
        packageName: String?
    ) {

        Log.d(
            TAG,
            "suppressPackage($packageName)"
        )

        suppressedPackage =
            packageName
    }

    // =========================================================
    // CLEAR SUPPRESSION
    // =========================================================

    fun clearSuppressedPackage() {

        Log.d(
            TAG,
            "clearSuppressedPackage()"
        )

        suppressedPackage = null
    }

    // =========================================================
    // CHECK SUPPRESSED PACKAGE
    // =========================================================

    fun isPackageSuppressed(
        packageName: String?
    ): Boolean {

        val result =
            !packageName.isNullOrEmpty() &&
                    suppressedPackage == packageName

        Log.d(
            TAG,
            "isPackageSuppressed($packageName) = $result"
        )

        return result
    }

    // =========================================================
    // CLEAR UNLOCKED APP
    // =========================================================

    fun clearUnlockedApp() {

        Log.d(
            TAG,
            "clearUnlockedApp()"
        )

        currentUnlockedApp = null

        lastUnlockTime = 0L

        suppressedPackage = null
    }

    // =========================================================
    // CLEAR EVERYTHING
    // =========================================================

    fun clear() {

        Log.d(
            TAG,
            "clear()"
        )

        isLockScreenOpen = false

        currentUnlockedApp = null

        lastUnlockTime = 0L

        suppressedPackage = null
    }
}

