
package com.example.myapplication

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log

object XiaomiPermissionHelper {

    private const val TAG = "APPLOCK_PERMISSION_DEBUG"
    private const val XIAOMI_BACKGROUND_POPUP_APP_OP = 10021
    private const val SECURITY_CENTER = "com.miui.securitycenter"

    data class PopupAccess(
        val required: Boolean,
        val granted: Boolean,
        val verified: Boolean = true
    ) {
        val satisfied: Boolean
            get() = !required || (verified && granted)
    }

    fun isXiaomi(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()

        val result =
            manufacturer.equals("xiaomi", ignoreCase = true) ||
                    brand.equals("xiaomi", ignoreCase = true) ||
                    brand.equals("redmi", ignoreCase = true) ||
                    brand.equals("poco", ignoreCase = true) ||
                    manufacturer.equals("redmi", ignoreCase = true) ||
                    manufacturer.equals("poco", ignoreCase = true)

        Log.d(
            TAG,
            "DEVICE_CHECK: isXiaomi=$result, " +
                    "manufacturer=$manufacturer, brand=$brand, " +
                    "sdk=${Build.VERSION.SDK_INT}"
        )

        return result
    }

    fun backgroundPopupAccess(context: Context): PopupAccess {
        if (!isXiaomi()) {
            val result = PopupAccess(
                required = false,
                granted = true,
                verified = true
            )

            Log.d(TAG, "POPUP_STATUS: permission not required; result=$result")
            return result
        }

        val appOps = context.getSystemService(Context.APP_OPS_SERVICE)
                as? AppOpsManager

        if (appOps == null) {
            Log.e(TAG, "POPUP_STATUS: AppOpsManager unavailable")

            return PopupAccess(
                required = true,
                granted = false,
                verified = false
            )
        }

        return try {
            val method = appOps.javaClass.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )

            val mode = (
                    method.invoke(
                        appOps,
                        XIAOMI_BACKGROUND_POPUP_APP_OP,
                        Process.myUid(),
                        context.packageName
                    ) as Number
                    ).toInt()

            val granted = mode == AppOpsManager.MODE_ALLOWED

            val result = PopupAccess(
                required = true,
                granted = granted,
                verified = true
            )

            Log.d(
                TAG,
                "POPUP_STATUS: package=${context.packageName}, " +
                        "op=$XIAOMI_BACKGROUND_POPUP_APP_OP, " +
                        "mode=$mode, " +
                        "allowedMode=${AppOpsManager.MODE_ALLOWED}, " +
                        "result=$result"
            )

            result
        } catch (e: Exception) {
            Log.e(
                TAG,
                "POPUP_STATUS: unable to read Xiaomi popup permission status",
                e
            )

            PopupAccess(
                required = true,
                granted = false,
                verified = false
            )
        }
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

    fun openBackgroundPopupSettings(context: Context): Boolean {
        Log.d(TAG, "OPEN_POPUP_SETTINGS: opening Xiaomi background popup settings")

        for ((index, intent) in permissionEditorIntents(context).withIndex()) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)

                Log.d(
                    TAG,
                    "OPEN_POPUP_SETTINGS: intent opened, index=$index"
                )

                return true
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "OPEN_POPUP_SETTINGS: intent failed, index=$index",
                    e
                )
            }
        }

        return try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(intent)
            Log.d(TAG, "OPEN_POPUP_SETTINGS: opened app details fallback")
            true
        } catch (e: Exception) {
            Log.e(TAG, "OPEN_POPUP_SETTINGS: could not open settings", e)
            false
        }
    }
}
