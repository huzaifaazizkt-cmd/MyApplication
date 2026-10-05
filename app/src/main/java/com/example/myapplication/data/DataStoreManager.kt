package com.example.myapplication.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(
    name = "app_lock"
)

class DataStoreManager(
    private val context: Context
) {

    companion object {

        // =====================================================
        // PIN / PATTERN / AUTH
        // =====================================================

        val PIN_KEY =
            stringPreferencesKey("pin_key")

        val PATTERN_KEY =
            stringPreferencesKey("pattern_key")

        val AUTH_TYPE_KEY =
            stringPreferencesKey("auth_type_key")


        // =====================================================
        // APP INITIALIZED
        // =====================================================

        val APP_INITIALIZED_KEY =
            booleanPreferencesKey("app_initialized")


        // =====================================================
        // LOCKED APPS
        // =====================================================

        val LOCKED_APPS =
            stringSetPreferencesKey("locked_apps")


        // =====================================================
        // APP PROTECTION
        // =====================================================

        val APP_PROTECTION_ENABLED =
            booleanPreferencesKey(
                "app_protection_enabled"
            )


        // =====================================================
        // LOCK SETTINGS
        // =====================================================

        val FINGERPRINT_ENABLED =
            booleanPreferencesKey(
                "fingerprint_enabled"
            )

        val VIBRATION_ENABLED =
            booleanPreferencesKey(
                "vibration_enabled"
            )

        val HIDE_TRACK_ENABLED =
            booleanPreferencesKey(
                "hide_track_enabled"
            )


        // =====================================================
        // INTRUDER
        // =====================================================

        val INTRUDER_ENABLED =
            booleanPreferencesKey(
                "intruder_enabled"
            )

        val INTRUDER_WRONG_ATTEMPTS =
            stringPreferencesKey(
                "intruder_wrong_attempts"
            )

        // Number of wrong attempts required
        // 0 = Never
        // 3 = 3 attempts
        // 5 = 5 attempts
        // 10 = 10 attempts
        val INTRUDER_OBSERVATION_ATTEMPTS =
            stringPreferencesKey(
                "intruder_observation_attempts"
            )

        // Delay before taking photo
        // Default = 5 seconds
        val INTRUDER_OBSERVATION_TIME =
            stringPreferencesKey(
                "intruder_observation_time"
            )

        val INTRUDER_PHOTOS =
            stringSetPreferencesKey(
                "intruder_photos"
            )

        val INTRUDER_PHOTO_URIS =
            stringSetPreferencesKey(
                "intruder_photo_uris"
            )


        // =====================================================
        // SECURITY QUESTION
        // =====================================================

        val SECURITY_QUESTION =
            stringPreferencesKey(
                "security_question"
            )

        val SECURITY_ANSWER =
            stringPreferencesKey(
                "security_answer"
            )


        // =====================================================
        // HIDE FROM RECENTS
        // =====================================================

        val HIDE_FROM_RECENTS =
            booleanPreferencesKey(
                "hide_from_recents"
            )


        // =====================================================
        // LANGUAGE
        // =====================================================

        val LANGUAGE_KEY =
            stringPreferencesKey(
                "language_key"
            )


        // =====================================================
        // RELOCK
        // =====================================================

        val RELOCK_OPTION_KEY =
            stringPreferencesKey(
                "relock_option_key"
            )

        val RELOCK_DELAY_KEY =
            stringPreferencesKey(
                "relock_delay_key"
            )


        // =====================================================
        // PREMIUM
        // =====================================================

        val PREMIUM_KEY =
            booleanPreferencesKey(
                "premium_key"
            )
    }


    // =========================================================
    // PIN
    // =========================================================

    suspend fun savePin(
        pin: String
    ) {
        context.dataStore.edit {
            it[PIN_KEY] = pin
        }
    }

    fun getPin(): Flow<String?> {
        return context.dataStore.data
            .catch { exception ->

                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map {
                it[PIN_KEY]
            }
    }


    // =========================================================
    // PATTERN
    // =========================================================

    suspend fun savePattern(
        pattern: String
    ) {
        context.dataStore.edit {
            it[PATTERN_KEY] = pattern
        }
    }

    fun getPattern(): Flow<String?> {
        return context.dataStore.data
            .catch { exception ->

                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map {
                it[PATTERN_KEY]
            }
    }


    // =========================================================
    // AUTH TYPE
    // =========================================================

    suspend fun saveAuthType(
        type: String
    ) {
        context.dataStore.edit {
            it[AUTH_TYPE_KEY] = type
        }
    }

    fun getAuthType(): Flow<String?> {
        return context.dataStore.data
            .map {
                it[AUTH_TYPE_KEY] ?: "pin"
            }
    }


    // =========================================================
    // APP INITIALIZED
    // =========================================================

    suspend fun saveAppInitialized(
        value: Boolean
    ) {
        context.dataStore.edit {
            it[APP_INITIALIZED_KEY] = value
        }
    }

    fun getAppInitialized(): Flow<Boolean> {
        return context.dataStore.data
            .catch { exception ->

                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->

                preferences[APP_INITIALIZED_KEY]
                    ?: (
                            preferences[PIN_KEY] != null ||
                                    preferences[PATTERN_KEY] != null
                            )
            }
    }


    // =========================================================
    // LOCKED APPS
    // =========================================================

    suspend fun saveLockedApp(
        packageName: String
    ) {
        context.dataStore.edit { preferences ->

            val currentApps =
                preferences[LOCKED_APPS]
                    ?: emptySet()

            preferences[LOCKED_APPS] =
                currentApps + packageName
        }
    }

    suspend fun removeLockedApp(
        packageName: String
    ) {
        context.dataStore.edit { preferences ->

            val currentApps =
                preferences[LOCKED_APPS]
                    ?: emptySet()

            preferences[LOCKED_APPS] =
                currentApps - packageName
        }
    }

    fun getLockedApps(): Flow<Set<String>> {
        return context.dataStore.data
            .catch { exception ->

                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map {
                it[LOCKED_APPS] ?: emptySet()
            }
    }

    val lockedAppsFlow: Flow<Set<String>>
        get() = getLockedApps()


    // =========================================================
    // APP PROTECTION
    // =========================================================

    suspend fun saveAppProtectionEnabled(
        enabled: Boolean
    ) {
        context.dataStore.edit {
            it[APP_PROTECTION_ENABLED] =
                enabled
        }
    }

    fun getAppProtectionEnabled(): Flow<Boolean> {
        return context.dataStore.data
            .catch { exception ->

                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map {

                it[APP_PROTECTION_ENABLED]
                    ?: true
            }
    }


    // =========================================================
    // FINGERPRINT
    // =========================================================

    suspend fun saveFingerprintEnabled(
        enabled: Boolean
    ) {
        context.dataStore.edit {
            it[FINGERPRINT_ENABLED] =
                enabled
        }
    }

    fun getFingerprintEnabled(): Flow<Boolean> {
        return context.dataStore.data
            .map {
                it[FINGERPRINT_ENABLED]
                    ?: false
            }
    }


    // =========================================================
    // VIBRATION
    // =========================================================

    suspend fun saveVibrationEnabled(
        enabled: Boolean
    ) {
        context.dataStore.edit {
            it[VIBRATION_ENABLED] =
                enabled
        }
    }

    fun getVibrationEnabled(): Flow<Boolean> {
        return context.dataStore.data
            .map {
                it[VIBRATION_ENABLED]
                    ?: false
            }
    }


    // =========================================================
    // HIDE TRACK
    // =========================================================

    suspend fun saveHideTrackEnabled(
        enabled: Boolean
    ) {
        context.dataStore.edit {
            it[HIDE_TRACK_ENABLED] =
                enabled
        }
    }

    fun getHideTrackEnabled(): Flow<Boolean> {
        return context.dataStore.data
            .map {
                it[HIDE_TRACK_ENABLED]
                    ?: false
            }
    }


    // =========================================================
    // INTRUDER ENABLED
    // =========================================================

    suspend fun saveIntruderEnabled(
        enabled: Boolean
    ) {
        context.dataStore.edit {
            it[INTRUDER_ENABLED] =
                enabled
        }
    }

    fun getIntruderEnabled(): Flow<Boolean> {
        return context.dataStore.data
            .map {
                it[INTRUDER_ENABLED]
                    ?: false
            }
    }


    // =========================================================
    // INTRUDER WRONG ATTEMPTS
    // =========================================================

    suspend fun saveIntruderWrongAttempts(
        attempts: Int
    ) {
        context.dataStore.edit {
            it[INTRUDER_WRONG_ATTEMPTS] =
                attempts.toString()
        }
    }

    fun getIntruderWrongAttempts(): Flow<Int> {
        return context.dataStore.data
            .map {
                it[INTRUDER_WRONG_ATTEMPTS]
                    ?.toIntOrNull()
                    ?: 0
            }
    }

    suspend fun resetIntruderWrongAttempts() {
        context.dataStore.edit {
            it[INTRUDER_WRONG_ATTEMPTS] =
                "0"
        }
    }


    // =========================================================
    // INTRUDER OBSERVATION ATTEMPTS
    // =========================================================

    suspend fun saveIntruderObservationAttempts(
        attempts: Int
    ) {
        context.dataStore.edit {
            it[INTRUDER_OBSERVATION_ATTEMPTS] =
                attempts.toString()
        }
    }

    fun getIntruderObservationAttempts(): Flow<Int> {
        return context.dataStore.data
            .map {

                when (
                    it[INTRUDER_OBSERVATION_ATTEMPTS]
                        ?.toIntOrNull()
                ) {

                    0 -> 0
                    3 -> 3
                    5 -> 5
                    10 -> 10

                    else -> 0
                }
            }
    }


    // =========================================================
    // INTRUDER OBSERVATION TIME
    // =========================================================

    suspend fun saveIntruderObservationTime(
        time: Int
    ) {
        context.dataStore.edit {
            it[INTRUDER_OBSERVATION_TIME] =
                time.toString()
        }
    }

    fun getIntruderObservationTime(): Flow<Int> {
        return context.dataStore.data
            .map {
                it[INTRUDER_OBSERVATION_TIME]
                    ?.toIntOrNull()
                    ?: 5
            }
    }


    // =========================================================
    // INTRUDER PHOTOS
    // =========================================================

    suspend fun saveIntruderPhoto(
        photoUri: String
    ) {
        context.dataStore.edit { preferences ->

            val currentPhotos =
                preferences[INTRUDER_PHOTO_URIS]
                    ?: emptySet()

            preferences[INTRUDER_PHOTO_URIS] =
                currentPhotos + photoUri
        }
    }

    fun getIntruderPhotosUris():
            Flow<Set<String>> {

        return context.dataStore.data
            .map {
                it[INTRUDER_PHOTO_URIS]
                    ?: emptySet()
            }
    }

    suspend fun removeIntruderPhoto(
        photoUri: String
    ) {
        context.dataStore.edit { preferences ->

            val currentPhotos =
                preferences[INTRUDER_PHOTO_URIS]
                    ?: emptySet()

            preferences[INTRUDER_PHOTO_URIS] =
                currentPhotos - photoUri
        }
    }

    suspend fun clearIntruderPhotos() {
        context.dataStore.edit {
            it[INTRUDER_PHOTO_URIS] =
                emptySet()
        }
    }


    // =========================================================
    // SECURITY QUESTION
    // =========================================================

    suspend fun saveSecurityQuestion(
        question: String
    ) {
        context.dataStore.edit {
            it[SECURITY_QUESTION] =
                question
        }
    }

    fun getSecurityQuestion():
            Flow<String?> {

        return context.dataStore.data
            .map {
                it[SECURITY_QUESTION]
            }
    }

    suspend fun saveSecurityAnswer(
        answer: String
    ) {
        context.dataStore.edit {
            it[SECURITY_ANSWER] =
                answer
        }
    }

    fun getSecurityAnswer():
            Flow<String?> {

        return context.dataStore.data
            .map {
                it[SECURITY_ANSWER]
            }
    }


    // =========================================================
    // HIDE FROM RECENTS
    // =========================================================

    suspend fun saveHideFromRecents(
        enabled: Boolean
    ) {
        context.dataStore.edit {
            it[HIDE_FROM_RECENTS] =
                enabled
        }
    }

    fun getHideFromRecents(): Flow<Boolean> {
        return context.dataStore.data
            .map {
                it[HIDE_FROM_RECENTS]
                    ?: false
            }
    }


    // =========================================================
    // LANGUAGE
    // =========================================================

    suspend fun saveLanguage(
        language: String
    ) {
        context.dataStore.edit {
            it[LANGUAGE_KEY] =
                language
        }
    }

    fun getLanguage(): Flow<String> {
        return context.dataStore.data
            .map {
                it[LANGUAGE_KEY]
                    ?: "en"
            }
    }


    // =========================================================
    // RELOCK OPTION
    // =========================================================

    suspend fun saveRelockOption(
        option: String
    ) {
        context.dataStore.edit {
            it[RELOCK_OPTION_KEY] =
                option
        }
    }

    fun getRelockOption(): Flow<String> {
        return context.dataStore.data
            .map {
                it[RELOCK_OPTION_KEY]
                    ?: "relock_after_quitting"
            }
    }


    // =========================================================
    // RELOCK DELAY
    // =========================================================

    suspend fun saveRelockDelay(
        delay: String
    ) {
        context.dataStore.edit {
            it[RELOCK_DELAY_KEY] =
                delay
        }
    }

    fun getRelockDelay(): Flow<String> {
        return context.dataStore.data
            .map {
                it[RELOCK_DELAY_KEY]
                    ?: "never"
            }
    }


    // =========================================================
    // PREMIUM
    // =========================================================

    suspend fun savePremium(
        enabled: Boolean
    ) {
        context.dataStore.edit {
            it[PREMIUM_KEY] =
                enabled
        }
    }

    fun getPremium(): Flow<Boolean> {

        return context.dataStore.data
            .catch { exception ->

                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map {

                it[PREMIUM_KEY]
                    ?: false
            }
    }
}