package com.example.myapplication

import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.vault.VaultMediaItem
import com.example.myapplication.vault.VaultRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30) // App-only MediaStore tests; legacy write permission stays untouched.
class VaultTransferTest {
    @Test fun failedSecondItemCannotDestroyTheFirstCopy() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val name = "AppLock_QA_${UUID.randomUUID()}.png"
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val source = resolver.insert(collection, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        })!!
        val bytes = java.io.ByteArrayOutputStream().use { output ->
            val image = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
            image.eraseColor(android.graphics.Color.BLUE)
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
            image.recycle()
            output.toByteArray()
        }
        resolver.openOutputStream(source)!!.use { it.write(bytes) }
        val repository = VaultRepository.get(context)
        try {
            val valid = VaultMediaItem(source, name, "AppLockQA", false)
            val missing = VaultMediaItem(Uri.parse("content://media/external/images/media/9223372036854775800"), "missing.jpg", "AppLockQA", false)
            val result = repository.hide(listOf(valid, missing))
            assertEquals(1, result.failures)
            val saved = repository.items.value.single { it.name == name }
            assertArrayEquals(bytes, File(saved.vaultFilePath!!).readBytes())
            resolver.query(source, null, null, null, null)!!.use { assertFalse(it.moveToFirst()) }
            assertEquals(0, repository.restore(listOf(saved)).failures)
            assertFalse(repository.items.value.any { it.name == name })
            resolver.query(collection, arrayOf(MediaStore.Images.Media._ID), "${MediaStore.Images.Media.DISPLAY_NAME}=?", arrayOf(name), null)!!.use {
                assertTrue(it.moveToFirst())
                val restored = ContentUris.withAppendedId(collection, it.getLong(0))
                assertArrayEquals(bytes, resolver.openInputStream(restored)!!.use { input -> input.readBytes() })
            }
        } finally {
            repository.items.value.filter { it.name == name }.takeIf { it.isNotEmpty() }?.let { repository.restore(it) }
            resolver.delete(collection, "${MediaStore.Images.Media.DISPLAY_NAME}=?", arrayOf(name))
        }
    }
}
