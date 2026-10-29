package com.example.myapplication.vault

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

internal object MediaPermissions {
    fun canRead(context: Context, video: Boolean): Boolean = granted(context, readPermission(video)) ||
        (Build.VERSION.SDK_INT >= 34 && granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
    fun canWriteLegacy(context: Context) = Build.VERSION.SDK_INT >= 30 || granted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun requested(video: Boolean): Array<String> = buildList {
        add(readPermission(video))
        if (Build.VERSION.SDK_INT >= 34) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        if (Build.VERSION.SDK_INT <= 29) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }.toTypedArray()
    private fun readPermission(video: Boolean) = if (Build.VERSION.SDK_INT >= 33)
        if (video) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
