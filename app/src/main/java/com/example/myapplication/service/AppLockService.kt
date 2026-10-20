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
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
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

        // Lock screen launch hone ke dauran duplicate events ko rokne ka guard
        private const val LOCK_LAUNCH_GUARD_MS = 3000L
        private const val FOREGROUND_VERIFY_DELAY_MS = 0L

        // Cover overlay: lock screen ready hone tak locked app ko chhupata hai
        private const val COVER_TIMEOUT_MS = 3500L   // safety: cover kabhi atka na rahe (slow phones ke liye zyada)
        private const val CLICK_COVER_TIMEOUT_MS = 1500L // icon tap par lagne wale cover ka chhota timeout

        // Cover (white parda) band hai. Dobara chahiye to true kar dein.
        private const val USE_COVER = false
        private const val COVER_HIDE_DELAY_MS = 150L // lock screen ka pehla frame draw hone ka waqt

        // FIX: Unlock screen ka background ab WHITE hai, to cover bhi WHITE hona chahiye.
        // Pehle yahan dark colour tha, isi wajah se pehli dafa "black screen" nazar aati thi.
        private val COVER_COLOR = Color.WHITE
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
    private var lockLaunchTime = 0L

    private var foregroundPackage: String? = null
    private var pendingPackage: String? = null
    private var homePackageName: String? = null
    private var checkingPackage: String? = null
    private var imePackages: Set<String> = emptySet()
    private var receiverRegistered = false
    private var verifyRetries = 0
    private var launchingPackage: String? = null
    private var coverView: View? = null
    @Volatile
    private var lockedLabelToPackage: Map<String, String> = emptyMap()
    private var clickCoverUntil = 0L
    private val windowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val hideCoverRunnable = Runnable { hideCover("LOCK SCREEN READY") }
    private val coverTimeoutRunnable = Runnable { hideCover("TIMEOUT") }

    private val foregroundVerifyRunnable = Runnable { verifyForegroundPackage() }
    private val resetUnlockedAppRunnable = Runnable { clearUnlockedApp("RELOCK_TIMER") }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            if (!appProtectionEnabled) return

            // Screen off par lock screen khuli reh jaye to flag stale ho sakta hai
            if (relockOption == RELOCK_AFTER_SCREEN_OFF &&
                AppLockServiceHolder.currentUnlockedApp != null
            ) {
                scheduleRelock("SCREEN_OFF")
            }
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "APP LOCK SERVICE CREATED")
        resolveHomePackage()
        refreshImePackages()
        registerScreenOffReceiver()
        observeDataStore()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "ACCESSIBILITY SERVICE CONNECTED")

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 0
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }

        // LockScreenActivity jab UnlockScreen draw kar leti hai to ye callback chalta hai
        AppLockServiceHolder.onLockScreenReady = {
            handler.post {
                Log.d(TAG, "UNLOCK UI READY CALLBACK")
                // launchingPackage jaan bujh kar null nahi kiya: locked app ke baad ke
                // events (jo lock screen ke peeche aate hain) guard se rukte rahen.
                hideCover("ACTIVITY READY")
            }
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

    override fun onInterrupt() {
        // Nothing to do
    }

    override fun onUnbind(intent: Intent?): Boolean {
        serviceConnected = false
        hideCover("UNBIND")
        AppLockServiceHolder.onLockScreenReady = null
        handler.removeCallbacksAndMessages(null)
        AppLockServiceHolder.clear()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        serviceConnected = false
        hideCover("DESTROY")
        AppLockServiceHolder.onLockScreenReady = null
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) {
            try {
                unregisterReceiver(screenOffReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
        AppLockServiceHolder.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    private fun observeDataStore() {
        val dataStore = DataStoreManager(this)

        serviceScope.launch {
            try {
                dataStore.getAppProtectionEnabled().collectLatest { enabled ->
                    appProtectionEnabled = enabled
                    Log.d(TAG, "APP PROTECTION = $enabled")
                    if (!enabled) {
                        withContext(Dispatchers.Main) { clearProtectionState() }
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
                    refreshLockedLabels()
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
        if (lockedAppsCache.isNotEmpty() || settingsLoaded) {
            settingsLoaded = true
        }
    }

    private fun loadCurrentSettings() {
        serviceScope.launch {
            try {
                val dataStore = DataStoreManager(this@AppLockService)

                appProtectionEnabled = dataStore.getAppProtectionEnabled().first()
                lockedAppsCache = dataStore.lockedAppsFlow.first().toSet()
                refreshLockedLabels()
                relockOption = dataStore.getRelockOption().first()
                relockDelay = normalizeRelockDelay(dataStore.getRelockDelay().first())
                settingsLoaded = true

                Log.d(TAG, "CURRENT SETTINGS LOADED")
                Log.d(TAG, "PROTECTION = $appProtectionEnabled")
                Log.d(TAG, "LOCKED APPS = $lockedAppsCache")
                Log.d(TAG, "RELOCK OPTION = $relockOption")
                Log.d(TAG, "RELOCK DELAY = $relockDelay")
                Log.d(TAG, "SETTINGS LOADED = $settingsLoaded")
            } catch (e: Exception) {
                Log.e(TAG, "INITIAL SETTINGS ERROR", e)
            }
        }
    }

    private fun normalizeRelockDelay(delay: String): String {
        return when (delay) {
            "five_seconds", "5_seconds" -> "five_seconds"
            "ten_seconds", "10_seconds" -> "ten_seconds"
            "thirty_seconds", "30_seconds" -> "thirty_seconds"
            "one_minute", "1_minute" -> "one_minute"
            "two_minutes", "2_minutes" -> "two_minutes"
            "five_minutes", "5_minutes" -> "five_minutes"
            else -> "never"
        }
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

    // ------------------------------------------------------------------
    // Package helpers
    // ------------------------------------------------------------------

    private fun resolveHomePackage() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            homePackageName = packageManager
                .resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
            Log.d(TAG, "HOME PACKAGE = $homePackageName")
        } catch (e: Exception) {
            Log.e(TAG, "HOME PACKAGE ERROR", e)
        }
    }

    private fun refreshImePackages() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imePackages = imm.enabledInputMethodList.map { it.packageName }.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "IME PACKAGES ERROR", e)
        }
    }

    private fun isOwnPackage(packageName: String) = packageName == this.packageName

    private fun isLauncherPackage(packageName: String): Boolean {
        return packageName == homePackageName
    }

    private fun isIgnorablePackage(packageName: String): Boolean {
        if (packageName in imePackages) return true
        return packageName == "com.android.systemui" ||
                packageName == "android" ||
                packageName == "com.google.android.permissioncontroller" ||
                packageName == "com.android.permissioncontroller" ||
                packageName == "com.google.android.packageinstaller" ||
                packageName == "com.android.packageinstaller"
    }

    private fun getEventPackage(event: AccessibilityEvent): String? {
        val fromEvent = event.packageName?.toString()
        if (!fromEvent.isNullOrEmpty()) return fromEvent
        return try {
            rootInActiveWindow?.packageName?.toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun getVerifiedForegroundPackage(): String? {
        return try {
            val wins = windows
            val focused = wins.firstOrNull { it.isFocused }?.root?.packageName?.toString()
            if (!focused.isNullOrEmpty()) return focused

            val active = wins.firstOrNull { it.isActive }?.root?.packageName?.toString()
            if (!active.isNullOrEmpty()) return active

            rootInActiveWindow?.packageName?.toString()
        } catch (e: Exception) {
            null
        }
    }

    // Locked apps ke naam (label) -> package, taake launcher click pehchana ja sake
    private fun refreshLockedLabels() {
        try {
            val map = HashMap<String, String>()
            for (pkg in lockedAppsCache) {
                try {
                    val info = packageManager.getApplicationInfo(pkg, 0)
                    val label = packageManager.getApplicationLabel(info).toString()
                        .trim().lowercase()
                    if (label.isNotEmpty()) map[label] = pkg
                } catch (_: Exception) {
                }
            }
            lockedLabelToPackage = map
            Log.d(TAG, "LOCKED LABELS = ${map.keys}")
        } catch (e: Exception) {
            Log.e(TAG, "LOCKED LABELS ERROR", e)
        }
    }

    private fun handleLauncherClick(event: AccessibilityEvent, packageName: String) {
        if (!serviceConnected || !settingsLoaded || !appProtectionEnabled) return
        if (!isLauncherPackage(packageName)) return
        if (lockedLabelToPackage.isEmpty()) return

        val candidates = mutableListOf<String>()
        try {
            event.text?.forEach { candidates.add(it.toString()) }
            event.contentDescription?.let { candidates.add(it.toString()) }
            event.source?.let { src ->
                src.text?.let { candidates.add(it.toString()) }
                src.contentDescription?.let { candidates.add(it.toString()) }
            }
        } catch (_: Exception) {
        }

        for (candidate in candidates) {
            val pkg = lockedLabelToPackage[candidate.trim().lowercase()] ?: continue
            if (AppLockServiceHolder.currentUnlockedApp == pkg) return

            Log.d(TAG, "LAUNCHER CLICK ON LOCKED APP -> $pkg")
            if (USE_COVER) {
                clickCoverUntil = SystemClock.elapsedRealtime() + CLICK_COVER_TIMEOUT_MS
                showCover(CLICK_COVER_TIMEOUT_MS)
            }
            // Icon tap par hi lock screen kholo (app ki window aane ka intezar nahi)
            checkingPackage = pkg
            openLockScreen(pkg)
            return
        }
    }

    // Kya is package ki window abhi screen par maujood hai?
    // (App khul rahi ho to window list mein hoti hai, band ho rahi ho to nahi.)
    private fun isPackageWindowPresent(packageName: String): Boolean {
        return try {
            windows.any { it.root?.packageName?.toString() == packageName }
        } catch (e: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !serviceConnected || !settingsLoaded || !appProtectionEnabled) return

        val packageName = getEventPackage(event) ?: return

        // Launcher mein locked app ke icon par tap: launch animation shuru hone se pehle cover lagao
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            handleLauncherClick(event, packageName)
            return
        }

        // Lock screen launch ho rahi hai: usi app ke baar baar aane wale events skip karo
        if (!isOwnPackage(packageName) &&
            packageName == launchingPackage &&
            AppLockServiceHolder.isLockScreenOpen &&
            SystemClock.elapsedRealtime() - lockLaunchTime < LOCK_LAUNCH_GUARD_MS
        ) return

        Log.d(TAG, "EVENT type=${event.eventType} package=$packageName")

        if (isOwnPackage(packageName)) {
            // Apni lock screen ka event: foreground verify ke liye bhi chalne do
            pendingPackage = packageName
            handler.removeCallbacks(foregroundVerifyRunnable)
            handler.postDelayed(foregroundVerifyRunnable, FOREGROUND_VERIFY_DELAY_MS)
            return
        }
        if (isIgnorablePackage(packageName)) return

        // FIX (preview band): locked app ka window event aate hi sab se pehle cover lagao.
        // Pehle cover verify ke baad lagta tha, aur verify mein jitna waqt lagta tha utni
        // der app ka content nazar aata tha. Agar baad mein pata chale ke ye app
        // asal mein foreground par nahi, to cover khud hat jata hai.
        if ((event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) &&
            lockedAppsCache.contains(packageName) &&
            !AppLockServiceHolder.isLockScreenOpen &&
            AppLockServiceHolder.currentUnlockedApp != packageName
        ) {
            showCover()
        }

        pendingPackage = packageName
        verifyRetries = 0
        handler.removeCallbacks(foregroundVerifyRunnable)
        // foran verify, handler ke tick ka intezar nahi
        verifyForegroundPackage()
    }

    private fun verifyForegroundPackage() {
        if (!serviceConnected || !settingsLoaded || !appProtectionEnabled) return

        val eventPackage = pendingPackage
        val verifiedPackage = getVerifiedForegroundPackage() ?: eventPackage
        pendingPackage = null

        if (verifiedPackage.isNullOrEmpty()) return

        Log.d(TAG, "VERIFIED FOREGROUND = $verifiedPackage")

        // Locked app ka event aa gaya lekin window focus abhi purani app par hai:
        // 30 ms baad dobara check karo (max 3 baar), taake lock miss na ho.
        if (eventPackage != null &&
            eventPackage != verifiedPackage &&
            lockedAppsCache.contains(eventPackage) &&
            AppLockServiceHolder.currentUnlockedApp != eventPackage &&
            verifyRetries < 5 &&
            isPackageWindowPresent(eventPackage)
        ) {
            verifyRetries++
            pendingPackage = eventPackage
            handler.removeCallbacks(foregroundVerifyRunnable)
            handler.postDelayed(foregroundVerifyRunnable, 30L)
        }

        handleConfirmedForeground(verifiedPackage)
    }

    private fun handleConfirmedForeground(packageName: String) {
        if (isOwnPackage(packageName)) {
            Log.d(TAG, "CONFIRMED OWN APP -> $packageName")
            if (coverView != null) {
                handler.removeCallbacks(hideCoverRunnable)
                handler.postDelayed(hideCoverRunnable, COVER_HIDE_DELAY_MS)
            }
            return
        }
        if (isIgnorablePackage(packageName)) return

        if (isLauncherPackage(packageName)) {
            Log.d(TAG, "CONFIRMED HOME / LAUNCHER")
            // Agar locked app ka retry pending hai (window abhi focus nahi hui),
            // to cover mat hatao, warna app ka content nazar aa jata hai.
            if (pendingPackage == null &&
                SystemClock.elapsedRealtime() > clickCoverUntil
            ) hideCover("HOME")
            foregroundPackage = packageName
            checkingPackage = null

            // User Home par aa gaya hai, to koi lock screen samne nahi hai.
            // Stale flag reset karo, warna agli dafa lock screen nahi aati.
            AppLockServiceHolder.isLockScreenOpen = false

            if (AppLockServiceHolder.currentUnlockedApp != null &&
                relockOption == RELOCK_AFTER_QUITTING
            ) {
                scheduleRelock("HOME")
            }
            return
        }

        val previousPackage = foregroundPackage

        if (previousPackage == packageName) {
            checkAndLockPackage(packageName)
            return
        }

        foregroundPackage = packageName
        val unlockedApp = AppLockServiceHolder.currentUnlockedApp

        if (!unlockedApp.isNullOrEmpty() &&
            previousPackage == unlockedApp &&
            packageName != unlockedApp
        ) {
            if (relockOption == RELOCK_AFTER_QUITTING) {
                scheduleRelock("CONFIRMED_APP_SWITCH")
            }
        }

        checkAndLockPackage(packageName)
    }

    // ------------------------------------------------------------------
    // Locking
    // ------------------------------------------------------------------

    private fun checkAndLockPackage(packageName: String) {
        Log.d(
            TAG,
            "CHECK PACKAGE = $packageName | protection=$appProtectionEnabled | " +
                    "loaded=$settingsLoaded | connected=$serviceConnected"
        )

        if (!appProtectionEnabled || !settingsLoaded || !serviceConnected) {
            Log.d(TAG, "LOCK SKIPPED -> SERVICE NOT READY")
            return
        }

        if (isOwnPackage(packageName) || isIgnorablePackage(packageName)) return

        if (isLauncherPackage(packageName)) {
            checkingPackage = null
            return
        }

        if (AppLockServiceHolder.currentUnlockedApp == packageName) {
            checkingPackage = null
            hideCover("ALREADY UNLOCKED")
            Log.d(TAG, "LOCK SKIPPED -> ALREADY UNLOCKED: $packageName")
            return
        }

        if (!lockedAppsCache.contains(packageName)) {
            // Cover galti se laga ho (foreground kisi aur app ka tha) to hata do
            hideCover("APP NOT LOCKED")
            Log.d(TAG, "LOCK SKIPPED -> APP NOT LOCKED: $packageName")
            return
        }

        // Stale isLockScreenOpen flag.
        // Yahan tak sirf third-party app ka package aata hai (own app / launcher upar
        // filter ho chuke). Yani lock screen asal mein samne nahi hai.
        if (AppLockServiceHolder.isLockScreenOpen) {
            val recentlyLaunched =
                SystemClock.elapsedRealtime() - lockLaunchTime < LOCK_LAUNCH_GUARD_MS
            if (recentlyLaunched) {
                Log.d(TAG, "LOCK SKIPPED -> LOCK SCREEN LAUNCHING")
                return
            }
            Log.d(TAG, "STALE isLockScreenOpen RESET")
            AppLockServiceHolder.isLockScreenOpen = false
        }

        if (checkingPackage == packageName) {
            Log.d(TAG, "LOCK SKIPPED -> ALREADY CHECKING: $packageName")
            return
        }

        Log.d(TAG, "LOCKING APP -> $packageName")
        showCover()
        checkingPackage = packageName
        openLockScreen(packageName)
    }

    private fun openLockScreen(packageName: String) {
        if (!appProtectionEnabled || !settingsLoaded || !serviceConnected) {
            checkingPackage = null
            return
        }

        run {
            try {
                if (AppLockServiceHolder.isLockScreenOpen &&
                    SystemClock.elapsedRealtime() - lockLaunchTime < LOCK_LAUNCH_GUARD_MS
                ) {
                    return@run
                }

                if (AppLockServiceHolder.currentUnlockedApp == packageName) {
                    return@run
                }

                if (!lockedAppsCache.contains(packageName)) {
                    return@run
                }

                val intent = Intent(this@AppLockService, LockScreenActivity::class.java).apply {
                    putExtra("packageName", packageName)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION
                    )
                }

                lockLaunchTime = SystemClock.elapsedRealtime()
                launchingPackage = packageName
                AppLockServiceHolder.isLockScreenOpen = true
                startActivity(intent)
                Log.d(TAG, "LOCK SCREEN OPENED -> $packageName")
            } catch (e: Exception) {
                lockLaunchTime = 0L
                AppLockServiceHolder.isLockScreenOpen = false
                hideCover("LAUNCH ERROR")
                Log.e(TAG, "LOCK SCREEN ERROR", e)
            } finally {
                checkingPackage = null
            }
        }
    }

    // ------------------------------------------------------------------
    // Cover overlay
    // ------------------------------------------------------------------

    private fun showCover(timeoutMs: Long = COVER_TIMEOUT_MS) {
        if (!USE_COVER) return
        handler.removeCallbacks(hideCoverRunnable)
        handler.removeCallbacks(coverTimeoutRunnable)
        handler.postDelayed(coverTimeoutRunnable, timeoutMs)
        if (coverView != null) return

        try {
            val view = FrameLayout(this).apply { setBackgroundColor(COVER_COLOR) }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            windowManager.addView(view, params)
            coverView = view
            Log.d(TAG, "COVER SHOWN")
        } catch (e: Exception) {
            coverView = null
            Log.e(TAG, "COVER ERROR", e)
        }
    }

    private fun hideCover(reason: String) {
        handler.removeCallbacks(hideCoverRunnable)
        handler.removeCallbacks(coverTimeoutRunnable)
        val view = coverView ?: return
        coverView = null
        try {
            windowManager.removeView(view)
            Log.d(TAG, "COVER HIDDEN -> $reason")
        } catch (e: Exception) {
            Log.e(TAG, "COVER REMOVE ERROR", e)
        }
    }

    // ------------------------------------------------------------------
    // Relock
    // ------------------------------------------------------------------

    private fun scheduleRelock(reason: String) {
        if (!appProtectionEnabled) return
        if (AppLockServiceHolder.currentUnlockedApp == null) return

        handler.removeCallbacks(resetUnlockedAppRunnable)

        val delay = getRelockDelayMillis()
        if (delay <= 0L) {
            clearUnlockedApp("IMMEDIATE_$reason")
            return
        }

        Log.d(TAG, "RELOCK SCHEDULED ($reason) in ${delay}ms")
        handler.postDelayed(resetUnlockedAppRunnable, delay)
    }

    private fun clearUnlockedApp(reason: String) {
        Log.d(TAG, "UNLOCKED APP CLEARED -> $reason")
        handler.removeCallbacks(resetUnlockedAppRunnable)
        AppLockServiceHolder.clearUnlockedApp()
        checkingPackage = null
    }

    private fun clearProtectionState() {
        hideCover("PROTECTION OFF")
        handler.removeCallbacks(foregroundVerifyRunnable)
        handler.removeCallbacks(resetUnlockedAppRunnable)
        AppLockServiceHolder.clear()
        foregroundPackage = null
        pendingPackage = null
        checkingPackage = null
    }

    // ------------------------------------------------------------------
    // Receiver / notification
    // ------------------------------------------------------------------

    private fun registerScreenOffReceiver() {
        if (receiverRegistered) return
        try {
            ContextCompat.registerReceiver(
                this,
                screenOffReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "RECEIVER ERROR", e)
        }
    }

    // NOTE: Agar aapke project mein is function ka apna version tha
    // (custom icon / foregroundServiceType ke saath), to apna purana version yahan paste kar dein.
    private fun startForegroundServiceNotification() {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "App Lock Service",
                    NotificationManager.IMPORTANCE_LOW
                )
                manager.createNotificationChannel(channel)
            }

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

            val notification = builder
                .setContentTitle("App Lock is active")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "FOREGROUND NOTIFICATION ERROR", e)
        }
    }
}