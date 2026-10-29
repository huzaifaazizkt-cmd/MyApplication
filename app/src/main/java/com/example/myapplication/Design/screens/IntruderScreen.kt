
package com.example.myapplication.Design.screens

import com.example.myapplication.billing.PremiumRepository

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import android.provider.Settings
import android.media.ExifInterface
import android.util.Size
import android.util.LruCache

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

import com.example.myapplication.R
import com.example.myapplication.data.DataStoreManager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class IntruderImage(
    val uri: Uri,
    val name: String,
    val dateAdded: Long
)

private fun intruderBitmapCache(maxBytes: Int) = object : LruCache<String, Bitmap>(maxBytes) {
    override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
}

// =============================================================
// INTRUDER SCREEN WRAPPER
// =============================================================

@Composable
fun IntruderScreen(
    onBackClick: () -> Unit = {},
    onUpgradeClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val premium by remember(context) { PremiumRepository.get(context) }.state.collectAsState()
    if (!premium.loaded || !premium.owned) {
        Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (!premium.loaded) CircularProgressIndicator(color = Color(0xFF0396FF))
            else {
                Text(stringResource(R.string.billing_intruder_locked), color = Color(0xFF333333), fontSize = 18.sp)
                Spacer(Modifier.height(20.dp))
                Button(onClick = onUpgradeClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0396FF))) {
                    Text(stringResource(R.string.premium_unlock_button), color = Color.White)
                }
                TextButton(onClick = onBackClick) { Text(stringResource(R.string.back)) }
            }
        }
        return
    }
    LimitFontScale {
        IntruderScreenContent(onBackClick)
    }
}

// =============================================================
// INTRUDER SCREEN CONTENT
// =============================================================

