package com.example.myapplication.Firebase

import android.app.Activity
import android.content.Context
import android.util.Log

import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

class ConsentManager(
    context: Context
) {

    private val appContext = context.applicationContext

    private val consentInformation =
        UserMessagingPlatform.getConsentInformation(
            appContext
        )

    fun requestConsent(
        activity: Activity,
        onComplete: (Boolean) -> Unit
    ) {

        val params =
            ConsentRequestParameters.Builder()
                .build()

        Log.d(
            TAG,
            "Requesting consent information"
        )

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,

            {

                Log.d(
                    TAG,
                    "Consent information updated"
                )

                UserMessagingPlatform
                    .loadAndShowConsentFormIfRequired(
                        activity
                    ) { formError ->

                        if (formError != null) {

                            Log.e(
                                TAG,
                                "Consent form error: ${formError.message}"
                            )

                        } else {

                            Log.d(
                                TAG,
                                "Consent form completed"
                            )
                        }

                        val canRequestAds =
                            consentInformation.canRequestAds()

                        Log.d(
                            TAG,
                            "Can request ads = $canRequestAds"
                        )

                        onComplete(
                            canRequestAds
                        )
                    }
            },

            { requestConsentError ->

                Log.e(
                    TAG,
                    "Consent information update failed: " +
                            requestConsentError.message
                )

                val canRequestAds =
                    consentInformation.canRequestAds()

                Log.d(
                    TAG,
                    "Can request ads after error = $canRequestAds"
                )

                onComplete(
                    canRequestAds
                )
            }
        )
    }

    fun canRequestAds(): Boolean {

        return consentInformation.canRequestAds()
    }

    fun isPrivacyOptionsRequired(): Boolean {

        return consentInformation
            .privacyOptionsRequirementStatus ==
                ConsentInformation
                    .PrivacyOptionsRequirementStatus
                    .REQUIRED
    }

    fun showPrivacyOptions(
        activity: Activity,
        onComplete: () -> Unit = {}
    ) {

        UserMessagingPlatform
            .showPrivacyOptionsForm(
                activity
            ) {

                onComplete()
            }
    }

    companion object {

        private const val TAG =
            "AppLockConsent"
    }
}