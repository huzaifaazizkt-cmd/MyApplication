package com.example.myapplication.service

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.example.myapplication.XiaomiPermissionHelper
import java.util.Locale

data class AppLockAccess(
    val usage: Boolean = false,
    val overlay: Boolean = false,
    val popupRequired: Boolean = false,
    val popupGranted: Boolean = true
) {
    val ready: Boolean get() = usage && overlay && (!popupRequired || popupGranted)
}

/** Only access needed by the monitor belongs in the blocking permission gate. */
object AppLockPermissions {
    fun hasUsageAccess(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun read(context: Context): AppLockAccess {
        val popup = XiaomiPermissionHelper.backgroundPopupAccess(context)
        return AppLockAccess(hasUsageAccess(context), Settings.canDrawOverlays(context),
            popup.required && popup.verified, popup.granted)
    }

    fun usageIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    fun overlayIntent(context: Context): Intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        ("package:" + context.packageName).toUri())

    fun reliabilityIntent(context: Context): Intent? {
        val candidates = when (Build.MANUFACTURER.lowercase(Locale.ROOT)) {
            "vivo", "iqoo" -> listOf(
                "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
            "xiaomi", "redmi", "poco" -> listOf(
                "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity")
            "oppo", "realme", "oneplus" -> listOf(
                "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                "com.oplus.safecenter" to "com.oplus.safecenter.startupapp.StartupAppListActivity")
            "huawei", "honor" -> listOf(
                "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            else -> emptyList()
        }
        candidates.forEach { (pkg, activity) ->
            val intent = Intent().setComponent(ComponentName(pkg, activity))
            if (context.packageManager.resolveActivity(intent, 0)?.activityInfo?.exported == true) return intent
        }
        if (!context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)) {
            return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .takeIf { it.resolveActivity(context.packageManager) != null }
        }
        return null
    }
}
