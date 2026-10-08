
package com.example.myapplication.service
import com.example.myapplication.service.AppLockServiceHolder

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import com.example.myapplication.Design.screens.LockScreenActivity
import com.example.myapplication.data.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppLockService : AccessibilityService() {

    companion object {
        private const val TAG = "AppLockService"
        private const val RELOCK_AFTER_QUITTING = "relock_after_quitting"
        private const val RELOCK_AFTER_SCREEN_OFF = "relock_after_screen_off"
        private const val NOTIFICATION_CHANNEL_ID = "applock_service_channel"
        private const val NOTIFICATION_ID = 1001

        private const val LOCK_LAUNCH_GUARD_MS = 2000L
        private const val FOREGROUND_VERIFY_DELAY_MS = 300L
    }

    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val handler =
        Handler(Looper.getMainLooper())

    @Volatile
    private var appProtectionEnabled = false

    @Volatile
    private var relockOption = RELOCK_AFTER_QUITTING

    @Volatile
    private var relockDelay = "never"

    @Volatile
    private var serviceConnected = false

    @Volatile
    private var settingsLoaded = false

    @Volatile
    private var lockedAppsCache: Set<String> = emptySet()

    @Volatile
    private var lockLaunchTime = 0L

    private var foregroundPackage: String? = null
    private var pendingPackage: String? = null
    private var homePackageName: String? = null
    private var checkingPackage: String? = null
    private var imePackages: Set<String> = emptySet()

    private val foregroundVerifyRunnable = Runnable {
        verifyForegroundPackage()
    }

    private val resetUnlockedAppRunnable = Runnable {
        clearUnlockedApp("RELOCK_TIMER")
    }

    private val screenOffReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                if (intent?.action != Intent.ACTION_SCREEN_OFF) return
                if (!appProtectionEnabled) return
                if (AppLockServiceHolder.currentUnlockedApp == null) return

                if (relockOption == RELOCK_AFTER_SCREEN_OFF) {
                    scheduleRelock("SCREEN_OFF")
                }
            }
        }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "================================")
        Log.d(TAG, "APP LOCK SERVICE CREATED")
        Log.d(TAG, "================================")

        resolveHomePackage()
        refreshImePackages()
        registerScreenOffReceiver()
        observeDataStore()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        Log.d(TAG, "================================")
        Log.d(TAG, "ACCESSIBILITY SERVICE CONNECTED")
        Log.d(TAG, "================================")

        serviceInfo =
            AccessibilityServiceInfo().apply {
                eventTypes =
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                            AccessibilityEvent.TYPE_WINDOWS_CHANGED

                feedbackType =
                    AccessibilityServiceInfo.FEEDBACK_GENERIC

                notificationTimeout = 50

                flags =
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }

        serviceConnected = true
        settingsLoaded = false
        lockLaunchTime = 0L
        foregroundPackage = null
        pendingPackage = null
        checkingPackage = null

        AppLockServiceHolder.isLockScreenOpen = false

        resolveHomePackage()
        refreshImePackages()
        startForegroundServiceNotification()
        loadCurrentSettings()
    }

    private fun observeDataStore() {
        val dataStore = DataStoreManager(this)

        serviceScope.launch {
            try {
                dataStore.getAppProtectionEnabled()
                    .collectLatest { enabled ->

                        appProtectionEnabled = enabled

                        Log.d(
                            TAG,
                            "APP PROTECTION = $enabled"
                        )

                        if (!enabled) {
                            withContext(Dispatchers.Main) {
                                clearProtectionState()
                            }
                        }

                        updateSettingsLoaded()
                    }
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "APP PROTECTION ERROR",
                    e
                )
            }
        }

        serviceScope.launch {
            try {
                dataStore.lockedAppsFlow
                    .collectLatest { apps ->

                        lockedAppsCache = apps.toSet()

                        Log.d(
                            TAG,
                            "LOCKED APPS = $lockedAppsCache"
                        )

                        updateSettingsLoaded()
                    }
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "LOCKED APPS ERROR",
                    e
                )
            }
        }

        serviceScope.launch {
            try {
                dataStore.getRelockOption()
                    .collectLatest { option ->

                        relockOption = option

                        Log.d(
                            TAG,
                            "RELOCK OPTION = $option"
                        )

                        updateSettingsLoaded()
                    }
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "RELOCK OPTION ERROR",
                    e
                )
            }
        }

        serviceScope.launch {
            try {
                dataStore.getRelockDelay()
                    .collectLatest { delay ->

                        relockDelay =
                            normalizeRelockDelay(delay)

                        Log.d(
                            TAG,
                            "RELOCK DELAY = $relockDelay"
                        )

                        updateSettingsLoaded()
                    }
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "RELOCK DELAY ERROR",
                    e
                )
            }
        }
    }

    private fun updateSettingsLoaded() {
        if (lockedAppsCache.isNotEmpty() || settingsLoaded) {
            settingsLoaded = true
        }
    }

    private fun loadCurrentSettings() {
        serviceScope.launch {
            try {
                val dataStore =
                    DataStoreManager(this@AppLockService)

                appProtectionEnabled =
                    dataStore.getAppProtectionEnabled().first()

                lockedAppsCache =
                    dataStore.lockedAppsFlow.first().toSet()

                relockOption =
                    dataStore.getRelockOption().first()

                relockDelay =
                    normalizeRelockDelay(
                        dataStore.getRelockDelay().first()
                    )

                settingsLoaded = true

                Log.d(TAG, "================================")
                Log.d(
                    TAG,
                    "CURRENT SETTINGS LOADED"
                )
                Log.d(
                    TAG,
                    "PROTECTION = $appProtectionEnabled"
                )
                Log.d(
                    TAG,
                    "LOCKED APPS = $lockedAppsCache"
                )
                Log.d(
                    TAG,
                    "RELOCK OPTION = $relockOption"
                )
                Log.d(
                    TAG,
                    "RELOCK DELAY = $relockDelay"
                )
                Log.d(
                    TAG,
                    "SETTINGS LOADED = $settingsLoaded"
                )
                Log.d(TAG, "================================")
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "INITIAL SETTINGS ERROR",
                    e
                )
            }
        }
    }

    private fun normalizeRelockDelay(
        delay: String
    ): String {
        return when (delay) {
            "five_seconds",
            "5_seconds" -> "five_seconds"

            "ten_seconds",
            "10_seconds" -> "ten_seconds"

            "thirty_seconds",
            "30_seconds" -> "thirty_seconds"

            "one_minute",
            "1_minute" -> "one_minute"

            "two_minutes",
            "2_minutes" -> "two_minutes"

            "five_minutes",
            "5_minutes" -> "five_minutes"

            else -> "never"
        }
    }

    private fun refreshImePackages() {
        try {
            val imm =
                getSystemService(
                    Context.INPUT_METHOD_SERVICE
                ) as InputMethodManager

            imePackages =
                imm.enabledInputMethodList
                    .map { it.packageName }
                    .toSet()
        } catch (e: Exception) {
            imePackages = emptySet()

            Log.e(
                TAG,
                "IME LIST ERROR",
                e
            )
        }
    }

    private fun isIgnorablePackage(
        packageName: String
    ): Boolean {
        return packageName == "com.android.systemui" ||
                packageName == "android" ||
                packageName in imePackages ||
                packageName.contains(
                    "permissioncontroller",
                    true
                ) ||
                packageName.contains(
                    "packageinstaller",
                    true
                ) ||
                packageName.contains(
                    "inputmethod",
                    true
                )
    }

    private fun isOwnPackage(
        packageName: String
    ): Boolean {
        return packageName ==
                applicationContext.packageName
    }

    private fun isLauncherPackage(
        packageName: String?
    ): Boolean {
        if (packageName.isNullOrEmpty()) {
            return false
        }

        return packageName == homePackageName ||
                packageName.contains(
                    "launcher",
                    true
                )
    }

    private fun isLockLaunchRecent(): Boolean {
        return lockLaunchTime != 0L &&
                SystemClock.elapsedRealtime() -
                lockLaunchTime <
                LOCK_LAUNCH_GUARD_MS
    }

    private fun resolveHomePackage() {
        try {
            val homeIntent =
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                }

            homePackageName =
                packageManager
                    .resolveActivity(
                        homeIntent,
                        0
                    )
                    ?.activityInfo
                    ?.packageName

            Log.d(
                TAG,
                "HOME PACKAGE = $homePackageName"
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "HOME PACKAGE ERROR",
                e
            )
        }
    }

    private fun registerScreenOffReceiver() {
        try {
            val filter =
                IntentFilter(
                    Intent.ACTION_SCREEN_OFF
                )

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {
                registerReceiver(
                    screenOffReceiver,
                    filter,
                    RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(
                    screenOffReceiver,
                    filter
                )
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "RECEIVER ERROR",
                e
            )
        }
    }

    private fun clearProtectionState() {
        handler.removeCallbacks(
            resetUnlockedAppRunnable
        )

        handler.removeCallbacks(
            foregroundVerifyRunnable
        )

        AppLockServiceHolder.currentUnlockedApp = null
        AppLockServiceHolder.lastUnlockTime = 0L
        AppLockServiceHolder.isLockScreenOpen = false
        AppLockServiceHolder.clearSuppressedPackage()

        foregroundPackage = null
        pendingPackage = null
        checkingPackage = null
        lockLaunchTime = 0L
    }

    private fun clearUnlockedApp(
        reason: String
    ) {
        Log.d(
            TAG,
            "CLEAR UNLOCKED APP -> $reason"
        )

        handler.removeCallbacks(
            resetUnlockedAppRunnable
        )

        AppLockServiceHolder.currentUnlockedApp = null
        AppLockServiceHolder.lastUnlockTime = 0L
        AppLockServiceHolder.clearSuppressedPackage()

        checkingPackage = null
    }

    private fun getRelockDelayMillis(): Long {
        return when (relockDelay) {
            "five_seconds" -> 5_000L
            "ten_seconds" -> 10_000L
            "thirty_seconds" -> 30_000L
            "one_minute" -> 60_000L
            "two_minutes" -> 120_000L
            "five_minutes" -> 300_000L
            else -> 0L
        }
    }

    private fun scheduleRelock(
        reason: String
    ) {
        if (!appProtectionEnabled) return

        val unlockedApp =
            AppLockServiceHolder.currentUnlockedApp
                ?: return

        handler.removeCallbacks(
            resetUnlockedAppRunnable
        )

        val delay =
            getRelockDelayMillis()

        if (delay <= 0L) {
            clearUnlockedApp(
                "IMMEDIATE_$reason"
            )
            return
        }

        Log.d(
            TAG,
            "RELOCK SCHEDULED: " +
                    "$unlockedApp -> $delay ms"
        )

        handler.postDelayed(
            resetUnlockedAppRunnable,
            delay
        )
    }

    private fun getEventPackage(
        event: AccessibilityEvent
    ): String? {
        val packageName =
            event.packageName?.toString()

        if (!packageName.isNullOrEmpty()) {
            return packageName
        }

        return try {
            rootInActiveWindow
                ?.packageName
                ?.toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun getVerifiedForegroundPackage(): String? {
        try {
            val activeWindows =
                windows

            var focusedPackage: String? = null
            var activePackage: String? = null

            for (window in activeWindows) {
                val packageName =
                    window.root
                        ?.packageName
                        ?.toString()

                if (packageName.isNullOrEmpty()) {
                    continue
                }

                if (window.isFocused) {
                    focusedPackage = packageName
                }

                if (window.isActive) {
                    activePackage = packageName
                }
            }

            return focusedPackage
                ?: activePackage
                ?: rootInActiveWindow
                    ?.packageName
                    ?.toString()
        } catch (e: Exception) {
            Log.e(
                TAG,
                "FOREGROUND VERIFY ERROR",
                e
            )

            return null
        }
    }

    private fun verifyForegroundPackage() {
        if (!serviceConnected ||
            !settingsLoaded ||
            !appProtectionEnabled
        ) {
            return
        }

        val eventPackage =
            pendingPackage

        val verifiedPackage =
            getVerifiedForegroundPackage()
                ?: eventPackage

        pendingPackage = null

        if (verifiedPackage.isNullOrEmpty()) {
            return
        }

        Log.d(
            TAG,
            "VERIFIED FOREGROUND = $verifiedPackage"
        )

        handleConfirmedForeground(
            verifiedPackage
        )
    }

    private fun handleConfirmedForeground(
        packageName: String
    ) {
        if (isOwnPackage(packageName)) {
            Log.d(
                TAG,
                "CONFIRMED OWN APP -> $packageName"
            )
            return
        }

        if (isIgnorablePackage(packageName)) {
            return
        }

        if (isLauncherPackage(packageName)) {
            Log.d(
                TAG,
                "CONFIRMED HOME / LAUNCHER"
            )

            foregroundPackage = packageName
            checkingPackage = null

            if (
                AppLockServiceHolder.currentUnlockedApp != null &&
                relockOption ==
                RELOCK_AFTER_QUITTING
            ) {
                scheduleRelock("HOME")
            }

            return
        }

        val previousPackage =
            foregroundPackage

        if (previousPackage == packageName) {
            checkAndLockPackage(packageName)
            return
        }

        foregroundPackage = packageName

        val unlockedApp =
            AppLockServiceHolder.currentUnlockedApp

        if (
            !unlockedApp.isNullOrEmpty() &&
            previousPackage == unlockedApp &&
            packageName != unlockedApp
        ) {
            Log.d(
                TAG,
                "CONFIRMED APP SWITCH: " +
                        "$unlockedApp -> $packageName"
            )

            if (
                relockOption ==
                RELOCK_AFTER_QUITTING
            ) {
                scheduleRelock(
                    "CONFIRMED_APP_SWITCH"
                )
            }
        }

        checkAndLockPackage(packageName)
    }

    private fun checkAndLockPackage(
        packageName: String
    ) {
        Log.d(
            TAG,
            "CHECK PACKAGE = $packageName | " +
                    "protection=$appProtectionEnabled | " +
                    "loaded=$settingsLoaded | " +
                    "connected=$serviceConnected"
        )

        if (
            !appProtectionEnabled ||
            !settingsLoaded ||
            !serviceConnected
        ) {
            Log.d(
                TAG,
                "LOCK SKIPPED -> SERVICE NOT READY"
            )
            return
        }

        if (isIgnorablePackage(packageName)) {
            Log.d(
                TAG,
                "LOCK SKIPPED -> IGNORABLE PACKAGE"
            )
            return
        }

        if (isOwnPackage(packageName)) {
            Log.d(
                TAG,
                "LOCK SKIPPED -> OWN APP"
            )
            return
        }

        if (isLauncherPackage(packageName)) {
            Log.d(
                TAG,
                "LOCK SKIPPED -> LAUNCHER"
            )
            return
        }

        if (!lockedAppsCache.contains(packageName)) {
            Log.d(
                TAG,
                "LOCK SKIPPED -> APP NOT LOCKED: $packageName"
            )
            return
        }

        if (AppLockServiceHolder.isLockScreenOpen) {
            Log.d(
                TAG,
                "LOCK SKIPPED -> LOCK SCREEN ALREADY OPEN"
            )
            return
        }

        if (isLockLaunchRecent()) {
            Log.d(
                TAG,
                "LOCK SKIPPED -> RECENT LOCK LAUNCH"
            )
            return
        }

        if (
            AppLockServiceHolder.currentUnlockedApp ==
            packageName
        ) {
            Log.d(
                TAG,
                "APP ALREADY UNLOCKED -> $packageName"
            )
            checkingPackage = null
            return
        }

        if (checkingPackage == packageName) {
            return
        }

        checkingPackage = packageName

        Log.d(TAG, "================================")
        Log.d(
            TAG,
            "LOCKING APP -> $packageName"
        )
        Log.d(TAG, "================================")

        openLockScreen(packageName)
    }

    private fun openLockScreen(
        packageName: String
    ) {
        if (
            !appProtectionEnabled ||
            !settingsLoaded ||
            !serviceConnected
        ) {
            checkingPackage = null
            return
        }

        handler.post {
            try {
                if (
                    AppLockServiceHolder.isLockScreenOpen
                ) {
                    checkingPackage = null
                    return@post
                }

                if (
                    AppLockServiceHolder.currentUnlockedApp ==
                    packageName
                ) {
                    checkingPackage = null
                    return@post
                }

                if (
                    !lockedAppsCache.contains(
                        packageName
                    )
                ) {
                    checkingPackage = null
                    return@post
                }

                val intent =
                    Intent(
                        this@AppLockService,
                        LockScreenActivity::class.java
                    ).apply {
                        putExtra(
                            "packageName",
                            packageName
                        )

                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                        )
                    }

                lockLaunchTime =
                    SystemClock.elapsedRealtime()

                AppLockServiceHolder.isLockScreenOpen =
                    true

                startActivity(intent)

                Log.d(
                    TAG,
                    "LOCK SCREEN OPENED -> $packageName"
                )
            } catch (e: Exception) {
                lockLaunchTime = 0L

                AppLockServiceHolder.isLockScreenOpen =
                    false

                Log.e(
                    TAG,
                    "LOCK SCREEN ERROR",
                    e
                )
            } finally {
                checkingPackage = null
            }
        }
    }

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {
        if (event == null) return

        if (!serviceConnected) {
            return
        }

        if (!settingsLoaded) {
            return
        }

        if (!appProtectionEnabled) {
            return
        }

        val packageName =
            getEventPackage(event)
                ?: return

        Log.d(
            TAG,
            "EVENT type=${event.eventType} " +
                    "package=$packageName"
        )

        if (isOwnPackage(packageName)) {
            return
        }

        if (isIgnorablePackage(packageName)) {
            return
        }

        if (isLauncherPackage(packageName)) {
            pendingPackage = packageName

            handler.removeCallbacks(
                foregroundVerifyRunnable
            )

            handler.postDelayed(
                foregroundVerifyRunnable,
                FOREGROUND_VERIFY_DELAY_MS
            )

            return
        }

        pendingPackage = packageName

        handler.removeCallbacks(
            foregroundVerifyRunnable
        )

        handler.postDelayed(
            foregroundVerifyRunnable,
            FOREGROUND_VERIFY_DELAY_MS
        )
    }

    override fun onInterrupt() {
        Log.d(
            TAG,
            "ACCESSIBILITY SERVICE INTERRUPTED"
        )
    }

    override fun onUnbind(
        intent: Intent?
    ): Boolean {
        Log.d(
            TAG,
            "ACCESSIBILITY SERVICE UNBOUND"
        )

        serviceConnected = false
        settingsLoaded = false
        checkingPackage = null

        return true
    }

    override fun onRebind(
        intent: Intent?
    ) {
        super.onRebind(intent)

        Log.d(
            TAG,
            "ACCESSIBILITY SERVICE REBOUND"
        )

        serviceConnected = true
        settingsLoaded = false
        checkingPackage = null
        foregroundPackage = null
        pendingPackage = null
        lockLaunchTime = 0L

        AppLockServiceHolder.isLockScreenOpen = false

        resolveHomePackage()
        refreshImePackages()
        startForegroundServiceNotification()
        loadCurrentSettings()
    }

    override fun onDestroy() {
        Log.d(
            TAG,
            "ACCESSIBILITY SERVICE DESTROYED"
        )

        serviceConnected = false
        settingsLoaded = false

        handler.removeCallbacks(
            resetUnlockedAppRunnable
        )

        handler.removeCallbacks(
            foregroundVerifyRunnable
        )

        foregroundPackage = null
        pendingPackage = null
        checkingPackage = null
        lockedAppsCache = emptySet()
        lockLaunchTime = 0L

        AppLockServiceHolder.isLockScreenOpen = false

        try {
            unregisterReceiver(
                screenOffReceiver
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "RECEIVER UNREGISTER ERROR",
                e
            )
        }

        serviceScope.cancel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(
                STOP_FOREGROUND_REMOVE
            )
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        super.onDestroy()
    }

    private fun startForegroundServiceNotification() {
        try {
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {
                val channel =
                    NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "AppLock Protection",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description =
                            "Keeps AppLock protection active"

                        setShowBadge(false)
                    }

                getSystemService(
                    NotificationManager::class.java
                ).createNotificationChannel(channel)
            }

            val notification =
                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.O
                ) {
                    Notification.Builder(
                        this,
                        NOTIFICATION_CHANNEL_ID
                    )
                        .setContentTitle(
                            "AppLock is active"
                        )
                        .setContentText(
                            "AppLock protection is running"
                        )
                        .setSmallIcon(
                            applicationInfo.icon
                        )
                        .setOngoing(true)
                        .setAutoCancel(false)
                        .setCategory(
                            Notification.CATEGORY_SERVICE
                        )
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    Notification.Builder(this)
                        .setContentTitle(
                            "AppLock is active"
                        )
                        .setContentText(
                            "AppLock protection is running"
                        )
                        .setSmallIcon(
                            applicationInfo.icon
                        )
                        .setOngoing(true)
                        .setAutoCancel(false)
                        .setCategory(
                            Notification.CATEGORY_SERVICE
                        )
                        .build()
                }

            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(
                    NOTIFICATION_ID,
                    notification
                )
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "FOREGROUND SERVICE ERROR",
                e
            )
        }
    }
}
