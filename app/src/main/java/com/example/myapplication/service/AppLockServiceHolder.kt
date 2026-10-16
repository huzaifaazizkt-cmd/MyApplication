package com.example.myapplication.service

/**
 * Service aur LockScreenActivity ke darmiyan shared state.
 */
object AppLockServiceHolder {

    @Volatile
    var isLockScreenOpen: Boolean = false

    @Volatile
    var currentUnlockedApp: String? = null

    @Volatile
    var lastUnlockTime: Long = 0L

    @Volatile
    var suppressedPackage: String? = null

    // LockScreenActivity ka pehla frame draw hone par service ko batane ke liye
    @Volatile
    var onLockScreenReady: (() -> Unit)? = null

    fun notifyLockScreenReady() {
        onLockScreenReady?.invoke()
    }

    fun suppressPackage(packageName: String?) {
        suppressedPackage = packageName
    }

    fun clearSuppressedPackage() {
        suppressedPackage = null
    }

    fun isPackageSuppressed(packageName: String?): Boolean {
        return !packageName.isNullOrEmpty() && suppressedPackage == packageName
    }

    fun clearUnlockedApp() {
        currentUnlockedApp = null
        lastUnlockTime = 0L
        suppressedPackage = null
    }

    fun clear() {
        isLockScreenOpen = false
        currentUnlockedApp = null
        lastUnlockTime = 0L
        suppressedPackage = null
    }
}