@Composable
private fun IntruderScreenContent(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val premiumRepository = remember(context) { PremiumRepository.get(context) }

    val layoutDirection = LocalLayoutDirection.current

    val dataStore = remember {
        DataStoreManager(context)
    }

    val scope = rememberCoroutineScope()

    val blueColor = Color(0xFF0396FF)
    val backgroundColor = Color(0xFFF7F7F7)

    val thumbnailCache = remember {
        intruderBitmapCache(8 * 1024 * 1024)
    }

    val fullImageCache = remember {
        intruderBitmapCache(24 * 1024 * 1024)
    }

    // =========================================================
    // INTRUDER ENABLED
    // =========================================================

    var intruderEnabled by remember {
        mutableStateOf(false)
    }

    // =========================================================
    // OBSERVATION ATTEMPTS
    // =========================================================

    var observationAttempts by remember {
        mutableStateOf(0)
    }
    val captureStatus by IntruderCamera.captureStatus.collectAsState()

    var selectedObservationAttempts by remember {
        mutableStateOf("")
    }

    var showAttemptsDialog by remember {
        mutableStateOf(false)
    }

    var showPermissionSettingsDialog by remember {
        mutableStateOf(false)
    }

    var openedAppSettingsForPermission by remember {
        mutableStateOf(false)
    }

    // =========================================================
    // CAMERA PERMISSION
    // =========================================================

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted && premiumRepository.state.value.owned) {
                IntruderCamera.resetStatus()
                intruderEnabled = true

                scope.launch {
                    dataStore.saveIntruderEnabled(true)
                }
            } else if (premiumRepository.state.value.owned) {
                intruderEnabled = false

                scope.launch {
                    dataStore.saveIntruderEnabled(false)
                }

                showPermissionSettingsDialog = true
            }
        }

    // =========================================================
    // OPEN APP SETTINGS
    // =========================================================

    fun openAppSettings() {
        openedAppSettingsForPermission = true

        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )

            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // =========================================================
    // REQUEST CAMERA PERMISSION
    // =========================================================

    fun requestCameraPermission() {
        if (!premiumRepository.state.value.owned) return
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
            android.widget.Toast.makeText(context, R.string.intruder_camera_unavailable, android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val permissionGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            IntruderCamera.resetStatus()
            intruderEnabled = true

            scope.launch {
                dataStore.saveIntruderEnabled(true)
            }

            return
        }

        cameraPermissionLauncher.launch(
            Manifest.permission.CAMERA
        )
    }

    // =========================================================
    // LOAD SETTINGS
    // =========================================================

    LaunchedEffect(Unit) {
        intruderEnabled =
            dataStore.getIntruderEnabled().first()

        val savedAttempts =
            dataStore.getIntruderObservationAttempts().first()

        observationAttempts = when (savedAttempts) {
            0 -> 0
            3 -> 3
            5 -> 5
            10 -> 10
            else -> 0
        }

        selectedObservationAttempts =
            observationAttemptsToText(
                observationAttempts,
                context
            )
    }

    // =========================================================
    // CHECK CAMERA PERMISSION WHEN RETURNING FROM SETTINGS
    // =========================================================

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->

            if (event == Lifecycle.Event.ON_RESUME) {
                if (openedAppSettingsForPermission) {
                    val permissionGranted =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED

                    if (permissionGranted && premiumRepository.state.value.owned) {
                        IntruderCamera.resetStatus()
                        openedAppSettingsForPermission = false
                        intruderEnabled = true
                        showPermissionSettingsDialog = false

                        scope.launch {
                            dataStore.saveIntruderEnabled(true)
                        }
                    }
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // =========================================================
    // IMAGES
    // =========================================================

    var intruderImages by remember {
        mutableStateOf<List<IntruderImage>>(emptyList())
    }

    var selectionMode by remember {
        mutableStateOf(false)
    }

    var selectedImages by remember {
        mutableStateOf<Set<Uri>>(emptySet())
    }

    var previewImage by remember {
        mutableStateOf<IntruderImage?>(null)
    }

    LaunchedEffect(Unit) {
        intruderImages = loadIntruderImages(context)
    }

    // =========================================================
    // RELOAD
    // =========================================================

    fun reloadImages() {
        scope.launch {
            intruderImages = loadIntruderImages(context)
        }
    }

    // =========================================================
    // DELETE SINGLE IMAGE
    // =========================================================

    fun deleteSingleImage(image: IntruderImage) {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    deleteIntruderImage(context, image.uri)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            thumbnailCache.remove(image.uri.toString())
            fullImageCache.remove(image.uri.toString())

            previewImage = null
            reloadImages()
        }
    }

    // =========================================================
    // DELETE SELECTED IMAGES
    // =========================================================

    fun deleteSelectedImages() {
        val imagesToDelete = intruderImages.filter {
            selectedImages.contains(it.uri)
        }

        scope.launch {
            withContext(Dispatchers.IO) {
                imagesToDelete.forEach {
                    try {
                        deleteIntruderImage(context, it.uri)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            imagesToDelete.forEach {
                thumbnailCache.remove(it.uri.toString())
                fullImageCache.remove(it.uri.toString())
            }

            selectedImages = emptySet()
            selectionMode = false

            reloadImages()
        }
    }

    // =========================================================
    // PREVIEW
    // =========================================================

    if (previewImage != null) {
        IntruderImagePreviewScreen(
            image = previewImage!!,
            thumbnailCache = thumbnailCache,
            fullImageCache = fullImageCache,
            onBackClick = {
                previewImage = null
            },
            onDelete = {
                previewImage?.let { image ->
                    deleteSingleImage(image)
                }
            }
        )

        return
    }

    // =========================================================
    // MAIN SCREEN
    // =========================================================

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            // =================================================
            // TOP BAR
            // =================================================

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .heightIn(min = 64.dp)
                    .padding(
                        start = 6.dp,
                        end = 6.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (selectionMode) {
                            selectionMode = false
                            selectedImages = emptySet()
                        } else {
                            onBackClick()
                        }
                    }
                ) {
                    if (selectionMode) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(
                                R.string.close_selection
                            ),
                            tint = Color(0xFF333333),
                            modifier = Modifier.size(21.dp)
                        )
                    } else {
                        Image(
                            painter = painterResource(
                                id = R.drawable.backarrow
                            ),
                            contentDescription = stringResource(
                                R.string.back
                            ),
                            modifier = Modifier
                                .size(30.dp)
                                .graphicsLayer {
                                    scaleX =
                                        if (layoutDirection == LayoutDirection.Rtl) {
                                            -1f
                                        } else {
                                            1f
                                        }
                                }
                        )
                    }
                }

                Text(
                    text = if (selectionMode) {
                        stringResource(
                            R.string.selected_count,
                            selectedImages.size
                        )
                    } else {
                        stringResource(R.string.intruder)
                    },
                    color = Color(0xFF333333),
                    fontSize = 22.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (selectionMode) {
                    val allSelected =
                        intruderImages.isNotEmpty() &&
                                selectedImages.size == intruderImages.size

                    Row(
                        modifier = Modifier
                            .clickable {
                                selectedImages =
                                    if (allSelected) {
                                        emptySet()
                                    } else {
                                        intruderImages
                                            .map { it.uri }
                                            .toSet()
                                    }
                            }
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.all),
                            color = Color(0xFF444444),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.width(3.dp))

                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    if (allSelected) {
                                        blueColor
                                    } else {
                                        Color.Transparent
                                    },
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (allSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(
                                        R.string.all_selected
                                    ),
                                    tint = Color.White,
                                    modifier = Modifier.size(8.dp)
                                )
                            }
                        }
                    }
                } else {
                    IconButton(
                        onClick = {
                            selectedObservationAttempts =
                                observationAttemptsToText(
                                    observationAttempts,
                                    context
                                )

                            showAttemptsDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(
                                R.string.set_observation_attempts
                            ),
                            tint = Color(0xFFBDBDBD),
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }
            }

            // =================================================
            // INTRUDER CARD
            // =================================================

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 14.dp,
                        end = 14.dp
                    ),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 2.dp
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 14.dp,
                            end = 12.dp,
                            top = 14.dp,
                            bottom = 14.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(
                            id = R.drawable.hacker
                        ),
                        contentDescription = stringResource(
                            R.string.intruder_camera
                        ),
                        modifier = Modifier.size(30.dp)
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.intruder_camera
                            ),
                            color = Color(0xFF333333),
                            fontSize = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = stringResource(
                                R.string.capture_wrong_password
                            ),
                            color = Color(0xFF666666),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (intruderEnabled && (observationAttempts == 0 ||
                            captureStatus == IntruderCaptureStatus.UNAVAILABLE || captureStatus == IntruderCaptureStatus.PERMISSION_REQUIRED)) {
                            Text(stringResource(if (captureStatus == IntruderCaptureStatus.PERMISSION_REQUIRED)
                                R.string.intruder_camera_permission_required
                                else if (captureStatus == IntruderCaptureStatus.UNAVAILABLE) R.string.intruder_camera_unavailable
                                else R.string.intruder_select_attempts), color = Color(0xFF666666), fontSize = 11.sp)
                        }
                    }

                    Switch(
                        checked = intruderEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                requestCameraPermission()
                            } else {
                                intruderEnabled = false

                                scope.launch {
                                    dataStore.saveIntruderEnabled(false)
                                }
                            }
                        },
                        modifier = Modifier
                            .size(
                                width = 42.dp,
                                height = 24.dp
                            )
                            .scale(0.56f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = blueColor,
                            uncheckedThumbColor = Color(0xFFAAAAAA),
                            uncheckedTrackColor = Color(0xFFE3E3E3),
                            uncheckedBorderColor = Color.Transparent,
                            checkedBorderColor = Color.Transparent
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(
                    R.string.take_intruder_photo
                ),
                color = Color(0xFF777777),
                fontSize = 12.sp,
                modifier = Modifier.padding(
                    start = 18.dp,
                    top = 2.dp,
                    bottom = 4.dp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // =================================================
            // EMPTY STATE
            // =================================================

            if (intruderImages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(
                                id = R.drawable.nofound
                            ),
                            contentDescription = stringResource(
                                R.string.no_intruder_found
                            ),
                            tint = Color(0xFFBDBDBD),
                            modifier = Modifier.size(120.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = stringResource(
                                R.string.no_intruder_found
                            ),
                            color = Color(0xFF333333),
                            fontSize = 19.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(
                                horizontal = 24.dp
                            )
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.today),
                        color = Color(0xFF555555),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(
                            start = 18.dp,
                            top = 10.dp,
                            bottom = 7.dp
                        )
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(
                            minSize = 96.dp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(
                                start = 8.dp,
                                end = 8.dp
                            ),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(
                            bottom = 20.dp
                        )
                    ) {
                        items(
                            items = intruderImages,
                            key = { it.uri.toString() }
                        ) { image ->
                            IntruderImageItem(
                                image = image,
                                thumbnailCache = thumbnailCache,
                                selected = selectedImages.contains(image.uri),
                                selectionMode = selectionMode,
                                onClick = {
                                    if (selectionMode) {
                                        selectedImages =
                                            if (selectedImages.contains(image.uri)) {
                                                selectedImages - image.uri
                                            } else {
                                                selectedImages + image.uri
                                            }
                                    } else {
                                        previewImage = image
                                    }
                                },
                                onLongClick = {
                                    if (!selectionMode) {
                                        selectionMode = true
                                        selectedImages = setOf(image.uri)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // =====================================================
        // DELETE SELECTED BUTTON
        // =====================================================

        if (selectionMode && selectedImages.isNotEmpty()) {
            Button(
                onClick = ::deleteSelectedImages,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 20.dp
                    )
                    .heightIn(min = 47.dp),
                shape = RoundedCornerShape(7.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = blueColor,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = stringResource(R.string.delete),
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    // =========================================================
    // ATTEMPTS DIALOG
    // =========================================================

    if (showAttemptsDialog) {
        val neverText = stringResource(R.string.never)
        val threeAttemptsText = stringResource(R.string.three_attempts)
        val fiveAttemptsText = stringResource(R.string.five_attempts)
        val tenAttemptsText = stringResource(R.string.ten_attempts)

        AlertDialog(
            onDismissRequest = {
                showAttemptsDialog = false
            },
            shape = RoundedCornerShape(10.dp),
            containerColor = Color.White,
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            R.string.set_observation_attempts
                        ),
                        color = Color(0xFF333333),
                        fontSize = 16.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = stringResource(
                            R.string.take_intruder_photo
                        ),
                        color = Color(0xFF999999),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ObservationAttemptsOption(
                        text = neverText,
                        selected = selectedObservationAttempts == neverText,
                        blueColor = blueColor,
                        onClick = {
                            selectedObservationAttempts = neverText
                        }
                    )

                    ObservationAttemptsOption(
                        text = threeAttemptsText,
                        selected = selectedObservationAttempts == threeAttemptsText,
                        blueColor = blueColor,
                        onClick = {
                            selectedObservationAttempts = threeAttemptsText
                        }
                    )

                    ObservationAttemptsOption(
                        text = fiveAttemptsText,
                        selected = selectedObservationAttempts == fiveAttemptsText,
                        blueColor = blueColor,
                        onClick = {
                            selectedObservationAttempts = fiveAttemptsText
                        }
                    )

                    ObservationAttemptsOption(
                        text = tenAttemptsText,
                        selected = selectedObservationAttempts == tenAttemptsText,
                        blueColor = blueColor,
                        onClick = {
                            selectedObservationAttempts = tenAttemptsText
                        }
                    )
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        selectedObservationAttempts =
                            observationAttemptsToText(
                                observationAttempts,
                                context
                            )

                        showAttemptsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF999999)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val attempts =
                            observationAttemptsFromText(
                                selectedObservationAttempts,
                                context
                            )

                        observationAttempts = attempts
                        showAttemptsDialog = false

                        scope.launch {
                            dataStore.saveIntruderObservationAttempts(
                                attempts
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = blueColor
                    )
                ) {
                    Text(
                        text = stringResource(R.string.confirm),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        )
    }

    // =========================================================
    // CAMERA PERMISSION DIALOG
    // =========================================================

    if (showPermissionSettingsDialog) {
        AlertDialog(
            onDismissRequest = {
                showPermissionSettingsDialog = false
            },
            shape = RoundedCornerShape(12.dp),
            containerColor = Color.White,
            title = {
                Text(
                    text = stringResource(
                        R.string.camera_permission
                    ),
                    color = Color(0xFF333333),
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.camera_permission_denied
                    ),
                    color = Color(0xFF666666),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            },
            dismissButton = {
                Button(
                    onClick = {
                        showPermissionSettingsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF999999)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionSettingsDialog = false
                        openAppSettings()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = blueColor
                    )
                ) {
                    Text(
                        text = stringResource(R.string.settings),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        )
    }
}

// =============================================================
// OBSERVATION ATTEMPTS → TEXT
// =============================================================

private fun observationAttemptsToText(
    value: Int,
    context: Context
): String {
    return when (value) {
        0 -> context.getString(R.string.never)
        3 -> context.getString(R.string.three_attempts)
        5 -> context.getString(R.string.five_attempts)
        10 -> context.getString(R.string.ten_attempts)
        else -> context.getString(R.string.never)
    }
}

// =============================================================
// TEXT → OBSERVATION ATTEMPTS
// =============================================================

private fun observationAttemptsFromText(
    value: String,
    context: Context
): Int {
    return when (value) {
        context.getString(R.string.never) -> 0
        context.getString(R.string.three_attempts) -> 3
        context.getString(R.string.five_attempts) -> 5
        context.getString(R.string.ten_attempts) -> 10
        else -> 0
    }
}

// =============================================================
// IMAGE ITEM
// =============================================================

@Composable
private fun IntruderImageItem(
    image: IntruderImage,
    thumbnailCache: LruCache<String, Bitmap>,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        IntruderThumbnail(
            uri = image.uri,
            thumbnailCache = thumbnailCache
        )

        if (selectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(14.dp)
                    .background(
                        if (selected) {
                            Color(0xFF0396FF)
                        } else {
                            Color.White.copy(alpha = 0.75f)
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(
                            R.string.selected
                        ),
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}

// =============================================================
// LOAD ROTATED IMAGE
// =============================================================

private suspend fun loadCorrectlyRotatedBitmap(
    context: Context,
    uri: Uri,
    maxDimension: Int = 2048
): Bitmap? {
    return withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > maxDimension) inSampleSize *= 2
            }
            val bitmap =
                context.contentResolver
                    .openInputStream(uri)
                    ?.use { input ->
                        BitmapFactory.decodeStream(input, null, options)
                    } ?: return@withContext null

            val orientation =
                context.contentResolver
                    .openInputStream(uri)
                    ?.use { input ->
                        ExifInterface(input).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                    } ?: ExifInterface.ORIENTATION_NORMAL

            val matrix = Matrix()

            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                    matrix.setScale(-1f, 1f)
                }

                ExifInterface.ORIENTATION_ROTATE_180 -> {
                    matrix.setRotate(180f)
                }

                ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                    matrix.setScale(1f, -1f)
                }

                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.setRotate(90f)
                    matrix.postScale(-1f, 1f)
                }

                ExifInterface.ORIENTATION_ROTATE_90 -> {
                    matrix.setRotate(90f)
                }

                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.setRotate(-90f)
                    matrix.postScale(-1f, 1f)
                }

                ExifInterface.ORIENTATION_ROTATE_270 -> {
                    matrix.setRotate(270f)
                }
            }

            if (matrix.isIdentity) {
                return@withContext bitmap
            }

            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            ).also { if (it !== bitmap) bitmap.recycle() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

// =============================================================
// LOAD THUMBNAIL
// =============================================================

private suspend fun loadIntruderThumbnail(
    context: Context,
    uri: Uri
): Bitmap? {
    return withContext(Dispatchers.IO) {
        try {
            if (uri.scheme == "file") {
                loadCorrectlyRotatedBitmap(context, uri, maxDimension = 300)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(
                    uri,
                    Size(300, 300),
                    null
                )
            } else {
                MediaStore.Images.Thumbnails.getThumbnail(
                    context.contentResolver,
                    ContentUris.parseId(uri),
                    MediaStore.Images.Thumbnails.MINI_KIND,
                    null
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

// =============================================================
// THUMBNAIL COMPOSABLE
// =============================================================

@Composable
private fun IntruderThumbnail(
    uri: Uri,
    thumbnailCache: LruCache<String, Bitmap>
) {
    val context = LocalContext.current
    val cacheKey = uri.toString()

    var bitmap by remember(cacheKey) {
        mutableStateOf(thumbnailCache[cacheKey])
    }

    LaunchedEffect(cacheKey) {
        if (bitmap == null) {
            val loadedBitmap =
                loadIntruderThumbnail(context, uri)

            if (loadedBitmap != null) {
                thumbnailCache.put(cacheKey, loadedBitmap)
                bitmap = loadedBitmap
            }
        }
    }

    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = stringResource(
                R.string.intruder_photo
            ),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

// =============================================================
// PREVIEW SCREEN
// =============================================================

@Composable
private fun IntruderImagePreviewScreen(
    image: IntruderImage,
    thumbnailCache: LruCache<String, Bitmap>,
    fullImageCache: LruCache<String, Bitmap>,
    onBackClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val cacheKey = image.uri.toString()

    var bitmap by remember(cacheKey) {
        mutableStateOf(fullImageCache[cacheKey])
    }

    val cachedThumbnail = thumbnailCache[cacheKey]

    LaunchedEffect(cacheKey) {
        if (bitmap == null) {
            val cachedFullImage = fullImageCache[cacheKey]

            if (cachedFullImage != null) {
                bitmap = cachedFullImage
            } else {
                val fullBitmap =
                    loadCorrectlyRotatedBitmap(
                        context,
                        image.uri
                    )

                if (fullBitmap != null) {
                    fullImageCache.put(cacheKey, fullBitmap)
                    bitmap = fullBitmap
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .heightIn(min = 64.dp)
                    .padding(
                        start = 6.dp,
                        end = 6.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Image(
                        painter = painterResource(
                            id = R.drawable.backarrow
                        ),
                        contentDescription = stringResource(
                            R.string.back
                        ),
                        modifier = Modifier
                            .size(30.dp)
                            .graphicsLayer {
                                scaleX =
                                    if (layoutDirection == LayoutDirection.Rtl) {
                                        -1f
                                    } else {
                                        1f
                                    }
                            }
                    )
                }

                Text(
                    text = image.name,
                    color = Color(0xFF333333),
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(
                        R.string.settings
                    ),
                    tint = Color(0xFFBDBDBD),
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(23.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(
                        start = 6.dp,
                        end = 6.dp,
                        top = 15.dp,
                        bottom = 15.dp
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF0EEEE),
                    ),
                contentAlignment = Alignment.Center
            ) {
                val imageToShow = bitmap ?: cachedThumbnail

                if (imageToShow != null) {
                    Image(
                        bitmap = imageToShow.asImageBitmap(),
                        contentDescription = stringResource(
                            R.string.intruder_photo_preview
                        ),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Button(
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        start = 10.dp,
                        end = 10.dp,
                        bottom = 20.dp
                    )
                    .heightIn(min = 46.dp),
                shape = RoundedCornerShape(7.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = stringResource(R.string.delete),
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// =============================================================
// LOAD INTRUDER IMAGES
// =============================================================

private suspend fun loadIntruderImages(context: Context): List<IntruderImage> = withContext(Dispatchers.IO) {
    val privateImages = File(context.filesDir, "intruder_photos").listFiles().orEmpty()
        .filter { it.isFile && it.extension == "jpg" }
        .map { IntruderImage(Uri.fromFile(it), it.name, it.lastModified() / 1_000L) }
    (privateImages + loadLegacyIntruderImages(context)).distinctBy { it.uri }.sortedByDescending { it.dateAdded }
}

private suspend fun deleteIntruderImage(context: Context, uri: Uri) {
    if (uri.scheme == "file") {
        val file = File(uri.path ?: return).canonicalFile
        val directory = File(context.filesDir, "intruder_photos").canonicalFile
        check(file.parentFile == directory && (!file.exists() || file.delete()))
    } else context.contentResolver.delete(uri, null, null)
    DataStoreManager(context).removeIntruderPhoto(uri.toString())
}

private suspend fun loadLegacyIntruderImages(
    context: Context
): List<IntruderImage> {
    return withContext(Dispatchers.IO) {
        val result = mutableListOf<IntruderImage>()

        val collection =
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )

        val selection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Images.Media.RELATIVE_PATH}=?"
            } else {
                "${MediaStore.Images.Media.DATA} LIKE ?"
            }

        val selectionArgs =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf("Pictures/AppLock/Intruder/")
            } else {
                arrayOf("%Pictures/AppLock/Intruder/%")
            }

        val sortOrder =
            "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->

                val idColumn =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Images.Media._ID
                    )

                val nameColumn =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Images.Media.DISPLAY_NAME
                    )

                val dateColumn =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Images.Media.DATE_ADDED
                    )

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val date = cursor.getLong(dateColumn)

                    val contentUri =
                        ContentUris.withAppendedId(
                            collection,
                            id
                        )

                    result.add(
                        IntruderImage(
                            uri = contentUri,
                            name = name,
                            dateAdded = date
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        result
    }
}

// =============================================================
// ATTEMPTS OPTION
// =============================================================

@Composable
private fun ObservationAttemptsOption(
    text: String,
    selected: Boolean,
    blueColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = blueColor,
                unselectedColor = Color(0xFF555555)
            ),
            modifier = Modifier
                .size(40.dp)
                .scale(0.75f)
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = text,
            color = Color(0xFF444444),
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
