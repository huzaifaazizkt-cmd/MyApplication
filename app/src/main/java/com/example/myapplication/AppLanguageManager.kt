
package com.example.myapplication

import android.util.Log

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object AppLanguageManager {

    private const val TAG = "AppLanguageManager"

    fun setLanguage(
        languageCode: String
    ) {
        try {

            Log.d(
                TAG,
                "Setting language = $languageCode"
            )

            val localeList =
                LocaleListCompat.forLanguageTags(
                    languageCode
                )

            Log.d(
                TAG,
                "LocaleList = $localeList"
            )

            AppCompatDelegate.setApplicationLocales(
                localeList
            )

            val currentLocales =
                AppCompatDelegate.getApplicationLocales()

            Log.d(
                TAG,
                "Application locales after change = $currentLocales"
            )

            Log.d(
                TAG,
                "Current locale tags = ${currentLocales.toLanguageTags()}"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to change application language",
                e
            )
        }
    }

    fun getCurrentLanguage(): String {

        val locales =
            AppCompatDelegate.getApplicationLocales()

        val language =
            locales.toLanguageTags()

        Log.d(
            TAG,
            "Current application language = $language"
        )

        return language
    }
}

