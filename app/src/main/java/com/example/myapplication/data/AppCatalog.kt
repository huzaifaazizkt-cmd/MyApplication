package com.example.myapplication.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

@Immutable
data class AppListItem(val packageName: String, val appName: String, val searchName: String, val iconVersion: Long)
data class AppCatalogState(val apps: List<AppListItem> = emptyList(), val loading: Boolean = true, val failed: Boolean = false)

/** Process-scoped catalog: Android objects and package queries stay outside composition. */
object AppCatalog {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val generation = AtomicLong()
    private var loadedGeneration = -1L
    private var loadedLocale = ""
    private var loadedHome: String? = null
    private var registered = false
    private val snapshot = MutableStateFlow(AppCatalogState())
    val state = snapshot.asStateFlow()

    @Synchronized private fun register(context: Context) {
        if (registered) return
        val app = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                generation.incrementAndGet()
                AppIcons.clear()
                refresh(app)
            }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(app, receiver, IntentFilter(Intent.ACTION_LOCALE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true // Application lifetime; this receiver never retains an Activity.
    }

    fun refresh(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        register(app)
        if (force) generation.incrementAndGet()
        scope.launch {
            mutex.withLock {
                val locale = app.resources.configuration.locales.toLanguageTags()
                val requested = generation.get()
                try {
                    val pm = app.packageManager
                    val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
                    if (loadedGeneration == requested && loadedLocale == locale && loadedHome == home && !snapshot.value.failed) return@withLock
                    val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
                        .mapNotNull { it.activityInfo?.applicationInfo }
                        .distinctBy { it.packageName }
                        .filter { it.packageName != app.packageName && it.packageName != home }
                        .mapNotNull { info ->
                            try {
                                val name = pm.getApplicationLabel(info).toString()
                                val version = (File(info.sourceDir).lastModified() + requested) * 31 + locale.hashCode()
                                AppListItem(info.packageName, name, name.lowercase(Locale.ROOT), version)
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { null }
                        }.sortedBy { it.searchName }
                    snapshot.value = AppCatalogState(apps, loading = false)
                    loadedGeneration = requested
                    loadedLocale = locale
                    loadedHome = home
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { snapshot.value = snapshot.value.copy(loading = false, failed = true) }
            }
        }
    }
}

object AppIcons {
    private val workers = Semaphore(2)
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 32 / 1024).toInt().coerceIn(1024, 8192)) {
        override fun sizeOf(key: String, value: Bitmap) = (value.allocationByteCount / 1024).coerceAtLeast(1)
    }
    fun clear() = cache.evictAll()
    suspend fun load(context: Context, item: AppListItem, pixels: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = item.packageName + ":" + item.iconVersion + ":" + pixels
        cache.get(key) ?: workers.withPermit {
            cache.get(key) ?: try {
                context.packageManager.getApplicationIcon(item.packageName).toBitmap(pixels, pixels)
                    .also { cache.put(key, it) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        }
    }
}
