
package com.example.myapplication.Design.screens
import com.example.myapplication.Design.screens.UnlockScreen

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

    private val TAG = "APPLOCK_DEBUG"

    private var targetPackage: String? = null

    private lateinit var dataStore: DataStoreManager

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val CAMERA_PERMISSION =
        Manifest.permission.CAMERA

    private val CAMERA_REQUEST_CODE = 501

    private var biometricPrompt: BiometricPrompt? = null
    private var biometricStarted = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        dataStore = DataStoreManager(this)

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

        targetPackage =
            intent.getStringExtra("packageName")

        if (targetPackage.isNullOrEmpty()) {
            goHome()
            return
        }

        AppLockServiceHolder.isLockScreenOpen = true

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {
                    goHome()
                }
            }
        )

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

    private fun openResetPassword() {

        AppLockServiceHolder.isLockScreenOpen = false
        AppLockServiceHolder.currentUnlockedApp = null
        AppLockServiceHolder.lastUnlockTime = 0L
        AppLockServiceHolder.clearSuppressedPackage()

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

            startActivity(resetIntent)

        } catch (e: Exception) {

            Log.e(
                TAG,
                "RESET PASSWORD OPEN ERROR",
                e
            )
        }

        finish()
    }

    private fun unlockAndOpenApp() {

        val packageName =
            targetPackage

        if (packageName.isNullOrEmpty()) {
            goHome()
            return
        }

        AppLockServiceHolder.clearSuppressedPackage()

        AppLockServiceHolder.currentUnlockedApp =
            packageName

        AppLockServiceHolder.lastUnlockTime =
            System.currentTimeMillis()

        AppLockServiceHolder.isLockScreenOpen =
            false

        openApp()
    }

    private fun showBiometricPrompt() {

        if (biometricStarted) {
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
            return
        }

        val promptInfo =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Fingerprint Lock")
                .setSubtitle(
                    "Use your fingerprint to unlock"
                )
                .setNegativeButtonText(
                    "Use PIN / Pattern"
                )
                .build()

        val executor =
            ContextCompat.getMainExecutor(this)

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

                        biometricStarted = false

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

                        biometricStarted = false
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                    }
                }
            )

        biometricStarted = true

        biometricPrompt?.authenticate(
            promptInfo
        )
    }

    private fun captureIntruderPhoto() {

        if (
            ContextCompat.checkSelfPermission(
                this,
                CAMERA_PERMISSION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(CAMERA_PERMISSION),
                CAMERA_REQUEST_CODE
            )

            return
        }

        startCameraAndCapture()
    }

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

    private fun startCameraAndCapture() {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

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
            ContextCompat.getMainExecutor(this)
        )
    }

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
            ContextCompat.getMainExecutor(this),
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

                    if (savedUri != null) {

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

        imageCapture = null
        cameraProvider = null
    }

    override fun onNewIntent(
        intent: Intent
    ) {
        super.onNewIntent(intent)

        setIntent(intent)

        targetPackage =
            intent.getStringExtra(
                "packageName"
            )

        AppLockServiceHolder.isLockScreenOpen =
            true
    }

    private fun goHome() {

        AppLockServiceHolder.currentUnlockedApp = null
        AppLockServiceHolder.lastUnlockTime = 0L
        AppLockServiceHolder.clearSuppressedPackage()
        AppLockServiceHolder.isLockScreenOpen = false

        try {

            val homeIntent =
                Intent(Intent.ACTION_MAIN).apply {

                    addCategory(
                        Intent.CATEGORY_HOME
                    )

                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    )
                }

            startActivity(homeIntent)

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

    private fun openApp() {

        val packageName =
            targetPackage

        if (packageName.isNullOrEmpty()) {
            goHome()
            return
        }

        try {

            val appIntent =
                packageManager.getLaunchIntentForPackage(
                    packageName
                )

            if (appIntent == null) {
                goHome()
                return
            }

            appIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )

            startActivity(appIntent)

        } catch (e: Exception) {

            Log.e(
                TAG,
                "OPEN APP ERROR",
                e
            )

            goHome()
            return
        }

        AppLockServiceHolder.isLockScreenOpen = false

        finish()
    }

    override fun onDestroy() {

        releaseCamera()

        biometricPrompt = null
        biometricStarted = false

        AppLockServiceHolder.isLockScreenOpen = false

        super.onDestroy()
    }
}

