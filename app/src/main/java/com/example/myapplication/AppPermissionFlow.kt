package com.example.myapplication

import android.content.Context

object AppPermissionFlow {

    const val PREFS_NAME = "applock_permission_flow"
    const val KEY_PENDING = "permission_setup_pending"
    const val KEY_TYPE = "pending_confirm_type"
    const val KEY_VALUE = "pending_confirm_value"

    fun savePendingSetup(
        context: Context,
        type: String
    ) {
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putBoolean(KEY_PENDING, true)
            .putString(KEY_TYPE, type)
            .remove(KEY_VALUE)
            .apply()
    }

    fun isPending(context: Context): Boolean {
        return context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).getBoolean(KEY_PENDING, false)
    }

    fun getPendingType(context: Context): String? {
        return context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).getString(KEY_TYPE, null)
    }

    fun removeLegacyCredential(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(KEY_VALUE).apply()
    }

    fun clearPendingSetup(context: Context) {
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .remove(KEY_PENDING)
            .remove(KEY_TYPE)
            .remove(KEY_VALUE)
            .apply()
    }

    /**
     * Agar setup adhoora hai to app ko permission screen se resume karein.
     */
    fun getPendingPermissionDestination(context: Context): String? {
        if (!isPending(context)) return null

        return "permissionGate"
    }
}
