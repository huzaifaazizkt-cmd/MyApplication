package com.example.myapplication.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager

/**
 * Locked app ke upar foran ek solid white parda (overlay) laga deta hai,
 * taake lock screen khulne tak app ka content bilkul nazar na aaye.
 *
 * Use:
 *   AppLockService mein, LockScreenActivity start karne se PEHLE:  LockOverlay.show(this)
 *   LockScreenActivity mein, screen nazar aane ke baad:           LockOverlay.hide()
 *
 * Zaroori: "Display over other apps" (SYSTEM_ALERT_WINDOW) permission.
 */
object LockOverlay {
    private const val TAG = "LOCK_OVERLAY"
    private const val AUTO_HIDE_MS = 2500L

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    private val autoHide = Runnable { hide() }

    fun show(context: Context) {
        handler.post {
            if (overlayView != null) return@post

            if (!Settings.canDrawOverlays(context)) {
                Log.d(TAG, "NO OVERLAY PERMISSION")
                return@post
            }

            try {
                val appContext = context.applicationContext
                val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

                val view = View(appContext).apply {
                    setBackgroundColor(Color.WHITE)
                }

                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.OPAQUE
                )

                wm.addView(view, params)
                windowManager = wm
                overlayView = view

                // Safety: agar lock screen kisi wajah se na khule to parda atka na rahe
                handler.removeCallbacks(autoHide)
                handler.postDelayed(autoHide, AUTO_HIDE_MS)

                Log.d(TAG, "OVERLAY SHOWN")
            } catch (e: Exception) {
                Log.e(TAG, "OVERLAY SHOW ERROR", e)
                overlayView = null
            }
        }
    }

    fun hide() {
        handler.post {
            handler.removeCallbacks(autoHide)

            val view = overlayView ?: return@post
            try {
                windowManager?.removeViewImmediate(view)
                Log.d(TAG, "OVERLAY HIDDEN")
            } catch (e: Exception) {
                Log.e(TAG, "OVERLAY HIDE ERROR", e)
            } finally {
                overlayView = null
                windowManager = null
            }
        }
    }
}