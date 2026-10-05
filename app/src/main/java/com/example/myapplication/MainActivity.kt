package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log

import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent

import com.example.myapplication.navigation.NavGraph

import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration

private const val TAG = "AppLockAdMob"

private const val TEST_DEVICE_ID =
    "E5CA263188C38AF6BD96C4C84E9600BB"

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


        val startDestination =
            when {

                openResetPassword -> {
                    "resetCreate"
                }

                openUnlockAfterSplash -> {
                    "unlockScreen"
                }

                else -> {
                    "startScreen"
                }
            }

        Log.d(
            TAG,
            "MAIN ACTIVITY CREATED"
        )

        Log.d(
            TAG,
            "REQUESTED START DESTINATION = $startDestination"
        )

        Log.d(
            "AppLanguageManager",
            "Current locale = ${
                androidx.appcompat.app.AppCompatDelegate
                    .getApplicationLocales()
                    .toLanguageTags()
            }"
        )

        setContent {

            NavGraph(
                context = this,
                startDestination = startDestination
            )
        }
    }

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

            Log.d(
                TAG,
                "TEST DEVICE CONFIGURED"
            )

            Log.d(
                TAG,
                "Test Device ID = $TEST_DEVICE_ID"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to configure test device",
                e
            )
        }

        Log.d(
            TAG,
            "Initializing Google Mobile Ads SDK..."
        )

        MobileAds.initialize(
            this
        ) {

            Log.d(
                TAG,
                "AdMob initialized successfully"
            )
        }
    }

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

        Log.d(
            TAG,
            "MAIN ACTIVITY NEW INTENT"
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