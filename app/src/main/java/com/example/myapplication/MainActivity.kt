
package com.example.myapplication

import com.example.myapplication.billing.PremiumRepository

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log

import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.DataStoreManager
import com.example.myapplication.security.AuthSession
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.myapplication.data.AuthenticationSettingsCache
import com.example.myapplication.service.AppLockServiceHolder

import com.example.myapplication.navigation.NavGraph
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration

private const val TAG = "AppLockAdMob"
private const val NOTIFICATION_TAG = "AppLockNotification"
private const val TEST_DEVICE_ID = "E5CA263188C38AF6BD96C4C84E9600BB"

class MainActivity : AppCompatActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                AppLockServiceHolder.refreshNotification()
                Log.d(
                    NOTIFICATION_TAG,
                    "Notification permission granted"
                )
            } else {
                Log.w(
                    NOTIFICATION_TAG,
                    "Notification permission denied by user"
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        AppPermissionFlow.removeLegacyCredential(this)
        AuthenticationSettingsCache.start(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                DataStoreManager(this@MainActivity).protectionSettings.collectLatest {
                    try {
                        com.example.myapplication.service.AppLockServiceController.sync(this@MainActivity)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w("AppLockService", "Unable to update protection", error)
                    }
                }
            }
        }
        lifecycleScope.launch { AppLanguageManager.restoreLanguage(this@MainActivity) }
        lifecycleScope.launch {
            DataStoreManager(this@MainActivity).getHideFromRecents().collect { hidden ->
                getSystemService(android.app.ActivityManager::class.java).appTasks
                    .firstOrNull {
                        val info = it.taskInfo
                        if (Build.VERSION.SDK_INT >= 29) info?.taskId == taskId else info?.id == taskId
                    }?.setExcludeFromRecents(hidden)
            }
        }

        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        configureAdMob()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                combine(AppLockServiceHolder.isConnected, AuthSession.isAuthenticated,
                    DataStoreManager(this@MainActivity).getAppInitialized()) { ready, authenticated, initialized ->
                    ready && (!initialized || authenticated)
                }.collect { canRequest ->
                    if (canRequest) {
                        AppLockServiceHolder.refreshNotification()
                        requestNotificationPermissionIfNeeded()
                    }
                }
            }
        }

        val openResetPassword =
            intent.getBooleanExtra(
                "openResetPassword",
                false
            )

        val openUnlockAfterSplash =
            intent.getBooleanExtra(
                "openUnlockAfterSplash",
                false
            )

        // Incomplete permission setup ko unlock/start screen
        // se priority dein, taake user setup dobara continue kar sake.
        val pendingPermissionDestination =
            AppPermissionFlow.getPendingPermissionDestination(this)

        val startDestination = when {
            openResetPassword && AuthSession.hasResetAuthorization -> "resetCreate"

            pendingPermissionDestination != null ->
                pendingPermissionDestination

            openUnlockAfterSplash -> "unlockScreen"

            else -> "startScreen"
        }

        Log.d(
            "AppPermissionFlow",
            "Initial destination: $startDestination"
        )

        setContent {
            NavGraph(
                context = this,
                startDestination = startDestination
            )
        }

    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try {
                val initialized = DataStoreManager(this@MainActivity).getAppInitialized().first()
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                    PremiumRepository.get(this@MainActivity).onForeground(initialized)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Log.w("PremiumBilling", "Entry check unavailable")
            }
        }
        if (AppLockServiceHolder.isConnected.value) AppLockServiceHolder.refreshNotification()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        val permission = Manifest.permission.POST_NOTIFICATIONS

        val permissionGranted =
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            val prefs = getSharedPreferences("notification_permission", Context.MODE_PRIVATE)
            if (prefs.getBoolean("requested", false)) return
            prefs.edit().putBoolean("requested", true).apply()
            notificationPermissionLauncher.launch(permission)
        } else {
            Log.d(
                NOTIFICATION_TAG,
                "Notification permission already granted"
            )
        }
    }

    private fun configureAdMob() {
        try {
            val requestConfiguration =
                RequestConfiguration.Builder()
                    .setTestDeviceIds(
                        listOf(TEST_DEVICE_ID)
                    )
                    .build()

            MobileAds.setRequestConfiguration(
                requestConfiguration
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Failed to configure test device",
                e
            )
        }

        MobileAds.initialize(this) {
            Log.d(
                TAG,
                "AdMob initialized successfully"
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (AuthSession.hasResetAuthorization &&
            intent.getBooleanExtra(
                "openResetPassword",
                false
            )
        ) {
            setContent {
                NavGraph(
                    context = this,
                    startDestination = "resetCreate"
                )
            }
        }
    }

    override fun onStop() {
        if (!isChangingConfigurations) AuthSession.lock()
        super.onStop()
    }
}
