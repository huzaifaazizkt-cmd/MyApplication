package com.example.myapplication.vault

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Serialized, journaled transfers. UI cancellation cannot interrupt copy/commit/delete ordering. */
class VaultRepository private constructor(private val context: Context) {
    private val mutex = Mutex()
    private val metadata = AtomicFile(File(context.filesDir, "vault-index.json"))
    private val state = MutableStateFlow<List<VaultMediaItem>>(emptyList())
    val items = state.asStateFlow()
    private val working = MutableStateFlow(true)
    val isBusy = working.asStateFlow()
    private val ready = CoroutineScope(SupervisorJob() + Dispatchers.IO).async {
        try { state.value = readIndex() } finally { working.value = false }
    }
    suspend fun snapshot(): List<VaultMediaItem> { ready.await(); return state.value }

    data class Result(val failures: Int = 0, val consent: PendingIntent? = null)

    suspend fun hide(sources: List<VaultMediaItem>): Result = transaction {
            var failures = 0
            var consent: PendingIntent? = null
            for (source in sources) {
                try {
                    val existing = state.value.firstOrNull { it.uri == source.uri && it.pendingOriginalDelete }
                    if (existing != null) finishImport(source, existing)
                    else importSafely(
                        stage = { copyToVault(source) },
                        commit = { save(state.value + it) },
                        discardUncommitted = { File(it.vaultFilePath!!).delete() },
                        deleteOriginal = { copy -> finishImport(source, copy) }
                    )
                } catch (error: Exception) {
                    failures++
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && error is RecoverableSecurityException) {
                        consent = consent ?: error.userAction.actionIntent
                    }
                    Log.w(TAG, "Could not finish hiding an item; committed copies retained", error)
                }
            }
            Result(failures, consent)
    }

    suspend fun restore(selected: List<VaultMediaItem>): Result = transaction {
            var failures = 0
            for (selectedItem in selected) {
                val item = state.value.firstOrNull { it.vaultFilePath == selectedItem.vaultFilePath } ?: continue
                try { restoreOne(item) } catch (error: Exception) {
                    failures++
                    Log.w(TAG, "Could not restore an item; vault copy retained", error)
                }
            }
            Result(failures)
    }

    private suspend fun transaction(block: () -> Result): Result = withContext(NonCancellable + Dispatchers.IO) {
        try {
            ready.await()
            mutex.withLock {
                working.value = true
                try { block() } finally { working.value = false }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Vault is unavailable; files were retained", error)
            Result(failures = 1)
        }
    }

    private fun finishImport(source: VaultMediaItem, copy: VaultMediaItem) {
        check(context.contentResolver.delete(source.uri, null, null) > 0 || originalExists(source.uri) == false) {
            "Original could not be deleted; keeping the vault copy"
        }
        save(state.value.map { if (it.vaultFilePath == copy.vaultFilePath) it.copy(pendingOriginalDelete = false) else it })
    }

    private fun copyToVault(source: VaultMediaItem): VaultMediaItem {
        val dir = File(context.filesDir, if (source.isVideo) "vault_videos" else "vault_photos").apply { mkdirs() }
        val extension = source.name.substringAfterLast('.', "bin").takeIf { it.matches(Regex("[A-Za-z0-9]{1,10}")) } ?: "bin"
        val finalFile = File(dir, "${UUID.randomUUID()}.$extension")
        val staging = File(dir, finalFile.name + ".part")
        try {
            val descriptor = context.contentResolver.openAssetFileDescriptor(source.uri, "r") ?: error("Source unavailable")
            descriptor.use {
                val expected = it.length
                val count = it.createInputStream().use { input ->
                    FileOutputStream(staging).use { output -> input.copyTo(output).also { output.fd.sync() } }
                }
                check(count > 0 && (expected < 0 || count == expected)) { "Incomplete vault copy" }
            }
            check(staging.renameTo(finalFile)) { "Cannot commit vault file" }
            return source.copy(isVaultFile = true, vaultFilePath = finalFile.absolutePath, pendingOriginalDelete = true)
        } finally { staging.delete() }
    }

    private fun restoreOne(initial: VaultMediaItem) {
        val file = checkedFile(initial) ?: error("Vault file missing")
        val collection = if (initial.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val album = initial.bucketName.replace('/', '_').replace('\\', '_').trim().ifBlank { "Restored" }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, initial.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType(initial))
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${if (initial.isVideo) "Movies" else "Pictures"}/$album")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val destination = initial.restoreUri?.let(Uri::parse)?.takeIf { originalExists(it) == true }
            ?: context.contentResolver.insert(collection, values) ?: error("Cannot create gallery item")
        // Persist destination first, so a retry overwrites the same output after a process interruption.
        try {
            save(state.value.map { if (it.vaultFilePath == initial.vaultFilePath) it.copy(restoreUri = destination.toString()) else it })
            context.contentResolver.openFileDescriptor(destination, "rwt")?.use { descriptor ->
                FileOutputStream(descriptor.fileDescriptor).use { output ->
                    file.inputStream().use { input -> check(input.copyTo(output) == file.length()) }
                    output.fd.sync()
                }
            } ?: error("Gallery output unavailable")
            if (Build.VERSION.SDK_INT >= 29) {
                check(context.contentResolver.update(destination, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null) > 0) { "Cannot publish gallery item" }
            }
            // Gallery is complete before either the vault file or its index entry is removed.
            check(file.delete()) { "Cannot remove restored vault file" }
            save(state.value.filterNot { it.vaultFilePath == initial.vaultFilePath })
        } catch (error: Exception) {
            // Keep a journaled destination and the source. A future retry is safe and idempotent.
            throw error
        }
    }

    private fun originalExists(uri: Uri): Boolean? = try {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.moveToFirst() }
    } catch (_: Exception) { null } // An access error is not proof of existence or deletion.

    private fun checkedFile(item: VaultMediaItem): File? {
        val file = item.vaultFilePath?.let(::File)?.canonicalFile ?: return null
        val allowed = File(context.filesDir, if (item.isVideo) "vault_videos" else "vault_photos").canonicalFile
        return file.takeIf { it.parentFile == allowed && it.isFile }
    }

    private fun save(updated: List<VaultMediaItem>) {
        val json = JSONArray().apply { updated.forEach { item -> put(JSONObject().apply {
            put("uri", item.uri.toString()); put("name", item.name); put("album", item.bucketName)
            put("video", item.isVideo); put("path", item.vaultFilePath)
            put("pendingDelete", item.pendingOriginalDelete); put("restoreUri", item.restoreUri)
        }) } }
        val stream = metadata.startWrite()
        try {
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            metadata.finishWrite(stream)
        } catch (error: Exception) { metadata.failWrite(stream); throw error }
        state.value = updated
    }

    private fun readIndex(): List<VaultMediaItem> {
        val indexed = if (!metadata.baseFile.exists() && !File(metadata.baseFile.path + ".bak").exists()) {
            val prefs = context.getSharedPreferences("vault_hidden_media", Context.MODE_PRIVATE)
            (0 until prefs.getInt("count", 0)).mapNotNull { i ->
                val uri = prefs.getString("uri_$i", null) ?: return@mapNotNull null
                VaultMediaItem(Uri.parse(uri), prefs.getString("name_$i", "Media")!!,
                    prefs.getString("bucket_$i", "Restored")!!, prefs.getBoolean("video_$i", false), true,
                    prefs.getString("path_$i", null)).takeIf { checkedFile(it) != null }
            }
        } else try {
            val array = JSONArray(metadata.openRead().bufferedReader().use { it.readText() })
            (0 until array.length()).mapNotNull { i -> array.getJSONObject(i).let { json ->
                VaultMediaItem(Uri.parse(json.getString("uri")), json.getString("name"), json.getString("album"),
                    json.getBoolean("video"), true, json.getString("path"), json.optBoolean("pendingDelete"),
                    json.optString("restoreUri").takeUnless { it.isBlank() || it == "null" })
                    .takeIf { checkedFile(it) != null }
            } }
        } catch (error: Exception) {
            // Do not silently replace an unreadable index with an empty one.
            Log.e(TAG, "Vault index unreadable", error)
            // Retain the damaged index for recovery and expose every intact private file.
            runCatching { metadata.baseFile.copyTo(File(context.filesDir, "vault-index.corrupt-${System.currentTimeMillis()}.json")) }
            emptyList()
        }
        // A process can die after the completed file is renamed but before its index commit.
        // Reconcile private files even when the existing index is valid or absent.
        val knownPaths = indexed.mapNotNull { it.vaultFilePath?.let(::File)?.canonicalPath }.toSet()
        val recovered = listOf(false, true).flatMap { video ->
            File(context.filesDir, if (video) "vault_videos" else "vault_photos").listFiles().orEmpty()
                .filter { it.isFile && !it.name.endsWith(".part") && it.canonicalPath !in knownPaths }
                .map { VaultMediaItem(Uri.fromFile(it), it.name, "Recovered", video, true, it.absolutePath) }
                .filter { checkedFile(it) != null }
        }
        return indexed + recovered
    }

    private fun mimeType(item: VaultMediaItem): String {
        val extension = item.name.substringAfterLast('.', "").lowercase()
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: if (item.isVideo) "video/mp4" else "image/jpeg"
    }
    companion object {
        private const val TAG = "VaultRepository"
        // The private constructor is called only with applicationContext, never an Activity.
        @android.annotation.SuppressLint("StaticFieldLeak")
        @Volatile private var instance: VaultRepository? = null
        fun get(context: Context): VaultRepository = instance ?: synchronized(this) {
            instance ?: VaultRepository(context.applicationContext).also { instance = it }
        }
    }
}
