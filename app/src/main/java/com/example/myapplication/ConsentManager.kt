package com.example.myapplication


import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

class ConsentManager(context: Context) {

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context.applicationContext)

    val canRequestAds: Boolean
        get() = consentInformation.canRequestAds()

    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /** Requests consent info and shows the form only if one is required. */
    fun gatherConsent(activity: Activity, onComplete: (errorMessage: String?) -> Unit) {
        val params = ConsentRequestParameters.Builder()
            // TESTING ONLY - remove before release
            // .setConsentDebugSettings(
            //     com.google.android.ump.ConsentDebugSettings.Builder(activity)
            //         .setDebugGeography(
            //             com.google.android.ump.ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
            //         )
            //         .addTestDeviceHashedId("YOUR_HASHED_ID") // printed in Logcat
            //         .build()
            // )
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    onComplete(formError?.message)
                }
            },
            { requestError -> onComplete(requestError.message) }
        )
    }

    fun showPrivacyOptions(activity: Activity, onDismiss: (String?) -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { onDismiss(it?.message) }
    }

    /** Initializes the Mobile Ads SDK once, and only if consent allows it. */
    fun initializeAdsIfAllowed(context: Context) {
        if (canRequestAds && adsInitialized.compareAndSet(false, true)) {
            MobileAds.initialize(context.applicationContext) {}
        }
    }

    companion object {
        private val adsInitialized = AtomicBoolean(false)
    }
}