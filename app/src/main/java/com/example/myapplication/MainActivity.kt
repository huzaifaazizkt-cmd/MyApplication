package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity

import com.example.myapplication.navigation.NavGraph
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration

private const val TAG =
    "AppLockAdMob"

private const val TEST_DEVICE_ID =
    "E5CA263188C38AF6BD96C4C84E9600BB"

object AppPermissionFlow {

    const val PREFS_NAME =
        "applock_permission_flow"

    const val KEY_PENDING =
        "permission_setup_pending"

    const val KEY_TYPE =
        "pending_confirm_type"

    const val KEY_VALUE =
        "pending_confirm_value"

    fun savePendingSetup(
        context: Context,
        type: String,
        value: String
    ) {

        context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(
                KEY_PENDING,
                true
            )
            .putString(
                KEY_TYPE,
                type
            )
            .putString(
                KEY_VALUE,
                value
            )
            .apply()
    }

    fun isPending(
        context: Context
    ): Boolean {

        return context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .getBoolean(
                KEY_PENDING,
                false
            )
    }

    fun getPendingType(
        context: Context
    ): String? {

        return context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .getString(
                KEY_TYPE,
                null
            )
    }

    fun getPendingValue(
        context: Context
    ): String? {

        return context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .getString(
                KEY_VALUE,
                null
            )
    }

    fun clearPendingSetup(
        context: Context
    ) {

        context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .remove(KEY_PENDING)
            .remove(KEY_TYPE)
            .remove(KEY_VALUE)
            .apply()
    }
}

class MainActivity : AppCompatActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

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

        val permissionSetupPending =
            AppPermissionFlow.isPending(
                this
            )

        val startDestination =
            when {

                openResetPassword -> {
                    "resetCreate"
                }

                permissionSetupPending -> {
                    "permissionGate"
                }

                openUnlockAfterSplash -> {
                    "unlockScreen"
                }

                else -> {
                    "startScreen"
                }
            }

        setContent {

            NavGraph(
                context = this,
                startDestination = startDestination
            )
        }
    }

    // =========================================================
    // ADMOB
    // =========================================================

    private fun configureAdMob() {

        try {

            val requestConfiguration =
                RequestConfiguration.Builder()
                    .setTestDeviceIds(
                        listOf(
                            TEST_DEVICE_ID
                        )
                    )
                    .build()

            MobileAds.setRequestConfiguration(
                requestConfiguration
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "FAILED TO CONFIGURE TEST DEVICE",
                e
            )
        }

        MobileAds.initialize(this) {

            Log.d(
                TAG,
                "ADMOB INITIALIZED SUCCESSFULLY"
            )
        }
    }

    // =========================================================
    // NEW INTENT
    // =========================================================

    override fun onNewIntent(
        intent: Intent
    ) {

        super.onNewIntent(
            intent
        )

        setIntent(
            intent
        )

        val openResetPassword =
            intent.getBooleanExtra(
                "openResetPassword",
                false
            )

        if (openResetPassword) {

            setContent {

                NavGraph(
                    context = this,
                    startDestination = "resetCreate"
                )
            }
        }
    }
}