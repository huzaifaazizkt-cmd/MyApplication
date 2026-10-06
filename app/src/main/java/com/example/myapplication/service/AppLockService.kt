
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

        private const val TAG =
            "AppLockService"

        private const val RELOCK_AFTER_QUITTING =
            "relock_after_quitting"

        private const val RELOCK_AFTER_SCREEN_OFF =
            "relock_after_screen_off"

        private const val NOTIFICATION_CHANNEL_ID =
            "applock_service_channel"

        private const val NOTIFICATION_ID =
            1001
    }

    // =========================================================
    // COROUTINE
    // =========================================================

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.IO
        )

    // =========================================================
    // HANDLER
    // =========================================================

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    // =========================================================
    // STATE
    // =========================================================

    private var lastPackageName: String? =
        null

    private var checkingPackage: String? =
        null

    /*
     * This represents the last REAL foreground package.
     *
     * IMPORTANT:
     * We do NOT replace this with System UI.
     */
    private var foregroundPackage: String? =
        null

    @Volatile
    private var appProtectionEnabled =
        false

    @Volatile
    private var relockOption =
        RELOCK_AFTER_QUITTING

    @Volatile
    private var relockDelay =
        "never"

    @Volatile
    private var serviceConnected =
        false

    @Volatile
    private var lockedAppsCache:
            Set<String> =
        emptySet()

    @Volatile
    private var lockedAppsCacheReady =
        false

    private var homePackageName:
            String? =
        null

    // =========================================================
    // RESET UNLOCKED APP
    // =========================================================

    private val resetUnlockedAppRunnable =
        Runnable {

            Log.d(
                TAG,
                "========================================"
            )

            Log.d(
                TAG,
                "RELOCK TIMER FIRED"
            )

            Log.d(
                TAG,
                "currentUnlockedApp BEFORE = " +
                        AppLockServiceHolder
                            .currentUnlockedApp
            )

            AppLockServiceHolder
                .currentUnlockedApp =
                null

            AppLockServiceHolder
                .lastUnlockTime =
                0L

            AppLockServiceHolder
                .clearSuppressedPackage()

            checkingPackage =
                null

            Log.d(
                TAG,
                "currentUnlockedApp AFTER = " +
                        AppLockServiceHolder
                            .currentUnlockedApp
            )

            Log.d(
                TAG,
                "========================================"
            )
        }

    // =========================================================
    // SCREEN OFF RECEIVER
    // =========================================================

    private val screenOffReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {

                if (
                    intent?.action !=
                    Intent.ACTION_SCREEN_OFF
                ) {

                    return
                }

                Log.d(
                    TAG,
                    "SCREEN OFF"
                )

                if (
                    !appProtectionEnabled
                ) {

                    return
                }

                if (
                    relockOption ==
                    RELOCK_AFTER_SCREEN_OFF
                ) {

                    if (
                        AppLockServiceHolder
                            .currentUnlockedApp != null
                    ) {

                        scheduleRelock(
                            "SCREEN_OFF"
                        )
                    }
                }

                else if (
                    relockOption ==
                    RELOCK_AFTER_QUITTING
                ) {

                    if (
                        AppLockServiceHolder
                            .currentUnlockedApp != null
                    ) {

                        scheduleRelock(
                            "SCREEN_OFF_QUITTING_MODE"
                        )
                    }
                }
            }
        }

    // =========================================================
    // FOREGROUND NOTIFICATION
    // =========================================================

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

                val manager =
                    getSystemService(
                        NotificationManager::class.java
                    )

                manager.createNotificationChannel(
                    channel
                )
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

                    @Suppress(
                        "DEPRECATION"
                    )

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

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
            ) {

                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo
                        .FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )

            } else {

                @Suppress(
                    "DEPRECATION"
                )

                startForeground(
                    NOTIFICATION_ID,
                    notification
                )
            }

            Log.d(
                TAG,
                "FOREGROUND NOTIFICATION STARTED"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "FOREGROUND SERVICE ERROR",
                e
            )
        }
    }

    // =========================================================
    // HOME / LAUNCHER
    // =========================================================

    private fun resolveHomePackage() {

        try {

            val homeIntent =
                Intent(
                    Intent.ACTION_MAIN
                ).apply {

                    addCategory(
                        Intent.CATEGORY_HOME
                    )
                }

            val resolveInfo =
                packageManager.resolveActivity(
                    homeIntent,
                    0
                )

            homePackageName =
                resolveInfo
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

    // =========================================================
    // CLEAR PROTECTION STATE
    // =========================================================

    private fun clearProtectionState() {

        handler.removeCallbacks(
            resetUnlockedAppRunnable
        )

        AppLockServiceHolder
            .currentUnlockedApp =
            null

        AppLockServiceHolder
            .lastUnlockTime =
            0L

        AppLockServiceHolder
            .isLockScreenOpen =
            false

        AppLockServiceHolder
            .clearSuppressedPackage()

        checkingPackage =
            null

        lastPackageName =
            null

        foregroundPackage =
            null
    }

    // =========================================================
    // CLEAR UNLOCKED APP
    // =========================================================

    private fun clearUnlockedApp(
        reason: String
    ) {

        Log.d(
            TAG,
            "========================================"
        )

        Log.d(
            TAG,
            "CLEAR UNLOCKED APP"
        )

        Log.d(
            TAG,
            "REASON = $reason"
        )

        Log.d(
            TAG,
            "currentUnlockedApp BEFORE = " +
                    AppLockServiceHolder
                        .currentUnlockedApp
        )

        handler.removeCallbacks(
            resetUnlockedAppRunnable
        )

        AppLockServiceHolder
            .currentUnlockedApp =
            null

        AppLockServiceHolder
            .lastUnlockTime =
            0L

        AppLockServiceHolder
            .clearSuppressedPackage()

        checkingPackage =
            null

        Log.d(
            TAG,
            "currentUnlockedApp AFTER = " +
                    AppLockServiceHolder
                        .currentUnlockedApp
        )

        Log.d(
            TAG,
            "========================================"
        )
    }

    // =========================================================
    // SERVICE CREATE
    // =========================================================

    override fun onCreate() {

        super.onCreate()

        Log.d(
            TAG,
            "================================"
        )

        Log.d(
            TAG,
            "APP LOCK SERVICE CREATED"
        )

        Log.d(
            TAG,
            "================================"
        )

        resolveHomePackage()

        // -----------------------------------------------------
        // SCREEN OFF RECEIVER
        // -----------------------------------------------------

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

                @Suppress(
                    "DEPRECATION"
                )

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

        // -----------------------------------------------------
        // APP PROTECTION
        // -----------------------------------------------------

        serviceScope.launch {

            try {

                DataStoreManager(
                    this@AppLockService
                )
                    .getAppProtectionEnabled()
                    .collectLatest { enabled ->

                        appProtectionEnabled =
                            enabled

                        Log.d(
                            TAG,
                            "APP PROTECTION = $enabled"
                        )

                        if (!enabled) {

                            withContext(
                                Dispatchers.Main
                            ) {

                                clearProtectionState()
                            }
                        }
                    }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "APP PROTECTION ERROR",
                    e
                )
            }
        }

        // -----------------------------------------------------
        // LOCKED APPS
        // -----------------------------------------------------

        serviceScope.launch {

            try {

                DataStoreManager(
                    this@AppLockService
                )
                    .lockedAppsFlow
                    .collectLatest { apps ->

                        lockedAppsCache =
                            apps.toSet()

                        lockedAppsCacheReady =
                            true

                        Log.d(
                            TAG,
                            "LOCKED APPS = $lockedAppsCache"
                        )
                    }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "LOCKED APPS ERROR",
                    e
                )
            }
        }

        // -----------------------------------------------------
        // RELOCK OPTION
        // -----------------------------------------------------

        serviceScope.launch {

            try {

                DataStoreManager(
                    this@AppLockService
                )
                    .getRelockOption()
                    .collectLatest { option ->

                        relockOption =
                            option

                        Log.d(
                            TAG,
                            "RELOCK OPTION = $option"
                        )
                    }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "RELOCK OPTION ERROR",
                    e
                )
            }
        }

        // -----------------------------------------------------
        // RELOCK DELAY
        // -----------------------------------------------------

        serviceScope.launch {

            try {

                DataStoreManager(
                    this@AppLockService
                )
                    .getRelockDelay()
                    .collectLatest { delay ->

                        relockDelay =
                            when (delay) {

                                "never" ->
                                    "never"

                                "five_seconds",
                                "5_seconds" ->
                                    "five_seconds"

                                "ten_seconds",
                                "10_seconds" ->
                                    "ten_seconds"

                                "thirty_seconds",
                                "30_seconds" ->
                                    "thirty_seconds"

                                "one_minute",
                                "1_minute" ->
                                    "one_minute"

                                "two_minutes",
                                "2_minutes" ->
                                    "two_minutes"

                                "five_minutes",
                                "5_minutes" ->
                                    "five_minutes"

                                else ->
                                    "never"
                            }

                        Log.d(
                            TAG,
                            "RELOCK DELAY = $relockDelay"
                        )
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

    // =========================================================
    // ACCESSIBILITY SERVICE CONNECTED
    // =========================================================

    override fun onServiceConnected() {

        super.onServiceConnected()

        Log.d(
            TAG,
            "========================================"
        )

        Log.d(
            TAG,
            "ACCESSIBILITY SERVICE CONNECTED"
        )

        Log.d(
            TAG,
            "========================================"
        )

        val info =
            AccessibilityServiceInfo().apply {

                eventTypes =
                    AccessibilityEvent
                        .TYPE_WINDOW_STATE_CHANGED or
                            AccessibilityEvent
                                .TYPE_WINDOWS_CHANGED

                feedbackType =
                    AccessibilityServiceInfo
                        .FEEDBACK_GENERIC

                notificationTimeout =
                    50

                flags =
                    AccessibilityServiceInfo
                        .FLAG_REPORT_VIEW_IDS
            }

        serviceInfo =
            info

        serviceConnected =
            true

        startForegroundServiceNotification()

        handler.removeCallbacks(
            resetUnlockedAppRunnable
        )

        AppLockServiceHolder
            .isLockScreenOpen =
            false

        checkingPackage =
            null

        lastPackageName =
            null

        foregroundPackage =
            null

        resolveHomePackage()

        Log.d(
            TAG,
            "SERVICE READY"
        )
    }

    // =========================================================
    // RELOCK DELAY
    // =========================================================

    private fun getRelockDelayMillis():
            Long {

        return when (relockDelay) {

            "never" ->
                0L

            "five_seconds",
            "5_seconds" ->
                5_000L

            "ten_seconds",
            "10_seconds" ->
                10_000L

            "thirty_seconds",
            "30_seconds" ->
                30_000L

            "one_minute",
            "1_minute" ->
                60_000L

            "two_minutes",
            "2_minutes" ->
                120_000L

            "five_minutes",
            "5_minutes" ->
                300_000L

            else ->
                0L
        }
    }

    // =========================================================
    // SCHEDULE RELOCK
    // =========================================================

    private fun scheduleRelock(
        reason: String
    ) {

        if (
            !appProtectionEnabled
        ) {

            return
        }

        val unlockedApp =
            AppLockServiceHolder
                .currentUnlockedApp

        /*
         * Nothing is currently unlocked.
         */
        if (
            unlockedApp.isNullOrEmpty()
        ) {

            Log.d(
                TAG,
                "RELOCK IGNORED - NO UNLOCKED APP"
            )

            return
        }

        Log.d(
            TAG,
            "========================================"
        )

        Log.d(
            TAG,
            "SCHEDULE RELOCK"
        )

        Log.d(
            TAG,
            "REASON = $reason"
        )

        Log.d(
            TAG,
            "UNLOCKED APP = $unlockedApp"
        )

        Log.d(
            TAG,
            "RELOCK OPTION = $relockOption"
        )

        Log.d(
            TAG,
            "RELOCK DELAY = $relockDelay"
        )

        handler.removeCallbacks(
            resetUnlockedAppRunnable
        )

        val delayMillis =
            getRelockDelayMillis()

        /*
         * Never:
         *
         * No timer.
         *
         * The important part is that SYSTEM_UI does not call
         * this function anymore.
         */
        if (
            delayMillis <= 0L
        ) {

            clearUnlockedApp(
                "IMMEDIATE_$reason"
            )

            return
        }

        handler.postDelayed(
            resetUnlockedAppRunnable,
            delayMillis
        )

        Log.d(
            TAG,
            "RELOCK TIMER = $delayMillis ms"
        )

        Log.d(
            TAG,
            "========================================"
        )
    }

    // =========================================================
    // LAUNCHER CHECK
    // =========================================================

    private fun isLauncherPackage(
        packageName: String
    ): Boolean {

        if (
            homePackageName != null &&
            packageName ==
            homePackageName
        ) {

            return true
        }

        return packageName.contains(
            "launcher",
            ignoreCase = true
        )
    }

    // =========================================================
    // SYSTEM UI CHECK
    // =========================================================

    private fun isSystemUiPackage(
        packageName: String
    ): Boolean {

        return packageName ==
                "com.android.systemui"
    }

    // =========================================================
    // OPEN LOCK SCREEN
    // =========================================================

    private fun openLockScreen(
        packageName: String
    ) {

        if (
            !appProtectionEnabled
        ) {

            checkingPackage =
                null

            return
        }

        if (
            packageName ==
            applicationContext.packageName
        ) {

            checkingPackage =
                null

            return
        }

        if (
            AppLockServiceHolder
                .isLockScreenOpen
        ) {

            Log.d(
                TAG,
                "LOCK SCREEN ALREADY OPEN"
            )

            checkingPackage =
                null

            return
        }

        handler.post {

            try {

                if (
                    !appProtectionEnabled
                ) {

                    checkingPackage =
                        null

                    return@post
                }

                if (
                    AppLockServiceHolder
                        .isLockScreenOpen
                ) {

                    checkingPackage =
                        null

                    return@post
                }

                /*
                 * IMPORTANT:
                 *
                 * Set this BEFORE startActivity().
                 */

                AppLockServiceHolder
                    .isLockScreenOpen =
                    true

                val lockIntent =
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
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                        )
                    }

                Log.d(
                    TAG,
                    "========================================"
                )

                Log.d(
                    TAG,
                    "OPENING LOCK SCREEN"
                )

                Log.d(
                    TAG,
                    "PACKAGE = $packageName"
                )

                Log.d(
                    TAG,
                    "currentUnlockedApp = " +
                            AppLockServiceHolder
                                .currentUnlockedApp
                )

                Log.d(
                    TAG,
                    "========================================"
                )

                startActivity(
                    lockIntent
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "LOCK SCREEN ERROR",
                    e
                )

                AppLockServiceHolder
                    .isLockScreenOpen =
                    false

            } finally {

                checkingPackage =
                    null
            }
        }
    }

    // =========================================================
    // HANDLE FOREGROUND PACKAGE
    // =========================================================

    private fun handleForegroundPackage(
        packageName: String
    ) {

        val previousPackage =
            foregroundPackage

        /*
         * Same package = no real foreground change.
         */

        if (
            previousPackage ==
            packageName
        ) {

            return
        }

        Log.d(
            TAG,
            "FOREGROUND CHANGED: " +
                    "$previousPackage -> $packageName"
        )

        /*
         * Check whether an unlocked protected app was
         * actually left.
         */

        val unlockedApp =
            AppLockServiceHolder
                .currentUnlockedApp

        if (
            !unlockedApp.isNullOrEmpty() &&
            previousPackage ==
            unlockedApp &&
            packageName != unlockedApp
        ) {

            if (
                relockOption ==
                RELOCK_AFTER_QUITTING
            ) {

                Log.d(
                    TAG,
                    "UNLOCKED APP LEFT"
                )

                Log.d(
                    TAG,
                    "LEFT APP = $unlockedApp"
                )

                Log.d(
                    TAG,
                    "NEW APP = $packageName"
                )

                scheduleRelock(
                    "APP_LEFT_$packageName"
                )
            }
        }

        /*
         * Now store the REAL foreground package.
         */
        foregroundPackage =
            packageName
    }

    // =========================================================
    // ACCESSIBILITY EVENTS
    // =========================================================

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {

        if (
            !appProtectionEnabled ||
            !serviceConnected ||
            event == null
        ) {

            return
        }

        if (
            event.eventType !=
            AccessibilityEvent
                .TYPE_WINDOW_STATE_CHANGED &&
            event.eventType !=
            AccessibilityEvent
                .TYPE_WINDOWS_CHANGED
        ) {

            return
        }

        val packageName =
            event.packageName
                ?.toString()
                ?: return

        Log.d(
            TAG,
            "----------------------------------------"
        )

        Log.d(
            TAG,
            "ACCESSIBILITY EVENT"
        )

        Log.d(
            TAG,
            "PACKAGE = $packageName"
        )

        Log.d(
            TAG,
            "currentUnlockedApp = " +
                    AppLockServiceHolder
                        .currentUnlockedApp
        )

        Log.d(
            TAG,
            "isLockScreenOpen = " +
                    AppLockServiceHolder
                        .isLockScreenOpen
        )

        Log.d(
            TAG,
            "foregroundPackage = $foregroundPackage"
        )

        // =====================================================
        // SYSTEM UI
        // =====================================================

        if (
            isSystemUiPackage(
                packageName
            )
        ) {

            /*
             * IMPORTANT FIX
             *
             * System UI is often emitted while:
             *
             * - opening an app
             * - closing an activity
             * - opening recent apps
             * - showing transitions
             * - displaying system dialogs
             *
             * Therefore DO NOT clear currentUnlockedApp here.
             *
             * Also DO NOT replace foregroundPackage with
             * System UI.
             *
             * We wait for the next REAL package.
             */

            Log.d(
                TAG,
                "SYSTEM UI EVENT - IGNORED"
            )

            Log.d(
                TAG,
                "KEEPING currentUnlockedApp = " +
                        AppLockServiceHolder
                            .currentUnlockedApp
            )

            Log.d(
                TAG,
                "KEEPING foregroundPackage = " +
                        foregroundPackage
            )

            checkingPackage =
                null

            return
        }

        // =====================================================
        // LAUNCHER / HOME
        // =====================================================

        if (
            isLauncherPackage(
                packageName
            )
        ) {

            Log.d(
                TAG,
                "LAUNCHER DETECTED = $packageName"
            )

            val unlockedApp =
                AppLockServiceHolder
                    .currentUnlockedApp

            /*
             * Launcher is a REAL foreground destination.
             *
             * If user left unlocked app and went Home,
             * relock according to quitting mode.
             */

            if (
                !unlockedApp.isNullOrEmpty() &&
                foregroundPackage ==
                unlockedApp &&
                relockOption ==
                RELOCK_AFTER_QUITTING
            ) {

                Log.d(
                    TAG,
                    "UNLOCKED APP LEFT -> LAUNCHER"
                )

                scheduleRelock(
                    "LAUNCHER"
                )
            }

            foregroundPackage =
                packageName

            lastPackageName =
                packageName

            checkingPackage =
                null

            return
        }

        // =====================================================
        // OUR APP
        // =====================================================

        if (
            packageName ==
            applicationContext.packageName
        ) {

            /*
             * Our own app can generate accessibility events
             * while LockScreenActivity is visible.
             *
             * Do not clear currentUnlockedApp here.
             */

            foregroundPackage =
                packageName

            lastPackageName =
                packageName

            checkingPackage =
                null

            return
        }

        // =====================================================
        // REAL FOREGROUND PACKAGE
        // =====================================================

        handleForegroundPackage(
            packageName
        )

        // =====================================================
        // LOCK SCREEN ALREADY OPEN
        // =====================================================

        if (
            AppLockServiceHolder
                .isLockScreenOpen
        ) {

            checkingPackage =
                null

            return
        }

        // =====================================================
        // ALREADY UNLOCKED
        // =====================================================

        if (
            AppLockServiceHolder
                .currentUnlockedApp ==
            packageName
        ) {

            /*
             * IMPORTANT:
             *
             * The app that was just unlocked is still allowed.
             *
             * Never open LockScreenActivity again for it.
             */

            Log.d(
                TAG,
                "APP ALREADY UNLOCKED = $packageName"
            )

            lastPackageName =
                packageName

            checkingPackage =
                null

            return
        }

        // =====================================================
        // ALREADY CHECKING
        // =====================================================

        if (
            checkingPackage ==
            packageName
        ) {

            Log.d(
                TAG,
                "ALREADY CHECKING = $packageName"
            )

            return
        }

        checkingPackage =
            packageName

        lastPackageName =
            packageName

        // =====================================================
        // LOCKED APPS CACHE
        // =====================================================

        if (
            lockedAppsCacheReady
        ) {

            if (
                lockedAppsCache.contains(
                    packageName
                )
            ) {

                Log.d(
                    TAG,
                    "LOCKED APP FOUND = $packageName"
                )

                openLockScreen(
                    packageName
                )

            } else {

                Log.d(
                    TAG,
                    "APP NOT LOCKED = $packageName"
                )

                checkingPackage =
                    null
            }

            return
        }

        // =====================================================
        // FIRST LOAD
        // =====================================================

        serviceScope.launch {

            try {

                val lockedApps =
                    DataStoreManager(
                        this@AppLockService
                    )
                        .lockedAppsFlow
                        .first()

                lockedAppsCache =
                    lockedApps.toSet()

                lockedAppsCacheReady =
                    true

                Log.d(
                    TAG,
                    "LOADED LOCKED APPS = $lockedAppsCache"
                )

                if (
                    lockedApps.contains(
                        packageName
                    )
                ) {

                    withContext(
                        Dispatchers.Main
                    ) {

                        openLockScreen(
                            packageName
                        )
                    }

                } else {

                    checkingPackage =
                        null
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "CHECK LOCKED APP ERROR",
                    e
                )

                checkingPackage =
                    null
            }
        }
    }

    // =========================================================
    // INTERRUPT
    // =========================================================

    override fun onInterrupt() {

        Log.d(
            TAG,
            "ACCESSIBILITY SERVICE INTERRUPTED"
        )
    }

    // =========================================================
    // UNBIND
    // =========================================================

    override fun onUnbind(
        intent: Intent?
    ): Boolean {

        Log.d(
            TAG,
            "ACCESSIBILITY SERVICE UNBOUND"
        )

        serviceConnected =
            false

        checkingPackage =
            null

        return true
    }

    // =========================================================
    // REBIND
    // =========================================================

    override fun onRebind(
        intent: Intent?
    ) {

        super.onRebind(
            intent
        )

        Log.d(
            TAG,
            "ACCESSIBILITY SERVICE REBOUND"
        )

        serviceConnected =
            true

        checkingPackage =
            null

        lastPackageName =
            null

        foregroundPackage =
            null

        lockedAppsCacheReady =
            false

        AppLockServiceHolder
            .isLockScreenOpen =
            false

        resolveHomePackage()

        startForegroundServiceNotification()
    }

    // =========================================================
    // DESTROY
    // =========================================================

    override fun onDestroy() {

        Log.d(
            TAG,
            "ACCESSIBILITY SERVICE DESTROYED"
        )

        serviceConnected =
            false

        handler.removeCallbacks(
            resetUnlockedAppRunnable
        )

        checkingPackage =
            null

        lastPackageName =
            null

        foregroundPackage =
            null

        lockedAppsCache =
            emptySet()

        lockedAppsCacheReady =
            false

        AppLockServiceHolder
            .isLockScreenOpen =
            false

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

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.N
        ) {

            stopForeground(
                STOP_FOREGROUND_REMOVE
            )

        } else {

            @Suppress(
                "DEPRECATION"
            )

            stopForeground(true)
        }

        super.onDestroy()
    }
}

