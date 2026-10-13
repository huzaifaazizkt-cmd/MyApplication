package com.example.myapplication.Design.screens

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.DataStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class IntruderCamera(
    private val activity: ComponentActivity,
    private val dataStore: DataStoreManager
) {
    private val tag = "INTRUDER_CAMERA"
    private var busy = false

    fun capture() {
        Log.d(tag, "CAPTURE REQUESTED")

        if (busy) {
            Log.d(tag, "CAPTURE SKIPPED -> ALREADY BUSY")
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            Log.e(tag, "CAMERA PERMISSION NOT GRANTED")
            return
        }

        busy = true
        val future = ProcessCameraProvider.getInstance(activity)

        future.addListener({
            try {
                val provider = future.get()
                provider.unbindAll()

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                provider.bindToLifecycle(
                    activity,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    imageCapture
                )

                Log.d(tag, "FRONT CAMERA BOUND")
                takePhoto(provider, imageCapture)
            } catch (e: Exception) {
                Log.e(tag, "CAMERA START ERROR", e)
                busy = false
            }
        }, ContextCompat.getMainExecutor(activity))
    }

    private fun takePhoto(
        provider: ProcessCameraProvider,
        imageCapture: ImageCapture
    ) {
        val name = "Intruder_" +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(System.currentTimeMillis()) + ".jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/AppLock/Intruder"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val options = ImageCapture.OutputFileOptions.Builder(
            activity.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()

        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(activity),
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    result: ImageCapture.OutputFileResults
                ) {
                    val uri = result.savedUri
                    Log.d(tag, "PHOTO SAVED = $uri")

                    if (uri != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val done = ContentValues().apply {
                                put(MediaStore.Images.Media.IS_PENDING, 0)
                            }
                            activity.contentResolver.update(
                                uri, done, null, null
                            )
                        }

                        activity.lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                dataStore.saveIntruderPhoto(uri.toString())
                            } catch (e: Exception) {
                                Log.e(tag, "DATASTORE ERROR", e)
                            }
                        }
                    }

                    provider.unbindAll()
                    busy = false
                }

                override fun onError(e: ImageCaptureException) {
                    Log.e(tag, "PHOTO ERROR", e)
                    provider.unbindAll()
                    busy = false
                }
            }
        )
    }
}