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
import com.example.myapplication.security.CredentialHash
import com.example.myapplication.security.FastCredentialVerifier
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale
import com.example.myapplication.service.ProtectionSettings
import com.example.myapplication.service.RelockPolicy

private val Context.dataStore by preferencesDataStore(name = "app_lock")

class DataStoreManager(context: Context) {
    private val context = context.applicationContext
    val authenticationSettings: Flow<AuthenticationSettings> = context.dataStore.data.map { prefs ->
        AuthenticationSettings(
            type = prefs[AUTH_TYPE_KEY]?.lowercase(Locale.ROOT) ?: "pin",
            pinLength = CredentialHash.length(prefs[PIN_KEY]).takeIf { it in 4..12 } ?: 6,
            fingerprint = prefs[FINGERPRINT_ENABLED] ?: false,
            vibration = prefs[VIBRATION_ENABLED] ?: false,
            hideTrack = prefs[HIDE_TRACK_ENABLED] ?: false
        )
    }.distinctUntilChanged()
    // A single snapshot prevents a partially loaded policy being treated as ready.
    val protectionSettings: Flow<ProtectionSettings> = context.dataStore.data.map { prefs ->
        ProtectionSettings(
            enabled = prefs[APP_PROTECTION_ENABLED] ?: true,
            lockedApps = prefs[LOCKED_APPS] ?: emptySet(),
            mode = if (prefs[RELOCK_OPTION_KEY] == "relock_after_screen_off")
                RelockPolicy.Mode.AFTER_SCREEN_OFF else RelockPolicy.Mode.AFTER_QUITTING,
            delayMillis = when (prefs[RELOCK_DELAY_KEY]) {
                "five_seconds", "5_seconds" -> 5_000L
                "ten_seconds", "10_seconds" -> 10_000L
                "thirty_seconds", "30_seconds" -> 30_000L
                "one_minute", "1_minute" -> 60_000L
                "two_minutes", "2_minutes" -> 120_000L
                "five_minutes", "5_minutes" -> 300_000L
                else -> 0L // Migrates the old "never" value to immediate behavior.
            }
        )
    }.distinctUntilChanged()
    companion object {
        private val fastVerifier = FastCredentialVerifier()
        val PIN_KEY = stringPreferencesKey("pin_key")
        val PATTERN_KEY = stringPreferencesKey("pattern_key")
        val AUTH_TYPE_KEY = stringPreferencesKey("auth_type_key")
        val APP_INITIALIZED_KEY = booleanPreferencesKey("app_initialized")
        val LOCKED_APPS = stringSetPreferencesKey("locked_apps")
        val APP_PROTECTION_ENABLED = booleanPreferencesKey("app_protection_enabled")
        val FINGERPRINT_ENABLED = booleanPreferencesKey("fingerprint_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val HIDE_TRACK_ENABLED = booleanPreferencesKey("hide_track_enabled")
        val INTRUDER_ENABLED = booleanPreferencesKey("intruder_enabled")
        val INTRUDER_WRONG_ATTEMPTS = stringPreferencesKey("intruder_wrong_attempts")
        val INTRUDER_OBSERVATION_ATTEMPTS = stringPreferencesKey("intruder_observation_attempts")
        val INTRUDER_OBSERVATION_TIME = stringPreferencesKey("intruder_observation_time")
        val INTRUDER_PHOTOS = stringSetPreferencesKey("intruder_photos")
        val INTRUDER_PHOTO_URIS = stringSetPreferencesKey("intruder_photo_uris")
        val SECURITY_QUESTION = stringPreferencesKey("security_question")
        val SECURITY_ANSWER = stringPreferencesKey("security_answer")
        val HIDE_FROM_RECENTS = booleanPreferencesKey("hide_from_recents")
        val LANGUAGE_KEY = stringPreferencesKey("language_key")
        val RELOCK_OPTION_KEY = stringPreferencesKey("relock_option_key")
        val RELOCK_DELAY_KEY = stringPreferencesKey("relock_delay_key")
        val PREMIUM_KEY = booleanPreferencesKey("premium_key")
    }

    suspend fun savePin(pin: String) {
        saveCredential("pin", pin)
    }

    fun getPin(): Flow<String?> {
        return context.dataStore.data
            .catch { exception -> if (exception is IOException) emit(emptyPreferences()) else throw exception }
            .map { it[PIN_KEY] }
    }

    suspend fun savePattern(pattern: String) {
        saveCredential("pattern", pattern)
    }

    suspend fun saveCredential(type: String, value: String) {
        require(type == "pin" || type == "pattern")
        require(value.isNotEmpty())
        val key = if (type == "pin") PIN_KEY else PATTERN_KEY
        val (hash, fast) = withContext(Dispatchers.Default) {
            val hash = CredentialHash.encode(value)
            hash to fastVerifier.encode(key.name, hash, value)
        }
        context.dataStore.edit {
            it[key] = hash
            val other = if (type == "pin") PATTERN_KEY else PIN_KEY
            it.remove(other)
            it.remove(fastKey(other))
            if (fast != null) it[fastKey(key)] = fast else it.remove(fastKey(key))
            it[AUTH_TYPE_KEY] = type
        }
    }

    fun getPinLength(): Flow<Int> = getPin().map { CredentialHash.length(it) }
    suspend fun verifyPin(value: String) = verify(value, PIN_KEY)
    suspend fun verifyPattern(value: String) = verify(value, PATTERN_KEY)
    suspend fun verifySecurityAnswer(value: String) = verify(value.trim().lowercase(Locale.ROOT), SECURITY_ANSWER, true)
    private fun fastKey(key: Preferences.Key<String>) = stringPreferencesKey("${key.name}_fast_verifier")
    private suspend fun verify(value: String, key: Preferences.Key<String>, normalizeLegacy: Boolean = false): Boolean {
        val prefs = context.dataStore.data.first()
        val stored = prefs[key] ?: return false
        val quick = withContext(Dispatchers.Default) {
            fastVerifier.matches(value, key.name, stored, prefs[fastKey(key)])
        }
        if (quick != null) return quick
        val comparison = if (normalizeLegacy && !stored.startsWith("pbkdf2:")) stored.trim().lowercase(Locale.ROOT) else stored
        val valid = withContext(Dispatchers.Default) { CredentialHash.matches(value, comparison) }
        if (valid) {
            val (salted, fast) = withContext(Dispatchers.Default) {
                val salted = if (stored.startsWith("pbkdf2:")) stored else CredentialHash.encode(value)
                salted to fastVerifier.encode(key.name, salted, value)
            }
            context.dataStore.edit {
                if (it[key] == stored) {
                    it[key] = salted
                    if (fast != null) it[fastKey(key)] = fast else it.remove(fastKey(key))
                }
            }
        }
        return valid
    }

    fun getPattern(): Flow<String?> {
        return context.dataStore.data
            .catch { exception -> if (exception is IOException) emit(emptyPreferences()) else throw exception }
            .map { it[PATTERN_KEY] }
    }

    suspend fun saveAuthType(type: String) {
        context.dataStore.edit { it[AUTH_TYPE_KEY] = type }
    }

    fun getAuthType(): Flow<String?> {
        return context.dataStore.data.map { it[AUTH_TYPE_KEY] ?: "pin" }
    }

    suspend fun saveAppInitialized(value: Boolean) {
        context.dataStore.edit { it[APP_INITIALIZED_KEY] = value }
    }

    fun getAppInitialized(): Flow<Boolean> {
        return context.dataStore.data
            .map { preferences -> preferences[APP_INITIALIZED_KEY] ?: false }
            .catch { exception -> if (exception is IOException) emit(true) else throw exception }
    }

    suspend fun saveLockedApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentApps = preferences[LOCKED_APPS] ?: emptySet()
            preferences[LOCKED_APPS] = currentApps + packageName
        }
    }

    suspend fun removeLockedApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentApps = preferences[LOCKED_APPS] ?: emptySet()
            preferences[LOCKED_APPS] = currentApps - packageName
        }
    }

    fun getLockedApps(): Flow<Set<String>> {
        return context.dataStore.data
            .catch { exception -> if (exception is IOException) emit(emptyPreferences()) else throw exception }
            .map { it[LOCKED_APPS] ?: emptySet() }
            .distinctUntilChanged()
    }

    val lockedAppsFlow: Flow<Set<String>>
        get() = getLockedApps()

    suspend fun saveAppProtectionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[APP_PROTECTION_ENABLED] = enabled }
    }

    fun getAppProtectionEnabled(): Flow<Boolean> {
        return context.dataStore.data
            .catch { exception -> if (exception is IOException) emit(emptyPreferences()) else throw exception }
            .map { it[APP_PROTECTION_ENABLED] ?: true }
    }

    suspend fun saveFingerprintEnabled(enabled: Boolean) {
        context.dataStore.edit { it[FINGERPRINT_ENABLED] = enabled }
    }

    fun getFingerprintEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { it[FINGERPRINT_ENABLED] ?: false }
    }

    suspend fun saveVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[VIBRATION_ENABLED] = enabled }
    }

    fun getVibrationEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { it[VIBRATION_ENABLED] ?: false }
    }

    suspend fun saveHideTrackEnabled(enabled: Boolean) {
        context.dataStore.edit { it[HIDE_TRACK_ENABLED] = enabled }
    }

    fun getHideTrackEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { it[HIDE_TRACK_ENABLED] ?: false }
    }

    suspend fun saveIntruderEnabled(enabled: Boolean) {
        context.dataStore.edit {
            val allowed = enabled && it[PREMIUM_KEY] == true
            if (it[INTRUDER_ENABLED] != allowed) it[INTRUDER_WRONG_ATTEMPTS] = "0"
            it[INTRUDER_ENABLED] = allowed
        }
    }

    fun getIntruderEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { it[INTRUDER_ENABLED] ?: false }
    }

    suspend fun saveIntruderWrongAttempts(attempts: Int) {
        context.dataStore.edit { it[INTRUDER_WRONG_ATTEMPTS] = attempts.toString() }
    }

    fun getIntruderWrongAttempts(): Flow<Int> {
        return context.dataStore.data.map { it[INTRUDER_WRONG_ATTEMPTS]?.toIntOrNull() ?: 0 }
    }

    suspend fun recordIntruderAttempt(): Boolean {
        var capture = false
        context.dataStore.edit { prefs ->
            val threshold = when (val saved = prefs[INTRUDER_OBSERVATION_ATTEMPTS]?.toIntOrNull()) {
                0, 3, 5, 10 -> saved
                else -> 0
            }
            if (prefs[PREMIUM_KEY] != true || prefs[INTRUDER_ENABLED] != true || threshold <= 0) return@edit
            val attempts = (prefs[INTRUDER_WRONG_ATTEMPTS]?.toIntOrNull() ?: 0) + 1
            capture = attempts >= threshold
            prefs[INTRUDER_WRONG_ATTEMPTS] = if (capture) "0" else attempts.toString()
        }
        return capture
    }

    suspend fun resetIntruderWrongAttempts() {
        context.dataStore.edit { it[INTRUDER_WRONG_ATTEMPTS] = "0" }
    }

    suspend fun saveIntruderObservationAttempts(attempts: Int) {
        require(attempts in setOf(0, 3, 5, 10))
        context.dataStore.edit {
            if (it[INTRUDER_OBSERVATION_ATTEMPTS] != attempts.toString()) it[INTRUDER_WRONG_ATTEMPTS] = "0"
            it[INTRUDER_OBSERVATION_ATTEMPTS] = attempts.toString()
        }
    }

    fun getIntruderObservationAttempts(): Flow<Int> {
        return context.dataStore.data.map {
            when (it[INTRUDER_OBSERVATION_ATTEMPTS]?.toIntOrNull()) {
                0 -> 0
                3 -> 3
                5 -> 5
                10 -> 10
                else -> 0
            }
        }
    }

    suspend fun saveIntruderObservationTime(time: Int) {
        context.dataStore.edit { it[INTRUDER_OBSERVATION_TIME] = time.toString() }
    }

    fun getIntruderObservationTime(): Flow<Int> {
        return context.dataStore.data.map { it[INTRUDER_OBSERVATION_TIME]?.toIntOrNull() ?: 0 }
    }

    suspend fun saveIntruderPhoto(photoUri: String) {
        context.dataStore.edit { preferences ->
            val currentPhotos = preferences[INTRUDER_PHOTO_URIS] ?: emptySet()
            preferences[INTRUDER_PHOTO_URIS] = currentPhotos + photoUri
        }
    }

    fun getIntruderPhotosUris(): Flow<Set<String>> {
        return context.dataStore.data.map { it[INTRUDER_PHOTO_URIS] ?: emptySet() }
    }

    suspend fun removeIntruderPhoto(photoUri: String) {
        context.dataStore.edit { preferences ->
            val currentPhotos = preferences[INTRUDER_PHOTO_URIS] ?: emptySet()
            preferences[INTRUDER_PHOTO_URIS] = currentPhotos - photoUri
        }
    }

    suspend fun clearIntruderPhotos() {
        context.dataStore.edit { it[INTRUDER_PHOTO_URIS] = emptySet() }
    }

    fun getSecurityQuestion(): Flow<String?> {
        return context.dataStore.data.map { it[SECURITY_QUESTION] }
    }

    suspend fun saveSecurityQuestionAndAnswer(question: String, answer: String) {
        require(question.isNotBlank() && answer.isNotBlank())
        val normalized = answer.trim().lowercase(Locale.ROOT)
        val (hash, fast) = withContext(Dispatchers.Default) {
            val hash = CredentialHash.encode(normalized)
            hash to fastVerifier.encode(SECURITY_ANSWER.name, hash, normalized)
        }
        context.dataStore.edit {
            it[SECURITY_QUESTION] = question.trim()
            it[SECURITY_ANSWER] = hash
            if (fast != null) it[fastKey(SECURITY_ANSWER)] = fast else it.remove(fastKey(SECURITY_ANSWER))
        }
    }

    fun getSecurityAnswer(): Flow<String?> {
        return context.dataStore.data.map { it[SECURITY_ANSWER] }
    }

    suspend fun saveHideFromRecents(enabled: Boolean) {
        context.dataStore.edit { it[HIDE_FROM_RECENTS] = enabled }
    }

    fun getHideFromRecents(): Flow<Boolean> {
        return context.dataStore.data.map { it[HIDE_FROM_RECENTS] ?: false }
    }

    suspend fun saveLanguage(language: String) {
        context.dataStore.edit { it[LANGUAGE_KEY] = canonicalLanguageTag(language) }
    }

    fun getSavedLanguage(): Flow<String?> = context.dataStore.data.map { prefs -> prefs[LANGUAGE_KEY]?.let(::canonicalLanguageTag) }

    fun getLanguage(): Flow<String> {
        return context.dataStore.data.map { canonicalLanguageTag(it[LANGUAGE_KEY] ?: "en") }
    }

    suspend fun saveRelockOption(option: String) {
        context.dataStore.edit { it[RELOCK_OPTION_KEY] = option }
    }

    fun getRelockOption(): Flow<String> {
        return context.dataStore.data.map { it[RELOCK_OPTION_KEY] ?: "relock_after_quitting" }
    }

    suspend fun saveRelockDelay(delay: String) {
        context.dataStore.edit { it[RELOCK_DELAY_KEY] = delay }
    }

    fun getRelockDelay(): Flow<String> {
        return context.dataStore.data.map { it[RELOCK_DELAY_KEY] ?: "never" }
    }

    suspend fun savePremium(enabled: Boolean) {
        context.dataStore.edit {
            if (it[PREMIUM_KEY] != enabled) it[INTRUDER_WRONG_ATTEMPTS] = "0"
            it[PREMIUM_KEY] = enabled
        }
    }

    fun getPremium(): Flow<Boolean> {
        return context.dataStore.data
            .map { it[PREMIUM_KEY] ?: false }
    }
}
