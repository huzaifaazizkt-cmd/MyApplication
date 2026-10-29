package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.myapplication.R
import com.example.myapplication.data.AppCatalog
import com.example.myapplication.data.AppListItem
import com.example.myapplication.data.DataStoreManager
import com.example.myapplication.service.AppLockAccess
import com.example.myapplication.service.AppLockPermissions
import com.example.myapplication.service.AppLockServiceController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class AppListState(val apps: List<AppListItem> = emptyList(), val locked: Set<String> = emptySet(),
    val loading: Boolean = true, val failed: Boolean = false, val protectionEnabled: Boolean = true)

class AppListViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val app: Application = application
    private val store = DataStoreManager(app)
    private val actions = Mutex()
    private val notices = Channel<Int>(Channel.BUFFERED)
    val messages = notices.receiveAsFlow()
    val access = MutableStateFlow<AppLockAccess?>(null)
    val showPermissions = saved.getStateFlow("app_list_permission_dialog", false)
    val state = combine(AppCatalog.state, store.lockedAppsFlow, store.getAppProtectionEnabled()) { catalog, locked, enabled ->
        AppListState(catalog.apps, locked, catalog.loading, catalog.failed, enabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppListState())

    init { refresh() }
    fun refresh(force: Boolean = false) {
        AppCatalog.refresh(app, force)
        viewModelScope.launch {
            actions.withLock {
                val permissions = withContext(Dispatchers.IO) { AppLockPermissions.read(app) }
                access.value = permissions
                if (permissions.ready && showPermissions.value) {
                    val pending = saved.get<String>("app_list_pending_package")
                    val installed = pending != null && withContext(Dispatchers.IO) {
                        app.packageManager.getLaunchIntentForPackage(pending) != null
                    }
                    if (pending != null && installed) {
                        update(pending, locked = true)
                    }
                    dismissPermissions()
                }
            }
        }
    }
    fun requestPermissions() { saved["app_list_permission_dialog"] = true; refresh() }
    fun dismissPermissions() {
        saved["app_list_pending_package"] = null
        saved["app_list_permission_dialog"] = false
    }
    fun setLocked(packageName: String, locked: Boolean) {
        // Ignore another tap until this write (including permission verification) has completed.
        if (actions.isLocked) return
        viewModelScope.launch {
            actions.withLock {
                if (locked) {
                    val permissions = withContext(Dispatchers.IO) { AppLockPermissions.read(app) }
                    access.value = permissions
                    if (!permissions.ready) {
                        saved["app_list_pending_package"] = packageName
                        saved["app_list_permission_dialog"] = true
                        return@withLock
                    }
                }
                update(packageName, locked)
            }
        }
    }
    private suspend fun update(packageName: String, locked: Boolean) {
        try {
            if (locked) store.saveLockedApp(packageName) else store.removeLockedApp(packageName)
            AppLockServiceController.sync(app)
            if (locked) notices.send(R.string.app_locked_success)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { notices.send(R.string.app_lock_update_failed) }
    }
}
