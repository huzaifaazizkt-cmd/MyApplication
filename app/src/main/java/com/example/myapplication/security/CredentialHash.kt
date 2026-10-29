package com.example.myapplication.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Salted verifier. Length is public UI metadata, never the credential itself. */
internal object CredentialHash {
    private const val ITERATIONS = 120_000
    fun encode(value: String): String {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        return listOf("pbkdf2", ITERATIONS, value.length, Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(derive(value, salt, ITERATIONS))).joinToString(":")
    }
    fun length(stored: String?): Int = if (stored?.startsWith("pbkdf2:") == true)
        stored.split(':').getOrNull(2)?.toIntOrNull() ?: 0 else stored?.length ?: 0
    fun matches(value: String, stored: String?): Boolean {
        if (stored.isNullOrEmpty()) return false
        if (!stored.startsWith("pbkdf2:")) return MessageDigest.isEqual(value.toByteArray(), stored.toByteArray())
        return runCatching {
            val parts = stored.split(':')
            val iterations = parts[1].toInt().also { require(it in 10_000..1_000_000) }
            MessageDigest.isEqual(derive(value, Base64.getDecoder().decode(parts[3]), iterations),
                Base64.getDecoder().decode(parts[4]))
        }.getOrDefault(false)
    }
    private fun derive(value: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(value.toCharArray(), salt, iterations, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}
