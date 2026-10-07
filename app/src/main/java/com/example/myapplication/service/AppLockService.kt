package com.example.myapplication.service

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
import android.util.Log
import android.view.accessibility.AccessibilityEvent
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
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

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
    private var lockedAppsCacheReady = false

    private var foregroundPackage: String? = null
    private var homePackageName: String? = null
    private var checkingPackage: String? = null

    private val resetUnlockedAppRunnable = Runnable {
        Log.d(TAG, "========================================")
        Log.d(TAG, "RELOCK TIMER FIRED")
        Log.d(TAG, "CURRENT UNLOCKED APP = ${AppLockServiceHolder.currentUnlockedApp}")

        AppLockServiceHolder.currentUnlockedApp = null
        AppLockServiceHolder.lastUnlockTime = 0L
        AppLockServiceHolder.clearSuppressedPackage()
        checkingPackage = null

        Log.d(TAG, "UNLOCK STATE CLEARED")
        Log.d(TAG, "========================================")
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            if (!appProtectionEnabled) return

            Log.d(TAG, "SCREEN OFF")

            if (AppLockServiceHolder.currentUnlockedApp != null) {
                if (
                    relockOption == RELOCK_AFTER_SCREEN_OFF ||
                    relockOption == RELOCK_AFTER_QUITTING
                ) {
                    scheduleRelock("SCREEN_OFF")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "========================================")
        Log.d(TAG, "APP LOCK SERVICE CREATED")
        Log.d(TAG, "========================================")

        resolveHomePackage()
        registerScreenOffReceiver()
        observeDataStore()
    }

    private fun registerScreenOffReceiver() {
        try {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    screenOffReceiver,
                    filter,
                    RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(screenOffReceiver, filter)
            }

            Log.d(TAG, "SCREEN OFF RECEIVER REGISTERED")
        } catch (e: Exception) {
            Log.e(TAG, "RECEIVER ERROR", e)
        }
    }

    private fun observeDataStore() {
        val dataStore = DataStoreManager(this)

        serviceScope.launch {
            try {
                dataStore.getAppProtectionEnabled().collectLatest { enabled ->
                    appProtectionEnabled = enabled

                    Log.d(TAG, "APP PROTECTION = $enabled")

                    if (!enabled) {
                        withContext(Dispatchers.Main) {
                            clearProtectionState()
                        }
                    }

                    updateSettingsLoaded()
                }
            } catch (e: Exception) {
                Log.e(TAG, "APP PROTECTION ERROR", e)
            }
        }

        serviceScope.launch {
            try {
                dataStore.lockedAppsFlow.collectLatest { apps ->
                    lockedAppsCache = apps.toSet()
                    lockedAppsCacheReady = true

                    Log.d(TAG, "LOCKED APPS = $lockedAppsCache")

                    updateSettingsLoaded()
                }
            } catch (e: Exception) {
                Log.e(TAG, "LOCKED APPS ERROR", e)
            }
        }

        serviceScope.launch {
            try {
                dataStore.getRelockOption().collectLatest { option ->
                    relockOption = option

                    Log.d(TAG, "RELOCK OPTION = $option")

                    updateSettingsLoaded()
                }
            } catch (e: Exception) {
                Log.e(TAG, "RELOCK OPTION ERROR", e)
            }
        }

        serviceScope.launch {
            try {
                dataStore.getRelockDelay().collectLatest { delay ->
                    relockDelay = normalizeRelockDelay(delay)

                    Log.d(TAG, "RELOCK DELAY = $relockDelay")

                    updateSettingsLoaded()
                }
            } catch (e: Exception) {
                Log.e(TAG, "RELOCK DELAY ERROR", e)
            }
        }
    }

    private fun updateSettingsLoaded() {
        if (lockedAppsCacheReady) {
            settingsLoaded = true
            Log.d(TAG, "SETTINGS READY")
        }
    }

    private fun normalizeRelockDelay(delay: String): String {
        return when (delay) {
            "never" -> "never"
            "five_seconds", "5_seconds" -> "five_seconds"
            "ten_seconds", "10_seconds" -> "ten_seconds"
            "thirty_seconds", "30_seconds" -> "thirty_seconds"
            "one_minute", "1_minute" -> "one_minute"
            "two_minutes", "2_minutes" -> "two_minutes"
            "five_minutes", "5_minutes" -> "five_minutes"
            else -> "never"
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        Log.d(TAG, "========================================")
        Log.d(TAG, "ACCESSIBILITY SERVICE CONNECTED")
        Log.d(TAG, "========================================")

        val info = AccessibilityServiceInfo().apply {
            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOWS_CHANGED

            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }

        serviceInfo = info

        serviceConnected = true
        settingsLoaded = false

        AppLockServiceHolder.isLockScreenOpen = false

        checkingPackage = null
        foregroundPackage = null

        resolveHomePackage()
        startForegroundServiceNotification()
        loadCurrentSettings()

        Log.d(TAG, "SERVICE READY")
    }

    private fun loadCurrentSettings() {
        serviceScope.launch {
            try {
                val dataStore = DataStoreManager(this@AppLockService)

                val protection = dataStore.getAppProtectionEnabled().first()
                val lockedApps = dataStore.lockedAppsFlow.first()
                val option = dataStore.getRelockOption().first()
                val delay = dataStore.getRelockDelay().first()

                appProtectionEnabled = protection
                lockedAppsCache = lockedApps.toSet()
                lockedAppsCacheReady = true
                relockOption = option
                relockDelay = normalizeRelockDelay(delay)
                settingsLoaded = true

                Log.d(TAG, "========================================")
                Log.d(TAG, "CURRENT SETTINGS LOADED")
                Log.d(TAG, "APP PROTECTION = $appProtectionEnabled")
                Log.d(TAG, "LOCKED APPS = $lockedAppsCache")
                Log.d(TAG, "RELOCK OPTION = $relockOption")
                Log.d(TAG, "RELOCK DELAY = $relockDelay")
                Log.d(TAG, "========================================")
            } catch (e: Exception) {
                Log.e(TAG, "INITIAL SETTINGS ERROR", e)
            }
        }
    }

    private fun startForegroundServiceNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "AppLock Protection",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps AppLock protection active"
                    setShowBadge(false)
                }

                getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(channel)
            }

            val notification =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                        .setContentTitle("AppLock is active")
                        .setContentText("AppLock protection is running")
                        .setSmallIcon(applicationInfo.icon)
                        .setOngoing(true)
                        .setAutoCancel(false)
                        .setCategory(Notification.CATEGORY_SERVICE)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    Notification.Builder(this)
                        .setContentTitle("AppLock is active")
                        .setContentText("AppLock protection is running")
                        .setSmallIcon(applicationInfo.icon)
                        .setOngoing(true)
                        .setAutoCancel(false)
                        .setCategory(Notification.CATEGORY_SERVICE)
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
                startForeground(NOTIFICATION_ID, notification)
            }

            Log.d(TAG, "FOREGROUND NOTIFICATION STARTED")
        } catch (e: Exception) {
            Log.e(TAG, "FOREGROUND SERVICE ERROR", e)
        }
    }

    private fun resolveHomePackage() {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }

            val resolveInfo = packageManager.resolveActivity(homeIntent, 0)
            homePackageName = resolveInfo?.activityInfo?.packageName

            Log.d(TAG, "HOME PACKAGE = $homePackageName")
        } catch (e: Exception) {
            Log.e(TAG, "HOME PACKAGE ERROR", e)
        }
    }

    private fun clearProtectionState() {
        handler.removeCallbacks(resetUnlockedAppRunnable)

        AppLockServiceHolder.currentUnlockedApp = null
        AppLockServiceHolder.lastUnlockTime = 0L
        AppLockServiceHolder.isLockScreenOpen = false
        AppLockServiceHolder.clearSuppressedPackage()

        checkingPackage = null
        foregroundPackage = null
    }

    private fun clearUnlockedApp(reason: String) {
        Log.d(TAG, "========================================")
        Log.d(TAG, "CLEAR UNLOCKED APP")
        Log.d(TAG, "REASON = $reason")
        Log.d(TAG, "APP = ${AppLockServiceHolder.currentUnlockedApp}")
        Log.d(TAG, "========================================")

        handler.removeCallbacks(resetUnlockedAppRunnable)

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

    private fun scheduleRelock(reason: String) {
        if (!appProtectionEnabled) return

        val unlockedApp = AppLockServiceHolder.currentUnlockedApp

        if (unlockedApp.isNullOrEmpty()) {
            Log.d(TAG, "RELOCK IGNORED - NO UNLOCKED APP")
            return
        }

        Log.d(TAG, "========================================")
        Log.d(TAG, "SCHEDULE RELOCK")
        Log.d(TAG, "REASON = $reason")
        Log.d(TAG, "APP = $unlockedApp")
        Log.d(TAG, "DELAY = $relockDelay")
        Log.d(TAG, "========================================")

        handler.removeCallbacks(resetUnlockedAppRunnable)

        val delay = getRelockDelayMillis()

        if (delay <= 0L) {
            clearUnlockedApp("IMMEDIATE_$reason")
            return
        }

        handler.postDelayed(resetUnlockedAppRunnable, delay)
    }

    private fun isLauncherPackage(packageName: String): Boolean {
        if (homePackageName != null && packageName == homePackageName) {
            return true
        }

        return packageName.contains("launcher", ignoreCase = true)
    }

    private fun isSystemUiPackage(packageName: String): Boolean {
        return packageName == "com.android.systemui"
    }

    private fun isOwnPackage(packageName: String): Boolean {
        return packageName == applicationContext.packageName
    }

    private fun openLockScreen(packageName: String) {
        if (!appProtectionEnabled || !settingsLoaded) {
            checkingPackage = null
            return
        }

        if (isOwnPackage(packageName)) {
            checkingPackage = null
            return
        }

        if (!lockedAppsCache.contains(packageName)) {
            checkingPackage = null
            return
        }

        if (AppLockServiceHolder.isLockScreenOpen) {
            Log.d(TAG, "LOCK SCREEN ALREADY OPEN")
            checkingPackage = null
            return
        }

        handler.post {
            try {
                if (!appProtectionEnabled) {
                    checkingPackage = null
                    return@post
                }

                if (!lockedAppsCache.contains(packageName)) {
                    checkingPackage = null
                    return@post
                }

                if (AppLockServiceHolder.isLockScreenOpen) {
                    checkingPackage = null
                    return@post
                }

                val lockIntent = Intent(
                    this@AppLockService,
                    LockScreenActivity::class.java
                ).apply {
                    putExtra("packageName", packageName)

                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                }

                Log.d(TAG, "========================================")
                Log.d(TAG, "OPENING LOCK SCREEN")
                Log.d(TAG, "PACKAGE = $packageName")
                Log.d(TAG, "========================================")

                AppLockServiceHolder.isLockScreenOpen = true

                startActivity(lockIntent)
            } catch (e: Exception) {
                AppLockServiceHolder.isLockScreenOpen = false
                Log.e(TAG, "LOCK SCREEN ERROR", e)
            } finally {
                checkingPackage = null
            }
        }
    }

    private fun handleForegroundPackage(packageName: String) {
        val previousPackage = foregroundPackage

        if (previousPackage == packageName) {
            return
        }

        Log.d(TAG, "FOREGROUND CHANGED: $previousPackage -> $packageName")

        val unlockedApp = AppLockServiceHolder.currentUnlockedApp

        if (
            !unlockedApp.isNullOrEmpty() &&
            packageName != unlockedApp &&
            relockOption == RELOCK_AFTER_QUITTING
        ) {
            Log.d(TAG, "UNLOCKED APP LEFT = $unlockedApp")
            Log.d(TAG, "NEW FOREGROUND APP = $packageName")

            clearUnlockedApp("APP_LEFT_$packageName")
        }

        foregroundPackage = packageName
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!serviceConnected) return
        if (!appProtectionEnabled) return
        if (!settingsLoaded) return

        if (
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }

        val packageName = event.packageName?.toString() ?: return

        Log.d(TAG, "----------------------------------------")
        Log.d(TAG, "ACCESSIBILITY EVENT")
        Log.d(TAG, "PACKAGE = $packageName")
        Log.d(
            TAG,
            "CURRENT UNLOCKED APP = ${AppLockServiceHolder.currentUnlockedApp}"
        )
        Log.d(
            TAG,
            "LOCK SCREEN OPEN = ${AppLockServiceHolder.isLockScreenOpen}"
        )
        Log.d(TAG, "FOREGROUND PACKAGE = $foregroundPackage")

        if (isSystemUiPackage(packageName)) {
            Log.d(TAG, "SYSTEM UI EVENT - IGNORED")
            return
        }

        if (isOwnPackage(packageName)) {
            foregroundPackage = packageName
            checkingPackage = null
            return
        }

        if (isLauncherPackage(packageName)) {
            Log.d(TAG, "LAUNCHER DETECTED = $packageName")

            val unlockedApp = AppLockServiceHolder.currentUnlockedApp

            if (
                !unlockedApp.isNullOrEmpty() &&
                relockOption == RELOCK_AFTER_QUITTING
            ) {
                Log.d(TAG, "LOCKED APP CLOSED")
                Log.d(TAG, "CLEARING UNLOCK STATE = $unlockedApp")

                clearUnlockedApp("LAUNCHER_EXIT")
            }

            foregroundPackage = packageName
            checkingPackage = null
            return
        }

        handleForegroundPackage(packageName)

        if (AppLockServiceHolder.isLockScreenOpen) {
            checkingPackage = null
            return
        }

        if (AppLockServiceHolder.currentUnlockedApp == packageName) {
            Log.d(TAG, "APP ALREADY UNLOCKED = $packageName")
            checkingPackage = null
            return
        }

        if (checkingPackage == packageName) {
            Log.d(TAG, "ALREADY CHECKING = $packageName")
            return
        }

        checkingPackage = packageName

        if (lockedAppsCache.contains(packageName)) {
            Log.d(TAG, "========================================")
            Log.d(TAG, "LOCKED APP DETECTED")
            Log.d(TAG, "PACKAGE = $packageName")
            Log.d(TAG, "========================================")

            openLockScreen(packageName)
        } else {
            Log.d(TAG, "APP NOT LOCKED = $packageName")
            checkingPackage = null
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "ACCESSIBILITY SERVICE INTERRUPTED")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "ACCESSIBILITY SERVICE UNBOUND")

        serviceConnected = false
        settingsLoaded = false
        checkingPackage = null

        return true
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)

        Log.d(TAG, "ACCESSIBILITY SERVICE REBOUND")

        serviceConnected = true
        settingsLoaded = false
        checkingPackage = null
        foregroundPackage = null
        lockedAppsCacheReady = false

        AppLockServiceHolder.isLockScreenOpen = false

        resolveHomePackage()
        startForegroundServiceNotification()
        loadCurrentSettings()
    }

    override fun onDestroy() {
        Log.d(TAG, "ACCESSIBILITY SERVICE DESTROYED")

        serviceConnected = false
        settingsLoaded = false

        handler.removeCallbacks(resetUnlockedAppRunnable)

        checkingPackage = null
        foregroundPackage = null

        lockedAppsCache = emptySet()
        lockedAppsCacheReady = false

        AppLockServiceHolder.isLockScreenOpen = false

        try {
            unregisterReceiver(screenOffReceiver)
            Log.d(TAG, "SCREEN OFF RECEIVER UNREGISTERED")
        } catch (e: Exception) {
            Log.e(TAG, "RECEIVER UNREGISTER ERROR", e)
        }

        serviceScope.cancel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        super.onDestroy()
    }
}