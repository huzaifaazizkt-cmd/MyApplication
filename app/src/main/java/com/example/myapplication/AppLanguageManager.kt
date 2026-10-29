package com.example.myapplication

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.myapplication.data.DataStoreManager
import com.example.myapplication.data.canonicalLanguageTag
import kotlinx.coroutines.flow.first

object AppLanguageManager {
    suspend fun restoreLanguage(context: Context) {
        try {
            val current = getCurrentLanguage()
            if (current.isNotEmpty()) {
                val canonical = canonicalLanguageTag(current)
                if (canonical != current) setLanguage(canonical)
            } else if (Build.VERSION.SDK_INT <= 32) {
                DataStoreManager(context.applicationContext).getSavedLanguage().first()?.let(::setLanguage)
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.w("AppLanguageManager", "Could not restore language preference", error)
        }
    }

    fun setLanguage(languageCode: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(canonicalLanguageTag(languageCode)))
    }

    fun getCurrentLanguage(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags()
}
