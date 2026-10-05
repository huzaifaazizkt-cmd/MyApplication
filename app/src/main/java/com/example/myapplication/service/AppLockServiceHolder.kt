
package com.example.myapplication.service
object AppLockServiceHolder {
    @Volatile
    var isLockScreenOpen: Boolean = false
    @Volatile
    var currentUnlockedApp: String? = null
    @Volatile
    var lastUnlockTime: Long = 0L
    @Volatile
    var suppressedPackage: String? = null
    fun suppressPackage(
        packageName: String?
    ) {
        suppressedPackage = packageName
    }
    fun clearSuppressedPackage() { suppressedPackage = null }
    fun isPackageSuppressed(packageName: String?): Boolean { return !packageName.isNullOrEmpty() &&
            suppressedPackage == packageName
    }
    fun clear() {
        isLockScreenOpen = false
        currentUnlockedApp = null
        lastUnlockTime = 0L
        suppressedPackage = null
    }
}

