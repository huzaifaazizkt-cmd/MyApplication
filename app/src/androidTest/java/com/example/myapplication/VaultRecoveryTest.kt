package com.example.myapplication

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.vault.VaultRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class VaultRecoveryTest {
    @Test fun completedUnindexedFilesAreRecoveredButPartialCopiesAreNot() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(base.cacheDir, "vault-recovery-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(base) {
            override fun getFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("qa-recovery-${directory.name}-$name", mode)
        }
        val constructor = VaultRepository::class.java.getDeclaredConstructor(Context::class.java).apply {
            isAccessible = true
        }
        try {
            val photos = File(directory, "vault_photos").apply { mkdirs() }
            val completed = File(photos, "completed.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            File(photos, "incomplete.jpg.part").writeBytes(byteArrayOf(1))
            // Covers both an absent initial index and a valid index missing the latest commit.
            for (hasIndex in listOf(false, true)) {
                if (hasIndex) File(directory, "vault-index.json").writeText("[]")
                val repository = constructor.newInstance(isolated)
                val items = repository.snapshot()
                assertEquals(1, items.size)
                assertEquals(completed.absolutePath, items.single().vaultFilePath)
                assertArrayEquals(byteArrayOf(1, 2, 3), completed.readBytes())
            }
        } finally {
            directory.deleteRecursively()
            base.deleteSharedPreferences("qa-recovery-${directory.name}-vault_hidden_media")
        }
    }
}
