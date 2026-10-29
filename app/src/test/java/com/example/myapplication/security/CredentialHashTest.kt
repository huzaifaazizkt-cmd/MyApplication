package com.example.myapplication.security

import org.junit.Assert.*
import org.junit.Test

class CredentialHashTest {
    @Test fun saltedHashVerifiesWithoutStoringCredential() {
        val first = CredentialHash.encode("123456")
        val second = CredentialHash.encode("123456")
        assertNotEquals(first, second)
        assertTrue(CredentialHash.matches("123456", first))
        assertFalse(CredentialHash.matches("123457", first))
        assertEquals(6, CredentialHash.length(first))
    }
    @Test fun legacyCredentialsRemainVerifiableForMigration() {
        assertTrue(CredentialHash.matches("0-1-2-5", "0-1-2-5"))
        assertFalse(CredentialHash.matches("0-1-2-4", "0-1-2-5"))
    }
    @Test fun corruptAndMissingVerifiersFailClosed() {
        assertFalse(CredentialHash.matches("1234", null))
        assertFalse(CredentialHash.matches("", ""))
        assertFalse(CredentialHash.matches("1234", "pbkdf2:9999999999:4:bad:bad"))
        assertFalse(CredentialHash.matches("1234", "pbkdf2:120000:4:bad:bad"))
    }
}
