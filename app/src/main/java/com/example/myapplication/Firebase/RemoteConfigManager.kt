package com.example.myapplication.Firebase

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

class RemoteConfigManager {

    private val remoteConfig =
        FirebaseRemoteConfig.getInstance()

    init {
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 0
        }

        remoteConfig.setConfigSettingsAsync(
            configSettings
        )

        remoteConfig.setDefaultsAsync(
            mapOf(
                "show_interstitial_ad" to true,
                "show_banner_ad" to true
            )
        )
    }

    fun fetchConfig(
        onComplete: (
            showInterstitialAd: Boolean,
            showBannerAd: Boolean
        ) -> Unit
    ) {
        remoteConfig
            .fetchAndActivate()
            .addOnCompleteListener { task ->

                val showInterstitialAd =
                    remoteConfig.getBoolean(
                        "show_interstitial_ad"
                    )

                val showBannerAd =
                    remoteConfig.getBoolean(
                        "show_banner_ad"
                    )

                if (task.isSuccessful) {

                    Log.d(
                        "RemoteConfig",
                        "Fetch successful"
                    )

                } else {

                    Log.e(
                        "RemoteConfig",
                        "Fetch failed",
                        task.exception
                    )
                }

                Log.d(
                    "RemoteConfig",
                    "show_interstitial_ad = $showInterstitialAd"
                )

                Log.d(
                    "RemoteConfig",
                    "show_banner_ad = $showBannerAd"
                )

                onComplete(
                    showInterstitialAd,
                    showBannerAd
                )
            }
    }
}