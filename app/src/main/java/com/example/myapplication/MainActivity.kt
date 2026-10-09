
package com.example.myapplication

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

import com.example.myapplication.navigation.NavGraph
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration

private const val TAG = "AppLockAdMob"
private const val NOTIFICATION_TAG = "AppLockNotification"
private const val TEST_DEVICE_ID = "E5CA263188C38AF6BD96C4C84E9600BB"

object AppPermissionFlow {

    const val PREFS_NAME = "applock_permission_flow"
    const val KEY_PENDING = "permission_setup_pending"
    const val KEY_TYPE = "pending_confirm_type"
    const val KEY_VALUE = "pending_confirm_value"

    fun savePendingSetup(
        context: Context,
        type: String,
        value: String
    ) {
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putBoolean(KEY_PENDING, true)
            .putString(KEY_TYPE, type)
            .putString(KEY_VALUE, value)
            .apply()
    }

    fun isPending(context: Context): Boolean {
        return context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).getBoolean(KEY_PENDING, false)
    }

    fun getPendingType(context: Context): String? {
        return context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).getString(KEY_TYPE, null)
    }

    fun getPendingValue(context: Context): String? {
        return context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).getString(KEY_VALUE, null)
    }

    fun clearPendingSetup(context: Context) {
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .remove(KEY_PENDING)
            .remove(KEY_TYPE)
            .remove(KEY_VALUE)
            .apply()
    }

    /**
     * Agar setup adhoora hai to app ko permission screen se resume karein.
     */
    fun getPendingPermissionDestination(context: Context): String? {
        if (!isPending(context)) return null

        val type = getPendingType(context)
        val value = getPendingValue(context)

        return if (
            !type.isNullOrBlank() &&
            !value.isNullOrBlank()
        ) {
            "permissionGate"
        } else {
            null
        }
    }
}

class MainActivity : AppCompatActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
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
            openResetPassword -> "resetCreate"

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

        requestNotificationPermissionIfNeeded()
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

        if (
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
}