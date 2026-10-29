package com.example.myapplication.Design.screens

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.myapplication.MainActivity
import com.example.myapplication.data.DataStoreManager
import com.example.myapplication.security.AuthSession
import com.example.myapplication.service.AppLockServiceHolder

/** Presentation only. App authentication grants and launch recovery belong to the service. */
class LockScreenActivity : AppCompatActivity() {
    private var target: String? = null
    private var leaving = false
    private val version = mutableIntStateOf(0)
    private val authenticationUiReady = mutableStateOf(false)
    private lateinit var camera: IntruderCamera

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeInstance = this
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        lifecycleScope.launch { com.example.myapplication.AppLanguageManager.restoreLanguage(this@LockScreenActivity) }
        camera = IntruderCamera(this, DataStoreManager(this))
        target = intent.getStringExtra("packageName")
        if (target.isNullOrBlank() || !AppLockServiceHolder.isConnected.value) { goHome(); return }
        if (!AppLockServiceHolder.isChallengeRequired(target!!)) { finishObsoleteChallenge(); return }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        setContent {
            key(version.intValue) {
                UnlockScreen(
                    biometricReady = authenticationUiReady.value,
                    onUnlockSuccess = ::unlock,
                    onFingerprintRequest = { camera.cancelCapture(); showFingerprintPrompt(this) { unlock() } },
                    onIntruderCapture = { camera.capture() },
                    onForgotPasswordSuccess = ::resetPassword
                )
            }
        }
    }

    private fun unlock() {
        if (leaving || isFinishing || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val pkg = target ?: return
        if (!AppLockServiceHolder.authenticate(pkg)) { goHome(); return }
        camera.cancelCapture()
        leaving = true
        // Resume the actual task, preserving its deep link and current screen.
        finishAndRemoveTask()
    }

    private fun resetPassword() {
        if (leaving || !AuthSession.hasResetAuthorization) return
        camera.cancelCapture()
        leaving = true
        target?.let(AppLockServiceHolder::dismiss)
        startActivity(Intent(this, MainActivity::class.java).putExtra("openResetPassword", true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finishAndRemoveTask()
    }

    private fun goHome() {
        if (leaving) return
        camera.cancelCapture()
        leaving = true
        runCatching {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        target?.let(AppLockServiceHolder::dismiss)
        finishAndRemoveTask()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val pkg = intent.getStringExtra("packageName")
        if (pkg.isNullOrBlank()) { goHome(); return }
        if (!AppLockServiceHolder.isChallengeRequired(pkg)) {
            if (target?.let(AppLockServiceHolder::isChallengeRequired) != true) finishObsoleteChallenge()
            return
        }
        setIntent(intent)
        cancelFingerprintPrompt(this)
        target = pkg; leaving = false; activeInstance = this
        authenticationUiReady.value = false
        version.intValue++
        acknowledgeDraw()
    }

    override fun onResume() {
        super.onResume()
        if (leaving) return
        if (target?.let(AppLockServiceHolder::isChallengeRequired) != true) { finishObsoleteChallenge(); return }
        authenticationUiReady.value = false
        acknowledgeDraw()
    }
    private fun finishObsoleteChallenge() {
        leaving = true
        authenticationUiReady.value = false
        camera.cancelCapture()
        cancelFingerprintPrompt(this)
        finishAndRemoveTask()
    }
    private fun acknowledgeDraw() {
        val pkg = target ?: return
        val view = window.decorView
        val visible = {
            if (activeInstance === this && !leaving && !isFinishing && target == pkg &&
                AppLockServiceHolder.isChallengeRequired(pkg) &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                AppLockServiceHolder.onLockVisible(pkg)
                authenticationUiReady.value = true
            }
        }
        view.doOnPreDraw {
            if (Build.VERSION.SDK_INT >= 29) view.viewTreeObserver.registerFrameCommitCallback { runOnUiThread { visible() } }
            else view.postOnAnimation { visible() }
        }
    }
    override fun onStop() {
        if (!isChangingConfigurations && activeInstance === this) target?.let(AppLockServiceHolder::onLockHidden)
        super.onStop()
    }
    override fun onDestroy() {
        if (activeInstance === this) activeInstance = null
        super.onDestroy()
    }
    companion object {
        var activeInstance: LockScreenActivity? = null
            private set
    }
}
