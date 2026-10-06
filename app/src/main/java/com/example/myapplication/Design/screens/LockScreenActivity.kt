package com.example.myapplication.Design.screens

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

import com.example.myapplication.MainActivity
import com.example.myapplication.data.DataStoreManager
import com.example.myapplication.service.AppLockServiceHolder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import java.text.SimpleDateFormat
import java.util.Locale

class LockScreenActivity : FragmentActivity() {

    companion object {

        private const val TAG =
            "APPLOCK_DEBUG"

        private const val CAMERA_PERMISSION =
            Manifest.permission.CAMERA

        private const val CAMERA_REQUEST_CODE =
            501
    }

    private var targetPackage: String? =
        null

    private lateinit var dataStore: DataStoreManager

    private var imageCapture: ImageCapture? =
        null

    private var cameraProvider: ProcessCameraProvider? =
        null

    private var biometricPrompt:
            BiometricPrompt? =
        null

    private var biometricStarted =
        false

    /*
     * Prevents multiple unlock callbacks from
     * PIN + fingerprint or repeated UI events.
     */
    private var unlockHandled =
        false

    // =========================================================
    // ON CREATE
    // =========================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        Log.d(
            TAG,
            "================================"
        )

        Log.d(
            TAG,
            "LOCK SCREEN CREATED"
        )

        Log.d(
            TAG,
            "================================"
        )

        dataStore =
            DataStoreManager(this)

        // -----------------------------------------------------
        // SHOW ABOVE LOCK SCREEN
        // -----------------------------------------------------

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O_MR1
        ) {

            setShowWhenLocked(true)

            setTurnScreenOn(true)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        // -----------------------------------------------------
        // TARGET PACKAGE
        // -----------------------------------------------------

        targetPackage =
            intent.getStringExtra(
                "packageName"
            )

        Log.d(
            TAG,
            "TARGET PACKAGE = $targetPackage"
        )

        if (
            targetPackage.isNullOrEmpty()
        ) {

            Log.e(
                TAG,
                "TARGET PACKAGE IS NULL"
            )

            goHome()

            return
        }

        /*
         * Lock screen is definitely visible now.
         *
         * IMPORTANT:
         * Do not set this false from onDestroy().
         * Service controls this state when another lock
         * screen is actually requested.
         */
        AppLockServiceHolder
            .isLockScreenOpen = true

        // -----------------------------------------------------
        // BACK BUTTON
        // -----------------------------------------------------

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {

                    goHome()
                }
            }
        )

        // -----------------------------------------------------
        // UI
        // -----------------------------------------------------

        setContent {

            UnlockScreen(

                onUnlockSuccess = {

                    unlockAndOpenApp()
                },

                onFingerprintRequest = {

                    showBiometricPrompt()
                },

                onIntruderCapture = {

                    captureIntruderPhoto()
                },

                onForgotPasswordSuccess = {

                    openResetPassword()
                }
            )
        }
    }

    // =========================================================
    // RESET PASSWORD
    // =========================================================

    private fun openResetPassword() {

        unlockHandled = true

        AppLockServiceHolder
            .isLockScreenOpen = false

        AppLockServiceHolder
            .currentUnlockedApp = null

        AppLockServiceHolder
            .lastUnlockTime = 0L

        AppLockServiceHolder
            .clearSuppressedPackage()

        val resetIntent =
            Intent(
                this,
                MainActivity::class.java
            ).apply {

                putExtra(
                    "openResetPassword",
                    true
                )

                addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }

        try {

            startActivity(
                resetIntent
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "RESET PASSWORD OPEN ERROR",
                e
            )
        }

        finish()
    }

    // =========================================================
    // UNLOCK APP
    // =========================================================

    private fun unlockAndOpenApp() {

        /*
         * VERY IMPORTANT:
         *
         * UnlockScreen can theoretically call this more than
         * once because of rapid taps / biometric callback.
         *
         * Only first callback is accepted.
         */
        if (unlockHandled) {

            Log.d(
                TAG,
                "UNLOCK IGNORED - ALREADY HANDLED"
            )

            return
        }

        unlockHandled = true

        val packageName =
            targetPackage

        if (
            packageName.isNullOrEmpty()
        ) {

            Log.e(
                TAG,
                "UNLOCK FAILED - PACKAGE NULL"
            )

            AppLockServiceHolder
                .isLockScreenOpen = false

            goHome()

            return
        }

        Log.d(
            TAG,
            "================================"
        )

        Log.d(
            TAG,
            "UNLOCK SUCCESS"
        )

        Log.d(
            TAG,
            "UNLOCKED PACKAGE = $packageName"
        )

        Log.d(
            TAG,
            "================================"
        )

        /*
         * This is the MAIN FIX.
         *
         * Tell AccessibilityService:
         *
         * "This package has just been unlocked.
         * Do not immediately open LockScreenActivity again."
         */
        AppLockServiceHolder
            .suppressPackage(packageName)

        AppLockServiceHolder
            .currentUnlockedApp =
            packageName

        AppLockServiceHolder
            .lastUnlockTime =
            System.currentTimeMillis()

        /*
         * Lock screen is no longer logically active.
         */
        AppLockServiceHolder
            .isLockScreenOpen = false

        openApp()
    }

    // =========================================================
    // BIOMETRIC
    // =========================================================

    private fun showBiometricPrompt() {

        if (biometricStarted) {

            return
        }

        if (unlockHandled) {

            return
        }

        val biometricManager =
            BiometricManager.from(this)

        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK

        val canAuthenticate =
            biometricManager.canAuthenticate(
                authenticators
            )

        if (
            canAuthenticate !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {

            Log.d(
                TAG,
                "BIOMETRIC NOT AVAILABLE"
            )

            return
        }

        val promptInfo =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(
                    "Fingerprint Lock"
                )
                .setSubtitle(
                    "Use your fingerprint to unlock"
                )
                .setNegativeButtonText(
                    "Use PIN / Pattern"
                )
                .build()

        val executor =
            ContextCompat.getMainExecutor(
                this
            )

        biometricPrompt =
            BiometricPrompt(
                this,
                executor,
                object :
                    BiometricPrompt.AuthenticationCallback() {

                    override fun onAuthenticationSucceeded(
                        result:
                        BiometricPrompt.AuthenticationResult
                    ) {

                        super.onAuthenticationSucceeded(
                            result
                        )

                        biometricStarted =
                            false

                        Log.d(
                            TAG,
                            "BIOMETRIC SUCCESS"
                        )

                        unlockAndOpenApp()
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence
                    ) {

                        super.onAuthenticationError(
                            errorCode,
                            errString
                        )

                        biometricStarted =
                            false

                        Log.d(
                            TAG,
                            "BIOMETRIC ERROR = $errString"
                        )
                    }

                    override fun onAuthenticationFailed() {

                        super.onAuthenticationFailed()

                        Log.d(
                            TAG,
                            "BIOMETRIC FAILED"
                        )
                    }
                }
            )

        biometricStarted =
            true

        biometricPrompt?.authenticate(
            promptInfo
        )
    }

    // =========================================================
    // INTRUDER PHOTO
    // =========================================================

    private fun captureIntruderPhoto() {

        if (
            ContextCompat.checkSelfPermission(
                this,
                CAMERA_PERMISSION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    CAMERA_PERMISSION
                ),
                CAMERA_REQUEST_CODE
            )

            return
        }

        startCameraAndCapture()
    }

    // =========================================================
    // CAMERA PERMISSION RESULT
    // =========================================================

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode !=
            CAMERA_REQUEST_CODE
        ) {

            return
        }

        if (
            grantResults.isNotEmpty() &&
            grantResults[0] ==
            PackageManager.PERMISSION_GRANTED
        ) {

            startCameraAndCapture()
        }
    }

    // =========================================================
    // START CAMERA
    // =========================================================

    private fun startCameraAndCapture() {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(
                this
            )

        cameraProviderFuture.addListener(
            {

                try {

                    val provider =
                        cameraProviderFuture.get()

                    cameraProvider =
                        provider

                    provider.unbindAll()

                    val cameraSelector =
                        CameraSelector.Builder()
                            .requireLensFacing(
                                CameraSelector.LENS_FACING_FRONT
                            )
                            .build()

                    val capture =
                        ImageCapture.Builder()
                            .setCaptureMode(
                                ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                            )
                            .setTargetRotation(
                                windowManager
                                    .defaultDisplay
                                    .rotation
                            )
                            .build()

                    imageCapture =
                        capture

                    provider.bindToLifecycle(
                        this,
                        cameraSelector,
                        capture
                    )

                    takeIntruderPhoto(
                        capture
                    )

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "CAMERA START ERROR",
                        e
                    )
                }

            },
            ContextCompat.getMainExecutor(
                this
            )
        )
    }

    // =========================================================
    // TAKE PHOTO
    // =========================================================

    private fun takeIntruderPhoto(
        capture: ImageCapture
    ) {

        val fileName =
            "Intruder_" +
                    SimpleDateFormat(
                        "yyyyMMdd_HHmmss",
                        Locale.US
                    ).format(
                        System.currentTimeMillis()
                    ) +
                    ".jpg"

        val contentValues =
            ContentValues().apply {

                put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    fileName
                )

                put(
                    MediaStore.Images.Media.MIME_TYPE,
                    "image/jpeg"
                )

                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q
                ) {

                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "Pictures/AppLock/Intruder"
                    )

                    put(
                        MediaStore.Images.Media.IS_PENDING,
                        1
                    )
                }
            }

        val outputOptions =
            ImageCapture.OutputFileOptions.Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()

        capture.takePicture(

            outputOptions,

            ContextCompat.getMainExecutor(
                this
            ),

            object :
                ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    outputFileResults:
                    ImageCapture.OutputFileResults
                ) {

                    val savedUri =
                        outputFileResults.savedUri

                    if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.Q
                    ) {

                        savedUri?.let { uri ->

                            val values =
                                ContentValues().apply {

                                    put(
                                        MediaStore.Images.Media.IS_PENDING,
                                        0
                                    )
                                }

                            contentResolver.update(
                                uri,
                                values,
                                null,
                                null
                            )
                        }
                    }

                    if (
                        savedUri != null
                    ) {

                        CoroutineScope(
                            Dispatchers.IO
                        ).launch {

                            try {

                                dataStore.saveIntruderPhoto(
                                    savedUri.toString()
                                )

                            } catch (e: Exception) {

                                Log.e(
                                    TAG,
                                    "INTRUDER URI ERROR",
                                    e
                                )
                            }
                        }
                    }

                    releaseCamera()
                }

                override fun onError(
                    exception:
                    ImageCaptureException
                ) {

                    Log.e(
                        TAG,
                        "INTRUDER PHOTO ERROR",
                        exception
                    )

                    releaseCamera()
                }
            }
        )
    }

    // =========================================================
    // RELEASE CAMERA
    // =========================================================

    private fun releaseCamera() {

        try {

            cameraProvider?.unbindAll()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "CAMERA RELEASE ERROR",
                e
            )
        }

        imageCapture =
            null

        cameraProvider =
            null
    }

    // =========================================================
    // NEW INTENT
    // =========================================================

    override fun onNewIntent(
        intent: Intent
    ) {

        super.onNewIntent(
            intent
        )

        setIntent(
            intent
        )

        val newPackage =
            intent.getStringExtra(
                "packageName"
            )

        if (
            !newPackage.isNullOrEmpty()
        ) {

            targetPackage =
                newPackage

            unlockHandled =
                false

            AppLockServiceHolder
                .isLockScreenOpen = true

            Log.d(
                TAG,
                "NEW LOCK TARGET = $newPackage"
            )
        }
    }

    // =========================================================
    // GO HOME
    // =========================================================

    private fun goHome() {

        Log.d(
            TAG,
            "GO HOME"
        )

        AppLockServiceHolder
            .currentUnlockedApp =
            null

        AppLockServiceHolder
            .lastUnlockTime =
            0L

        AppLockServiceHolder
            .clearSuppressedPackage()

        AppLockServiceHolder
            .isLockScreenOpen =
            false

        try {

            val homeIntent =
                Intent(
                    Intent.ACTION_MAIN
                ).apply {

                    addCategory(
                        Intent.CATEGORY_HOME
                    )

                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    )
                }

            startActivity(
                homeIntent
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "HOME OPEN ERROR",
                e
            )
        }

        try {

            finishAffinity()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "FINISH AFFINITY ERROR",
                e
            )
        }

        finishAndRemoveTask()
    }

    // =========================================================
    // OPEN APP
    // =========================================================

    private fun openApp() {

        val packageName =
            targetPackage

        if (
            packageName.isNullOrEmpty()
        ) {

            goHome()

            return
        }

        try {

            val appIntent =
                packageManager
                    .getLaunchIntentForPackage(
                        packageName
                    )

            if (
                appIntent == null
            ) {

                Log.e(
                    TAG,
                    "LAUNCH INTENT NULL = $packageName"
                )

                goHome()

                return
            }

            /*
             * Do NOT clear the app's existing task.
             *
             * We want the actual app to come to foreground
             * after unlocking.
             */
            appIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )

            Log.d(
                TAG,
                "OPENING UNLOCKED APP = $packageName"
            )

            startActivity(
                appIntent
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "OPEN APP ERROR",
                e
            )

            goHome()

            return
        }

        /*
         * IMPORTANT:
         *
         * Do not clear currentUnlockedApp here.
         * Service needs it to know that this package was
         * already unlocked.
         */
        AppLockServiceHolder
            .isLockScreenOpen = false

        finish()
    }

    // =========================================================
    // DESTROY
    // =========================================================

    override fun onDestroy() {

        Log.d(
            TAG,
            "LOCK SCREEN DESTROYED"
        )

        /*
         * IMPORTANT:
         *
         * DO NOT DO:
         *
         * AppLockServiceHolder.isLockScreenOpen = false
         *
         * here.
         *
         * unlockAndOpenApp() already controls the state.
         * Setting it blindly here can create another lock
         * screen while AccessibilityService is processing
         * the foreground change.
         */

        releaseCamera()

        biometricPrompt =
            null

        biometricStarted =
            false

        super.onDestroy()
    }
}