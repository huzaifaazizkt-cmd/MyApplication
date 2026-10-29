package com.example.myapplication

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.security.FastCredentialVerifier
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FastCredentialVerifierTest {
    @Test fun verifiesWithoutAcceptingWrongCredentialsAndFallsBackAfterKeyOrVerifierChanges() {
        val alias = "qa-credential-${UUID.randomUUID()}"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        try {
            val verifier = FastCredentialVerifier(alias)
            val record = verifier.encode("pin", "salted-original", "1234")
            assumeNotNull(record)
            assertEquals(true, verifier.matches("1234", "pin", "salted-original", record))
            assertEquals(false, verifier.matches("4321", "pin", "salted-original", record))
            assertNull(verifier.matches("1234", "pattern", "salted-original", record))
            assertNull(verifier.matches("1234", "pin", "salted-updated", record))
            assertNull(verifier.matches("1234", "pin", "salted-original", "invalid"))
            store.deleteEntry(alias)
            assertNull(FastCredentialVerifier(alias).matches("1234", "pin", "salted-original", record))
        } finally {
            store.deleteEntry(alias)
        }
    }
}
