package com.example.myapplication

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

object XiaomiPermissionHelper {
    private const val SECURITY_CENTER = "com.miui.securitycenter"
    data class PopupAccess(val required: Boolean, val granted: Boolean, val verified: Boolean = true) {
        val satisfied: Boolean get() = !required || (verified && granted)
    }
    fun isXiaomi(): Boolean = listOf(Build.MANUFACTURER, Build.BRAND).any {
        it.equals("xiaomi", true) || it.equals("redmi", true) || it.equals("poco", true)
    }
    private fun editors(context: Context): List<Intent> = listOf(
        "com.miui.permcenter.permissions.PermissionsEditorActivity",
        "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
    ).map { Intent("miui.intent.action.APP_PERM_EDITOR").setClassName(SECURITY_CENTER, it)
        .putExtra("extra_pkgname", context.packageName) }

    fun backgroundPopupAccess(context: Context): PopupAccess {
        if (!isXiaomi()) return PopupAccess(false, true)
        if (editors(context).none { context.packageManager.resolveActivity(it, 0)?.activityInfo?.exported == true }) {
            return PopupAccess(false, false, false)
        }
        val mode = runCatching {
            val ops = context.getSystemService(AppOpsManager::class.java)
            val method = ops.javaClass.getMethod("checkOpNoThrow", Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, String::class.java)
            (method.invoke(ops, 10021, Process.myUid(), context.packageName) as Number).toInt()
        }.getOrNull() ?: return PopupAccess(false, false, false)
        // An unavailable/private vendor API is not grounds for trapping the user in setup.
        return PopupAccess(true, mode == AppOpsManager.MODE_ALLOWED)
    }
    fun openBackgroundPopupSettings(context: Context): Boolean {
        val candidates = editors(context) + Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + context.packageName))
        return candidates.any { runCatching {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        }.getOrDefault(false) }
    }
}
