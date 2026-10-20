package com.example.myapplication.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Temporary opaque cover placed over a locked app while LockScreenActivity
 * prepares and draws the PIN/Pattern UI.
 */
object AppLaunchPrivacyGuard {
    private const val TAG = "AppLaunchPrivacyGuard"
    private const val SAFETY_TIMEOUT_MS = 15_000L
    private val handler = Handler(Looper.getMainLooper())
    private var coverView: View? = null
    private var windowManager: WindowManager? = null

    private val safetyHideRunnable = Runnable { hide() }

    @Synchronized
    fun show(service: AccessibilityService) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { show(service) }
            return
        }

        handler.removeCallbacks(safetyHideRunnable)
        if (coverView != null) {
            handler.postDelayed(safetyHideRunnable, SAFETY_TIMEOUT_MS)
            return
        }

        try {
            val manager = service.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
            val cover = View(service).apply { setBackgroundColor(Color.WHITE); isClickable = true }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            manager.addView(cover, params)
            windowManager = manager
            coverView = cover
            handler.postDelayed(safetyHideRunnable, SAFETY_TIMEOUT_MS)
            Log.d(TAG, "PRIVACY COVER SHOWN")
        } catch (e: Exception) {
            coverView = null
            windowManager = null
            Log.e(TAG, "PRIVACY COVER SHOW FAILED", e)
        }
    }

    @Synchronized
    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { hide() }
            return
        }

        handler.removeCallbacks(safetyHideRunnable)
        val cover = coverView ?: return
        coverView = null
        try {
            windowManager?.removeView(cover)
            Log.d(TAG, "PRIVACY COVER HIDDEN")
        } catch (e: Exception) {
            Log.e(TAG, "PRIVACY COVER HIDE FAILED", e)
        } finally {
            windowManager = null
        }
    }
}
