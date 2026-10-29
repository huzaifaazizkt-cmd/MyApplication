package com.example.myapplication.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/** Optional fast checks bound to a non-exportable app key and the exact salted verifier.
 * A missing/changed key returns null so the caller can use its existing PBKDF2 verifier.
 */
internal class FastCredentialVerifier(private val alias: String = "applock.credential-check.v1") {
    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(alias)) return store.getKey(alias, null) as SecretKey
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setKeySize(256).setDigests(KeyProperties.DIGEST_SHA256).build())
        }.generateKey()
    }
    private fun mac(key: SecretKey, value: String): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(key)
        doFinal(value.toByteArray(Charsets.UTF_8))
    }
    private fun identity(key: SecretKey) = mac(key, "AppLock credential key identity v1")
    private fun binding(kind: String, salted: String) =
        MessageDigest.getInstance("SHA-256").digest("$kind\u0000$salted".toByteArray(Charsets.UTF_8))
    private fun message(kind: String, salted: String, value: String) = "$kind\u0000$salted\u0000$value"

    fun encode(kind: String, salted: String, value: String): String? = runCatching {
        val key = key()
        val base64 = Base64.getEncoder()
        "v1:${base64.encodeToString(identity(key))}:${base64.encodeToString(binding(kind, salted))}:${base64.encodeToString(mac(key, message(kind, salted, value)))}"
    }.onFailure { Log.w("CredentialVerifier", "Fast verifier unavailable; using salted verifier", it) }.getOrNull()

    /** false is a real mismatch; null means the fast path is unavailable or must be re-enrolled. */
    fun matches(value: String, kind: String, salted: String, record: String?): Boolean? {
        if (record == null) return null
        return runCatching {
            val parts = record.split(':')
            if (parts.size != 4 || parts[0] != "v1") return@runCatching null
            val key = key()
            val decoder = Base64.getDecoder()
            if (!MessageDigest.isEqual(identity(key), decoder.decode(parts[1]))) return@runCatching null
            if (!MessageDigest.isEqual(binding(kind, salted), decoder.decode(parts[2]))) return@runCatching null
            val expected = decoder.decode(parts[3])
            if (expected.size != 32) return@runCatching null
            MessageDigest.isEqual(mac(key, message(kind, salted, value)), expected)
        }.onFailure { Log.w("CredentialVerifier", "Fast check unavailable; using salted verifier", it) }.getOrNull()
    }
}
