package com.example.myapplication

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * Xiaomi / Redmi / POCO (MIUI / HyperOS) ki
 * "Display pop-up windows while running in the background" permission.
 *
 * Is ke baghair accessibility service background se LockScreenActivity nahi khol sakti.
 */
object XiaomiPermissionHelper {

    private const val XIAOMI_BACKGROUND_POPUP_APP_OP = 10021
    private const val SECURITY_CENTER = "com.miui.securitycenter"

    data class PopupAccess(
        val required: Boolean,
        val granted: Boolean
    ) {
        /** Dialog mein "Allowed" dikhane ke liye: required nahi ho to bhi true. */
        val satisfied: Boolean get() = !required || granted
    }

    fun isXiaomi(): Boolean =
        Build.MANUFACTURER.equals("xiaomi", ignoreCase = true) ||
                Build.BRAND.equals("xiaomi", ignoreCase = true) ||
                Build.BRAND.equals("redmi", ignoreCase = true) ||
                Build.BRAND.equals("poco", ignoreCase = true)

    /**
     * Permission ki halat check karta hai.
     * Xiaomi na ho, ya AppOps se value na mil sake to required = false (dialog mein row nahi aati).
     */
    fun backgroundPopupAccess(context: Context): PopupAccess {
        val notRequired = PopupAccess(required = false, granted = true)

        if (!isXiaomi()) return notRequired

        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return notRequired

        val mode = runCatching {
            val method = appOps.javaClass.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            (method.invoke(
                appOps,
                XIAOMI_BACKGROUND_POPUP_APP_OP,
                Process.myUid(),
                context.packageName
            ) as Number).toInt()
        }.getOrNull() ?: return notRequired

        return PopupAccess(
            required = true,
            granted = mode == AppOpsManager.MODE_ALLOWED
        )
    }

    private fun permissionEditorIntents(context: Context): List<Intent> {
        val packageName = context.packageName

        return listOf(
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName(
                    SECURITY_CENTER,
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                .putExtra("extra_pkgname", packageName),

            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName(
                    SECURITY_CENTER,
                    "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
                )
                .putExtra("extra_pkgname", packageName),

            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setPackage(SECURITY_CENTER)
                .putExtra("extra_pkgname", packageName)
        )
    }

    /**
     * Permission wali screen kholta hai. Pehle MIUI ki permission editor,
     * na khule to app ki normal settings. Kuch khul jaye to true.
     */
    fun openBackgroundPopupSettings(context: Context): Boolean {
        for (intent in permissionEditorIntents(context)) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // agla intent try karte hain
            }
        }

        return try {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}