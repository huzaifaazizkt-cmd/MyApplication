package com.example.myapplication.Design.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.edit
import com.example.myapplication.R
import com.example.myapplication.data.DataStoreManager
import com.example.myapplication.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ProtectionStatusMessage(protectionEnabled: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lockedFlow = remember(context) { DataStoreManager(context).lockedAppsFlow }
    val locked by lockedFlow.collectAsStateWithLifecycle(initialValue = emptySet())
    val service by AppLockServiceHolder.state.collectAsStateWithLifecycle()
    val notifications by AppLockServiceHolder.notificationState.collectAsStateWithLifecycle()
    var access by remember { mutableStateOf(AppLockAccess()) }
    var showPermissions by remember { mutableStateOf(false) }
    var reliability by remember { mutableStateOf<Intent?>(null) }
    val prefs = remember(context) { context.getSharedPreferences("protection_guidance", 0) }
    var guideReviewed by remember { mutableStateOf(prefs.getBoolean("reviewed", false)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scope.launch {
                access = withContext(Dispatchers.IO) { AppLockPermissions.read(context) }
                reliability = withContext(Dispatchers.IO) { AppLockPermissions.reliabilityIntent(context) }
                if (access.ready) showPermissions = false
                AppLockServiceHolder.refreshNotification()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (!protectionEnabled || locked.isEmpty()) return
    val message = when {
        !access.ready -> R.string.protection_service_required
        service == ProtectionStatus.SETTINGS_UNAVAILABLE -> R.string.secure_settings_unavailable
        service == ProtectionStatus.MONITOR_UNAVAILABLE -> R.string.protection_monitor_unavailable
        service == ProtectionStatus.START_FAILED || service == ProtectionStatus.DISCONNECTED -> R.string.protection_start_retry
        service != ProtectionStatus.READY -> R.string.protection_connecting
        notifications != ProtectionNotificationStatus.AVAILABLE -> R.string.protection_notifications_required
        !guideReviewed && reliability != null -> R.string.protection_reliability_tip
        else -> null
    }
    if (message != null) Text(stringResource(message), color = Color(0xFF666666),
        modifier = Modifier.padding(12.dp).clickable {
            when (message) {
                R.string.protection_service_required -> showPermissions = true
                R.string.protection_notifications_required -> runCatching {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }.onFailure { Toast.makeText(context, R.string.external_action_failed, Toast.LENGTH_SHORT).show() }
                R.string.protection_reliability_tip -> runCatching {
                    reliability?.let { context.startActivity(it) }
                    prefs.edit { putBoolean("reviewed", true) }; guideReviewed = true
                }.onFailure { Toast.makeText(context, R.string.external_action_failed, Toast.LENGTH_SHORT).show() }
                else -> scope.launch { runCatching { AppLockServiceController.sync(context) } }
            }
        })
    if (showPermissions) PermissionRequiredDialog(access) { showPermissions = false }
}
