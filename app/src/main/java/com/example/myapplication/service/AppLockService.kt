package com.example.myapplication.service

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.Design.screens.LockScreenActivity
import com.example.myapplication.Design.screens.isFingerprintPromptShowing
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.data.AuthenticationSettingsCache
import com.example.myapplication.data.DataStoreManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Usage Access monitoring and one authentication activity; no intermediate overlay window. */
class AppLockService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val policy = RelockPolicy(SystemClock::elapsedRealtime)
    private lateinit var reader: UsageForegroundReader
    private var monitor: Job? = null
    private var settings: ProtectionSettings? = null
    private var current: ForegroundEntry? = null
    private var target: String? = null
    private var visibleTarget: String? = null
    private var launchPending = false
    private var launchAttempts = 0
    private var launchDeadline = 0L
    private var launchNotBefore = Long.MIN_VALUE
    private var policyHistoryAfter = Long.MIN_VALUE
    private var lastPermissionCheck = Long.MIN_VALUE
    private var home: String? = null
    private var resumeAfterScreenOff = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                monitor?.cancel(); monitor = null
                policy.onScreenOff()
                policyHistoryAfter = System.currentTimeMillis()
                resumeAfterScreenOff = true
                clearLock(finish = true)
            } else {
                if (intent?.action == Intent.ACTION_USER_PRESENT) {
                    launchNotBefore = Long.MIN_VALUE
                    policyHistoryAfter = Long.MIN_VALUE
                }
                startMonitoring()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppLockServiceHolder.service = this
        AppLockServiceHolder.setStatus(ProtectionStatus.CONNECTING)
        AuthenticationSettingsCache.start(this)
        reader = UsageForegroundReader(this)
        if (!publishNotification()) { stopSelf(); return }
        ContextCompat.registerReceiver(this, screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF).apply {
                addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            DataStoreManager(this@AppLockService).protectionSettings.retryWhen { error, _ ->
                Log.w(TAG, "Protection settings unavailable", error)
                settings = null
                updateStatus(ProtectionStatus.SETTINGS_UNAVAILABLE)
                clearLock(finish = true)
                delay(1_000)
                true
            }.collect { config ->
                settings = config
                policy.configure(config.mode, config.delayMillis)
                policy.retainPackages(config.lockedApps)
                if (!config.enabled || config.lockedApps.isEmpty()) {
                    clearLock(finish = true)
                    stopSelf()
                } else {
                    startMonitoring()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun interactive(): Boolean = getSystemService(PowerManager::class.java).isInteractive &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun startMonitoring() {
        if (monitor?.isActive == true || settings == null) return
        monitor = scope.launch {
            while (isActive) {
                if (!interactive()) { delay(1_000); continue }
                if (resumeAfterScreenOff) {
                    launchNotBefore = Long.MIN_VALUE; policyHistoryAfter = Long.MIN_VALUE
                    resumeAfterScreenOff = false
                }
                try {
                    val elapsed = SystemClock.elapsedRealtime()
                    if (lastPermissionCheck == Long.MIN_VALUE || elapsed - lastPermissionCheck >= 2_500) {
                        val access = withContext(Dispatchers.IO) { AppLockPermissions.read(this@AppLockService) }
                        lastPermissionCheck = elapsed
                        if (!access.ready) { clearLock(finish = true); stopSelf(); break }
                        home = packageManager.resolveActivity(
                            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
                    }
                    val batch = withContext(Dispatchers.IO) { reader.read() }
                    if (!interactive()) continue
                    val config = settings
                    if (batch != null && config != null) {
                        if (batch.clockReset) {
                            policy.clear(); launchNotBefore = Long.MIN_VALUE; policyHistoryAfter = Long.MIN_VALUE
                        }
                        // Replay departures for relock policy, but launch only for the final foreground app.
                        batch.entries.filter { it.timestamp >= policyHistoryAfter }.forEach { entry ->
                            val observed = (SystemClock.elapsedRealtime() -
                                (System.currentTimeMillis() - entry.timestamp).coerceAtLeast(0)).coerceAtLeast(0)
                            recordForeground(entry, config, observed)
                        }
                        current = batch.current
                        updateStatus(ProtectionStatus.READY)
                        policy.expireGrants()
                        enforceCurrent(config)
                    } else if (config != null) {
                        updateStatus(ProtectionStatus.MONITOR_UNAVAILABLE)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    updateStatus(ProtectionStatus.MONITOR_UNAVAILABLE)
                    Log.w(TAG, "Unable to read foreground app", error)
                }
                delay(350)
            }
        }
    }

    private fun ownChallenge(entry: ForegroundEntry) = entry.packageName == packageName &&
        entry.className == LockScreenActivity::class.java.name

    private fun ignored(entry: ForegroundEntry, config: ProtectionSettings): Boolean {
        if (entry.packageName == "com.android.systemui" || entry.packageName == "android" ||
            entry.packageName == "com.android.permissioncontroller" ||
            entry.packageName == "com.google.android.permissioncontroller") return true
        val challenge = LockScreenActivity.activeInstance
        return challenge != null && isFingerprintPromptShowing(challenge) && entry.packageName != home &&
            entry.packageName !in config.lockedApps && entry.packageName != packageName
    }

    private fun recordForeground(entry: ForegroundEntry, config: ProtectionSettings, observedAt: Long = SystemClock.elapsedRealtime()) {
        if (ownChallenge(entry) || ignored(entry, config)) return
        policy.onForeground(if (entry.packageName == home || entry.packageName == packageName) null else entry.packageName, observedAt)
    }

    private fun enforceCurrent(config: ProtectionSettings) {
        val entry = current ?: return
        if (entry.timestamp < policyHistoryAfter) return
        if (ownChallenge(entry)) {
            if (launchPending && SystemClock.elapsedRealtime() >= launchDeadline) recoverLaunch()
            return
        }
        if (ignored(entry, config)) return
        recordForeground(entry, config)
        val pkg = entry.packageName
        if (pkg == home || pkg == packageName || pkg !in config.lockedApps || policy.isAuthenticated(pkg)) {
            clearLock(finish = true)
            return
        }
        // Back/Home must not cause a relaunch from the last, now stale resume event.
        if (entry.timestamp < launchNotBefore) return
        if (target != pkg) {
            target = pkg; visibleTarget = null; launchPending = false; launchAttempts = 0
        }
        if (visibleTarget == pkg) return
        if (launchPending) {
            if (SystemClock.elapsedRealtime() >= launchDeadline) recoverLaunch()
            return
        }
        launchLock()
    }

    private fun launchLock() {
        val pkg = target ?: return
        launchPending = true
        launchAttempts++
        launchDeadline = SystemClock.elapsedRealtime() + 1_500
        try {
            startActivity(Intent(this, LockScreenActivity::class.java).putExtra("packageName", pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        } catch (error: Exception) {
            Log.w(TAG, "Lock activity launch failed", error)
            returnHome()
        }
    }

    private fun recoverLaunch() {
        if (launchAttempts < 2) launchLock() else returnHome()
    }

    private fun returnHome() {
        launchNotBefore = System.currentTimeMillis()
        runCatching { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)) }
            .onFailure { Log.w(TAG, "Unable to return Home", it) }
        clearLock(finish = true)
        policy.onForeground(null)
    }

    fun verifyAfterAuthenticationUi() { lastPermissionCheck = Long.MIN_VALUE }
    fun onLockVisible(pkg: String) {
        if (target == pkg) { visibleTarget = pkg; launchPending = false; launchAttempts = 0 }
    }
    fun onLockHidden(pkg: String) {
        if (target == pkg) { visibleTarget = null; launchPending = false; launchAttempts = 0 }
    }
    fun authenticate(pkg: String): Boolean {
        if (!isChallengeRequired(pkg)) return false
        policy.authenticate(pkg)
        policyHistoryAfter = System.currentTimeMillis()
        clearLock()
        return true
    }
    fun isChallengeRequired(pkg: String): Boolean = settings?.enabled == true && target == pkg &&
        AppLockServiceHolder.isConnected.value && interactive() && !policy.isAuthenticated(pkg)
    fun dismiss(pkg: String) {
        launchNotBefore = System.currentTimeMillis()
        policyHistoryAfter = launchNotBefore
        if (target == pkg) clearLock()
        policy.onForeground(null)
    }
    private fun clearLock(finish: Boolean = false) {
        target = null; visibleTarget = null; launchPending = false; launchAttempts = 0
        if (finish) LockScreenActivity.activeInstance?.takeIf { !it.isFinishing }?.finishAndRemoveTask()
    }

    private fun updateStatus(status: ProtectionStatus) {
        if (AppLockServiceHolder.state.value != status) {
            AppLockServiceHolder.setStatus(status)
            publishNotification()
        }
    }
    fun refreshNotification() { publishNotification() }
    private fun publishNotification(): Boolean {
        val channel = "applock_protection"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channel, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW))
        return try {
            val notification = NotificationCompat.Builder(this, channel).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(if (AppLockServiceHolder.state.value == ProtectionStatus.READY)
                    R.string.protection_active else if (AppLockServiceHolder.state.value == ProtectionStatus.CONNECTING)
                    R.string.protection_connecting else R.string.protection_paused))
                .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setOnlyAlertOnce(true).setOngoing(true).build()
            startForeground(1001, notification)
            val allowed = manager.areNotificationsEnabled() && manager.getNotificationChannel(channel)?.importance != NotificationManager.IMPORTANCE_NONE
            AppLockServiceHolder.setNotificationStatus(if (allowed) ProtectionNotificationStatus.AVAILABLE else ProtectionNotificationStatus.BLOCKED)
            true
        } catch (error: Exception) {
            AppLockServiceHolder.setNotificationStatus(ProtectionNotificationStatus.FAILED)
            AppLockServiceHolder.setStatus(ProtectionStatus.START_FAILED)
            Log.w(TAG, "Foreground notification unavailable", error)
            false
        }
    }

    override fun onDestroy() {
        monitor?.cancel()
        scope.cancel()
        clearLock(finish = true)
        policy.clear()
        runCatching { unregisterReceiver(screenReceiver) }
        if (AppLockServiceHolder.service === this) {
            AppLockServiceHolder.service = null
            AppLockServiceHolder.setStatus(ProtectionStatus.DISCONNECTED)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object { private const val TAG = "AppLockService" }
}
