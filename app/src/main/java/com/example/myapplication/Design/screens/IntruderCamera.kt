package com.example.myapplication.Design.screens

import com.example.myapplication.billing.PremiumRepository

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

enum class IntruderCaptureStatus { IDLE, CAPTURED, UNAVAILABLE, PERMISSION_REQUIRED }

/** One lifecycle-bound capture; stale callbacks cannot release or replace a newer request. */
class IntruderCamera(private val activity: ComponentActivity, private val dataStore: DataStoreManager) : DefaultLifecycleObserver {
    private val premium = PremiumRepository.get(activity)
    private var provider: ProcessCameraProvider? = null
    private var useCase: ImageCapture? = null
    private var retry: Job? = null
    private var staging: File? = null
    private var busy = false
    private var generation = 0
    init {
        activity.lifecycle.addObserver(this)
        activity.lifecycleScope.launch {
            premium.state.collect { if (it.loaded && !it.owned) cancel() }
        }
    }
    private fun resumed() = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    private fun permitted() = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun capture() {
        if (busy || !resumed()) return
        busy = true
        val request = ++generation
        activity.lifecycleScope.launch {
            val access = withTimeoutOrNull(1_500) { premium.state.first { it.loaded } }
            if (request != generation) return@launch
            if (access?.owned != true || !resumed()) { cancel(); return@launch }
            if (!permitted()) { busy = false; status.value = IntruderCaptureStatus.PERMISSION_REQUIRED; return@launch }
            begin(request, 0)
        }
    }
    private fun begin(request: Int, attempt: Int) {
        val future = runCatching { ProcessCameraProvider.getInstance(activity) }
            .getOrElse { fail(request, attempt, IllegalStateException("Camera provider unavailable", it)); return }
        future.addListener({
            if (request != generation) return@addListener
            if (!resumed() || !permitted() || !premium.state.value.owned) { cancel(); return@addListener }
            try {
                val camera = future.get().also { provider = it }
                check(camera.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA))
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                    .setTargetRotation(activity.window.decorView.display?.rotation ?: Surface.ROTATION_0).build()
                useCase = capture
                camera.bindToLifecycle(activity, CameraSelector.DEFAULT_FRONT_CAMERA, capture)
                val directory = File(activity.filesDir, "intruder_photos").apply { check(isDirectory || mkdirs()) }
                val file = File(directory, "Intruder_${UUID.randomUUID()}.jpg")
                val part = File(directory, file.name + ".part").also { staging = it }
                capture.takePicture(ImageCapture.OutputFileOptions.Builder(part).build(), ContextCompat.getMainExecutor(activity),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                            if (request != generation) { part.delete(); return }
                            if (!premium.state.value.owned || !resumed()) { part.delete(); cancel(); return }
                            if (part.length() > 0 && part.renameTo(file)) {
                                status.value = IntruderCaptureStatus.CAPTURED
                                persistence.launch {
                                    runCatching { dataStore.saveIntruderPhoto(Uri.fromFile(file).toString()) }
                                        .onFailure { Log.w(TAG, "Photo retained for index recovery", it) }
                                }
                                release()
                                busy = false
                            } else fail(request, attempt, IllegalStateException("Photo was not committed"))
                        }
                        override fun onError(error: ImageCaptureException) {
                            part.delete()
                            if (request == generation) fail(request, attempt, error)
                        }
                    })
            } catch (error: Exception) { fail(request, attempt, error) }
        }, ContextCompat.getMainExecutor(activity))
    }
    private fun fail(request: Int, attempt: Int, error: Exception) {
        if (request != generation) return
        Log.w(TAG, "Capture unavailable (attempt ${attempt + 1})", error)
        release()
        if (attempt == 0 && resumed() && permitted() && premium.state.value.owned) {
            retry = activity.lifecycleScope.launch {
                delay(350)
                if (request == generation && resumed() && permitted() && premium.state.value.owned) begin(request, 1) else cancel()
            }
        } else {
            busy = false
            status.value = if (permitted()) IntruderCaptureStatus.UNAVAILABLE else IntruderCaptureStatus.PERMISSION_REQUIRED
        }
    }
    private fun release() {
        useCase?.let { capture -> runCatching { provider?.unbind(capture) } }
        useCase = null
        provider = null
        staging?.delete()
        staging = null
    }
    private fun cancel() { generation++; retry?.cancel(); retry = null; release(); busy = false }
    fun cancelCapture() { cancel() }
    override fun onStop(owner: LifecycleOwner) { cancel() }
    override fun onDestroy(owner: LifecycleOwner) { cancel(); owner.lifecycle.removeObserver(this) }
    companion object {
        private const val TAG = "IntruderCamera"
        private val persistence = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val status = MutableStateFlow(IntruderCaptureStatus.IDLE)
        val captureStatus = status.asStateFlow()
        fun resetStatus() { status.value = IntruderCaptureStatus.IDLE }
    }
}
