package com.example.myapplication.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.myapplication.Firebase.RemoteConfigManager

class RemoteConfigViewModel : ViewModel() {

    private val remoteConfigManager =
        RemoteConfigManager()

    var showInterstitialAd by mutableStateOf(true)
        private set

    var showBannerAd by mutableStateOf(true)
        private set

    /*
     * Existing WelcomeScreen ke liye.
     *
     * WelcomeScreen:
     *
     * loadConfig { showAd ->
     *
     */
    fun loadConfig(
        onComplete: (Boolean) -> Unit = {}
    ) {
        remoteConfigManager.fetchConfig {
                showInterstitial,
                showBanner ->

            showInterstitialAd =
                showInterstitial

            showBannerAd =
                showBanner

            onComplete(
                showInterstitial
            )
        }
    }

    /*
     * MainScreen ke liye.
     *
     * Is method se Interstitial aur Banner
     * dono values milengi.
     */
    fun loadConfigWithBanner(
        onComplete: (
            showInterstitialAd: Boolean,
            showBannerAd: Boolean
        ) -> Unit = { _, _ -> }
    ) {
        remoteConfigManager.fetchConfig {
                showInterstitial,
                showBanner ->

            showInterstitialAd =
                showInterstitial

            showBannerAd =
                showBanner

            onComplete(
                showInterstitial,
                showBanner
            )
        }
    }
}