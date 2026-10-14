package com.example.myapplication.Design.screens

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.collection.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myapplication.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class VaultMediaItem(
    val uri: Uri,
    val name: String,
    val bucketName: String,
    val isVideo: Boolean,
    val isVaultFile: Boolean = false,
    val vaultFilePath: String? = null
)

private const val ALL_ALBUMS_KEY = "ALL"
private val vaultThumbnailCache = LruCache<String, Bitmap>(80)

@Composable
fun VaultScreen(onPreviewStateChange: (Boolean) -> Unit = {}) {
    LimitFontScale { VaultScreenContent(onPreviewStateChange) }
}

@Composable
private fun VaultScreenContent(onPreviewStateChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedTab by remember { mutableStateOf(0) }
    var galleryOpen by remember { mutableStateOf(false) }
    var selectedMedia by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var selectionMode by remember { mutableStateOf(false) }
    var hiddenMedia by remember { mutableStateOf(loadHiddenMediaMetadata(context)) }
    var previewImages by remember { mutableStateOf<List<VaultMediaItem>>(emptyList()) }
    var previewImageIndex by remember { mutableStateOf(0) }
    var pendingHideItems by remember { mutableStateOf<List<VaultMediaItem>>(emptyList()) }
    var showPermissionSettingsDialog by remember { mutableStateOf(false) }
    var waitingForPermissionSettings by remember { mutableStateOf(false) }
    var isVaultProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(previewImages.isNotEmpty()) {
        onPreviewStateChange(previewImages.isNotEmpty())
    }
    DisposableEffect(Unit) { onDispose { onPreviewStateChange(false) } }

    fun hasMediaPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (selectedTab == 0) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (selectedTab == 0) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_MEDIA_VIDEO
        } else Manifest.permission.READ_EXTERNAL_STORAGE

        val granted = permissions[requiredPermission] == true ||
                ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            waitingForPermissionSettings = false
            showPermissionSettingsDialog = false
            galleryOpen = true
        } else {
            waitingForPermissionSettings = false
            galleryOpen = false
            showPermissionSettingsDialog = true
        }
    }

    fun openGallery() {
        if (hasMediaPermission()) {
            galleryOpen = true
            return
        }
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (selectedTab == 0) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_MEDIA_VIDEO
        } else Manifest.permission.READ_EXTERNAL_STORAGE
        mediaPermissionLauncher.launch(arrayOf(permission))
    }

    DisposableEffect(lifecycleOwner, selectedTab) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && waitingForPermissionSettings && hasMediaPermission()) {
                waitingForPermissionSettings = false
                showPermissionSettingsDialog = false
                galleryOpen = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val itemsToHide = pendingHideItems
        if (itemsToHide.isEmpty()) {
            isVaultProcessing = false
            return@rememberLauncherForActivityResult
        }

        if (result.resultCode == Activity.RESULT_OK) {
            isVaultProcessing = true
            coroutineScope.launch {
                try {
                    val copiedItems = withContext(Dispatchers.IO) {
                        itemsToHide.mapNotNull { copyMediaToVault(context, it) }
                    }

                    if (copiedItems.size != itemsToHide.size) {
                        withContext(Dispatchers.IO) {
                            copiedItems.forEach { copied ->
                                copied.vaultFilePath?.let { path ->
                                    try { File(path).delete() } catch (_: Exception) {}
                                }
                            }
                        }
                        return@launch
                    }

                    val deleteSuccess = withContext(Dispatchers.IO) {
                        itemsToHide.all { media ->
                            try { context.contentResolver.delete(media.uri, null, null) > 0 }
                            catch (_: Exception) { false }
                        }
                    }

                    if (deleteSuccess) {
                        val newHidden = hiddenMedia.toMutableList()
                        copiedItems.forEach { copied ->
                            if (newHidden.none { it.vaultFilePath == copied.vaultFilePath }) {
                                newHidden.add(copied)
                            }
                        }
                        hiddenMedia = newHidden
                        saveHiddenMediaMetadata(context, newHidden)
                        selectedMedia = emptySet()
                        selectionMode = false
                        galleryOpen = false
                    } else {
                        withContext(Dispatchers.IO) {
                            copiedItems.forEach { copied ->
                                copied.vaultFilePath?.let { path ->
                                    try { File(path).delete() } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingHideItems = emptyList()
                    isVaultProcessing = false
                }
            }
        } else {
            pendingHideItems = emptyList()
            isVaultProcessing = false
            galleryOpen = true
        }
    }

    if (previewImages.isNotEmpty()) {
        BackHandler {
            if (!isVaultProcessing) {
                previewImages = emptyList()
                previewImageIndex = 0
            }
        }

        Box(Modifier.fillMaxSize()) {
            VaultImagePreviewScreen(
                mediaList = previewImages,
                initialIndex = previewImageIndex,
                onBack = {
                    if (!isVaultProcessing) {
                        previewImages = emptyList()
                        previewImageIndex = 0
                    }
                },
                onUnhide = { media ->
                    if (isVaultProcessing) return@VaultImagePreviewScreen
                    isVaultProcessing = true
                    coroutineScope.launch {
                        try {
                            val success = withContext(Dispatchers.IO) {
                                restoreMediaToGallery(context, media)
                            }
                            if (success) {
                                val updated = hiddenMedia.filterNot { it.vaultFilePath == media.vaultFilePath }
                                hiddenMedia = updated
                                saveHiddenMediaMetadata(context, updated)
                                previewImages = emptyList()
                                previewImageIndex = 0
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            isVaultProcessing = false
                        }
                    }
                }
            )
            if (isVaultProcessing) VaultProcessingOverlay()
        }
        return
    }

    if (galleryOpen) {
        Box(Modifier.fillMaxSize()) {
            VaultGalleryScreen(
                context = context,
                isVideo = selectedTab == 1,
                hiddenMedia = hiddenMedia,
                selectedMedia = selectedMedia,
                onSelectionChange = { newSelection ->
                    if (!isVaultProcessing) {
                        selectedMedia = newSelection
                        selectionMode = newSelection.isNotEmpty()
                    }
                },
                onBack = {
                    if (!isVaultProcessing) {
                        galleryOpen = false
                        selectedMedia = emptySet()
                        selectionMode = false
                    }
                },
                onHide = { itemsToHide ->
                    if (itemsToHide.isEmpty() || isVaultProcessing) return@VaultGalleryScreen

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val pendingIntent = MediaStore.createWriteRequest(
                                context.contentResolver,
                                itemsToHide.map { it.uri }
                            )
                            pendingHideItems = itemsToHide
                            isVaultProcessing = false
                            writeLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                        } catch (e: Exception) {
                            e.printStackTrace()
                            pendingHideItems = emptyList()
                            isVaultProcessing = false
                        }
                    } else {
                        isVaultProcessing = true
                        coroutineScope.launch {
                            try {
                                val copiedItems = withContext(Dispatchers.IO) {
                                    itemsToHide.mapNotNull { copyMediaToVault(context, it) }
                                }
                                if (copiedItems.size != itemsToHide.size) {
                                    withContext(Dispatchers.IO) {
                                        copiedItems.forEach { copied ->
                                            copied.vaultFilePath?.let { path ->
                                                try { File(path).delete() } catch (_: Exception) {}
                                            }
                                        }
                                    }
                                    return@launch
                                }

                                val deleteSuccess = withContext(Dispatchers.IO) {
                                    itemsToHide.all { media ->
                                        try { context.contentResolver.delete(media.uri, null, null) > 0 }
                                        catch (_: Exception) { false }
                                    }
                                }

                                if (deleteSuccess) {
                                    val newHidden = hiddenMedia.toMutableList()
                                    copiedItems.forEach { copied ->
                                        if (newHidden.none { it.vaultFilePath == copied.vaultFilePath }) {
                                            newHidden.add(copied)
                                        }
                                    }
                                    hiddenMedia = newHidden
                                    saveHiddenMediaMetadata(context, newHidden)
                                    selectedMedia = emptySet()
                                    selectionMode = false
                                    galleryOpen = false
                                } else {
                                    withContext(Dispatchers.IO) {
                                        copiedItems.forEach { copied ->
                                            copied.vaultFilePath?.let { path ->
                                                try { File(path).delete() } catch (_: Exception) {}
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                isVaultProcessing = false
                            }
                        }
                    }
                }
            )
            if (isVaultProcessing) VaultProcessingOverlay()
        }
        return
    }

    // MainScreen pehle hi navigation bar ki jagah sambhalta hai,
    // is liye yahan navigationBarsPadding() nahi lagaya.
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text(
                text = stringResource(R.string.vault),
                color = Color.Black,
                fontSize = 20.sp,
                modifier = Modifier.padding(start = 20.dp, top = 15.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(42.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VaultTab(
                    selected = selectedTab == 0,
                    icon = R.drawable.imageicon,
                    text = stringResource(R.string.photos),
                    onClick = {
                        if (!isVaultProcessing) {
                            selectedTab = 0
                            selectedMedia = emptySet()
                            selectionMode = false
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                VaultTab(
                    selected = selectedTab == 1,
                    icon = R.drawable.vedioicon,
                    text = stringResource(R.string.videos),
                    onClick = {
                        if (!isVaultProcessing) {
                            selectedTab = 1
                            selectedMedia = emptySet()
                            selectionMode = false
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Box(Modifier.fillMaxSize()) {
                val currentHidden = hiddenMedia.filter { it.isVideo == (selectedTab == 1) }
                if (currentHidden.isEmpty()) {
                    if (selectedTab == 0) PhotosVaultContent() else VideosVaultContent()
                } else {
                    VaultHiddenMediaGrid(
                        mediaList = currentHidden,
                        selectionMode = selectionMode,
                        selectedMedia = selectedMedia,
                        onMediaClick = { media ->
                            if (isVaultProcessing) return@VaultHiddenMediaGrid
                            if (selectionMode) {
                                val newSet = selectedMedia.toMutableSet()
                                if (newSet.contains(media.uri)) newSet.remove(media.uri) else newSet.add(media.uri)
                                selectedMedia = newSet
                                if (newSet.isEmpty()) selectionMode = false
                            } else if (media.isVideo) {
                                openVaultVideo(context, media)
                            } else {
                                val imageList = currentHidden.filterNot { it.isVideo }
                                val index = imageList.indexOfFirst { it.vaultFilePath == media.vaultFilePath }
                                previewImages = imageList
                                previewImageIndex = if (index >= 0) index else 0
                            }
                        },
                        onLongPress = { media ->
                            if (isVaultProcessing) return@VaultHiddenMediaGrid
                            selectionMode = true
                            selectedMedia = selectedMedia.toMutableSet().apply { add(media.uri) }
                        }
                    )
                }
            }
        }

        if (selectionMode && !isVaultProcessing) {
            val currentHidden = hiddenMedia.filter { it.isVideo == (selectedTab == 1) }
            val allHiddenSelected = currentHidden.isNotEmpty() && currentHidden.all { selectedMedia.contains(it.uri) }

            Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                VaultSelectionBottomBar(
                    allSelected = allHiddenSelected,
                    selectedCount = selectedMedia.size,
                    onSelectAll = {
                        val newSet = selectedMedia.toMutableSet()
                        if (allHiddenSelected) currentHidden.forEach { newSet.remove(it.uri) }
                        else currentHidden.forEach { newSet.add(it.uri) }
                        selectedMedia = newSet
                        if (newSet.isEmpty()) selectionMode = false
                    },
                    onCancel = {
                        selectedMedia = emptySet()
                        selectionMode = false
                    },
                    onUnhide = {
                        if (isVaultProcessing) return@VaultSelectionBottomBar
                        val media = hiddenMedia.filter { selectedMedia.contains(it.uri) }
                        if (media.isNotEmpty()) {
                            isVaultProcessing = true
                            coroutineScope.launch {
                                try {
                                    val restoredItems = withContext(Dispatchers.IO) {
                                        media.map { item -> item to restoreMediaToGallery(context, item) }
                                    }
                                    val restoredPaths = restoredItems.filter { (_, success) -> success }
                                        .map { (item, _) -> item.vaultFilePath }.toSet()
                                    if (restoredPaths.isNotEmpty()) {
                                        val updated = hiddenMedia.filterNot { restoredPaths.contains(it.vaultFilePath) }
                                        hiddenMedia = updated
                                        saveHiddenMediaMetadata(context, updated)
                                    }
                                    selectedMedia = emptySet()
                                    selectionMode = false
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                } finally {
                                    isVaultProcessing = false
                                }
                            }
                        }
                    }
                )
            }
        } else if (!isVaultProcessing) {
            // Plus button: banner ke bilkul upar, navigation bar ka extra gap nahi
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                    .padding(end = 15.dp, bottom = 15.dp).size(50.dp).clip(CircleShape)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        openGallery()
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.plus),
                    contentDescription = stringResource(R.string.add),
                    modifier = Modifier.size(50.dp)
                )
            }
        }

        if (isVaultProcessing) VaultProcessingOverlay()

        if (showPermissionSettingsDialog && !isVaultProcessing) {
            AlertDialog(
                onDismissRequest = { showPermissionSettingsDialog = false },
                title = { Text(stringResource(R.string.permission_required)) },
                text = {
                    Text(
                        if (selectedTab == 0) stringResource(R.string.photo_permission_required)
                        else stringResource(R.string.video_permission_required)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showPermissionSettingsDialog = false
                        waitingForPermissionSettings = true
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        )
                    }) {
                        Text(stringResource(R.string.settings), color = Color(0xFF0396FF))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionSettingsDialog = false }) {
                        Text(stringResource(R.string.cancel), color = Color(0xFF818181))
                    }
                }
            )
        }
    }
}

@Composable
private fun VaultProcessingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator(color = Color(0xFF0396FF), strokeWidth = 4.dp, modifier = Modifier.size(45.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.processing), color = Color.White, fontSize = 16.sp)
        }
    }
}

@Composable
private fun VaultSelectionBottomBar(
    allSelected: Boolean,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit,
    onUnhide: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(70.dp).background(Color.White).padding(horizontal = 35.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.allimage),
            contentDescription = stringResource(R.string.select_all),
            colorFilter = ColorFilter.tint(if (allSelected) Color(0xFF0396FF) else Color(0xFF818181)),
            modifier = Modifier.size(28.dp).clickable(
                indication = null, interactionSource = remember { MutableInteractionSource() }
            ) { onSelectAll() }
        )
        Image(
            painter = painterResource(R.drawable.cross),
            contentDescription = stringResource(R.string.cancel),
            colorFilter = ColorFilter.tint(Color(0xFF818181)),
            modifier = Modifier.size(20.dp).clickable(
                indication = null, interactionSource = remember { MutableInteractionSource() }
            ) { onCancel() }
        )
        Image(
            painter = painterResource(R.drawable.unlock),
            contentDescription = stringResource(R.string.unhide),
            colorFilter = ColorFilter.tint(if (selectedCount > 0) Color(0xFF0396FF) else Color(0xFF818181)),
            modifier = Modifier.size(28.dp).clickable(
                indication = null, interactionSource = remember { MutableInteractionSource() }
            ) { if (selectedCount > 0) onUnhide() }
        )
    }
}

@Composable
private fun VaultHiddenMediaGrid(
    mediaList: List<VaultMediaItem>,
    selectionMode: Boolean,
    selectedMedia: Set<Uri>,
    onMediaClick: (VaultMediaItem) -> Unit,
    onLongPress: (VaultMediaItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 80.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        userScrollEnabled = true
    ) {
        items(mediaList, key = { it.vaultFilePath ?: it.uri.toString() }) { media ->
            VaultGalleryItem(
                media = media,
                selected = selectedMedia.contains(media.uri),
                selectionMode = selectionMode,
                onClick = { onMediaClick(media) },
                onLongClick = { onLongPress(media) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultGalleryItem(
    media: VaultMediaItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val cacheKey = remember(media.uri, media.vaultFilePath, media.isVideo) { buildThumbnailCacheKey(media) }
    var bitmap by remember(cacheKey) { mutableStateOf(vaultThumbnailCache.get(cacheKey)) }

    LaunchedEffect(cacheKey) {
        val cached = vaultThumbnailCache.get(cacheKey)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        val loaded = loadVaultThumbnail(
            context,
            if (media.isVaultFile) Uri.fromFile(File(media.vaultFilePath ?: "")) else media.uri,
            media.isVideo
        )
        if (loaded != null) {
            vaultThumbnailCache.put(cacheKey, loaded)
            bitmap = loaded
        }
    }

    Box(
        modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).combinedClickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = { onClick() },
            onLongClick = { onLongClick() }
        )
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }

        if (media.isVideo && !selectionMode) {
            // Play button: chhota circle (30dp) aur chhota tikona (12dp)
            Box(
                modifier = Modifier.align(Alignment.Center).size(30.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(12.dp)) {
                    val playPath = Path().apply {
                        moveTo(size.width * 0.25f, size.height * 0.15f)
                        lineTo(size.width * 0.25f, size.height * 0.85f)
                        lineTo(size.width * 0.85f, size.height * 0.50f)
                        close()
                    }
                    drawPath(playPath, Color.White)
                }
            }
        }

        if (selectionMode) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(16.dp).clip(CircleShape)
                    .background(if (selected) Color(0xFF0396FF) else Color.Transparent)
                    .border(1.dp, if (selected) Color(0xFF0396FF) else Color(0xFF818181), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) Text("✓", color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

private fun buildThumbnailCacheKey(media: VaultMediaItem): String =
    if (media.isVaultFile && media.vaultFilePath != null) "vault:${media.vaultFilePath}" else media.uri.toString()

private fun openVaultVideo(context: Context, media: VaultMediaItem) {
    try {
        val path = media.vaultFilePath ?: return
        val videoFile = File(path)
        if (!videoFile.exists()) return

        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", videoFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, getVideoMimeType(media.name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.open_with)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// Preview mein MainScreen ka bottom bar chhup jata hai,
// is liye yahan navigationBarsPadding() zaroori hai.
@Composable
private fun VaultImagePreviewScreen(
    mediaList: List<VaultMediaItem>,
    initialIndex: Int,
    onBack: () -> Unit,
    onUnhide: (VaultMediaItem) -> Unit
) {
    val context = LocalContext.current
    if (mediaList.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val safeInitialIndex = initialIndex.coerceIn(0, mediaList.lastIndex)
    val pagerState = rememberPagerState(initialPage = safeInitialIndex, pageCount = { mediaList.size })
    val currentPage = pagerState.currentPage.coerceIn(0, mediaList.lastIndex)
    val currentMedia = mediaList[currentPage]
    val filenameScrollState = rememberScrollState()

    LaunchedEffect(currentPage) { filenameScrollState.scrollTo(0) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(0.dp),
            pageSpacing = 0.dp
        ) { page ->
            val media = mediaList[page]
            val cacheKey = buildThumbnailCacheKey(media)
            var bitmap by remember(cacheKey) { mutableStateOf(vaultThumbnailCache.get(cacheKey)) }

            LaunchedEffect(cacheKey) {
                val cached = vaultThumbnailCache.get(cacheKey)
                if (cached != null) {
                    bitmap = cached
                    return@LaunchedEffect
                }
                val loaded = loadVaultThumbnail(
                    context,
                    if (media.isVaultFile) Uri.fromFile(File(media.vaultFilePath ?: "")) else media.uri,
                    false
                )
                if (loaded != null) {
                    vaultThumbnailCache.put(cacheKey, loaded)
                    bitmap = loaded
                }
            }

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                bitmap?.let {
                    Image(it.asImageBitmap(), contentDescription = media.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(start = 15.dp, end = 15.dp, top = 15.dp, bottom = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.backarrow),
                contentDescription = stringResource(R.string.back),
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(24.dp).clickable(
                    indication = null, interactionSource = remember { MutableInteractionSource() }
                ) { onBack() }
            )
            Spacer(Modifier.width(20.dp))
            Text(
                text = currentMedia.name,
                color = Color.White,
                fontSize = 16.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f).horizontalScroll(filenameScrollState)
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(start = 35.dp, end = 35.dp, bottom = 15.dp).height(60.dp)
                .clip(RoundedCornerShape(10.dp)).background(Color(0xFF0396FF))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    onUnhide(currentMedia)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.unhide), color = Color.White, fontSize = 16.sp)
        }
    }
}

@Composable
private fun VaultTab(
    selected: Boolean,
    icon: Int,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedColor = Color(0xFF0396FF)
    val unselectedColor = Color(0xFFBDBDBD)

    Column(
        modifier = modifier.fillMaxHeight().clickable(
            indication = null, interactionSource = remember { MutableInteractionSource() }
        ) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.height(40.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = icon),
                contentDescription = text,
                colorFilter = ColorFilter.tint(if (selected) selectedColor else unselectedColor),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(text, color = if (selected) selectedColor else unselectedColor, fontSize = 16.sp)
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(2.dp)
                .background(if (selected) selectedColor else Color.Transparent)
        )
    }
}

@Composable
private fun PhotosVaultContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.imageclick),
                contentDescription = stringResource(R.string.add_image),
                modifier = Modifier.size(80.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.click_to_add_image), color = Color(0xFF9E9E9E), fontSize = 14.sp)
        }
    }
}

@Composable
private fun VideosVaultContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.vedioclick),
                contentDescription = stringResource(R.string.add_video),
                modifier = Modifier.size(80.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.click_to_add_video), color = Color(0xFF9E9E9E), fontSize = 14.sp)
        }
    }
}

@Composable
private fun VaultGalleryScreen(
    context: Context,
    isVideo: Boolean,
    hiddenMedia: List<VaultMediaItem>,
    selectedMedia: Set<Uri>,
    onSelectionChange: (Set<Uri>) -> Unit,
    onBack: () -> Unit,
    onHide: (List<VaultMediaItem>) -> Unit
) {
    var mediaList by remember { mutableStateOf(emptyList<VaultMediaItem>()) }
    var selectedAlbum by remember { mutableStateOf(ALL_ALBUMS_KEY) }
    var albumDropdownOpen by remember { mutableStateOf(false) }
    var showHideDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isVideo, hiddenMedia) {
        mediaList = loadVaultMedia(context, isVideo).filterNot { media ->
            hiddenMedia.any { it.name == media.name && it.isVideo == media.isVideo && it.isVaultFile }
        }
    }

    val albums = remember(mediaList) {
        listOf(ALL_ALBUMS_KEY) + mediaList.map { it.bucketName }
            .filter { it.isNotBlank() }.distinct().sorted()
    }
    val filteredMedia = remember(mediaList, selectedAlbum) {
        if (selectedAlbum == ALL_ALBUMS_KEY) mediaList
        else mediaList.filter { it.bucketName == selectedAlbum }
    }
    val allSelected = filteredMedia.isNotEmpty() && filteredMedia.all { selectedMedia.contains(it.uri) }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        // MainScreen ka bottom bar yahan bhi dikhta hai, is liye navigationBarsPadding() nahi
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(70.dp).padding(start = 15.dp, end = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.backarrow),
                    contentDescription = stringResource(R.string.back),
                    modifier = Modifier.size(24.dp).clickable(
                        indication = null, interactionSource = remember { MutableInteractionSource() }
                    ) {
                        if (albumDropdownOpen) albumDropdownOpen = false else onBack()
                    }
                )
                Spacer(Modifier.width(25.dp))
                Row(
                    modifier = Modifier.clickable(
                        indication = null, interactionSource = remember { MutableInteractionSource() }
                    ) { albumDropdownOpen = !albumDropdownOpen },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (selectedAlbum == ALL_ALBUMS_KEY) stringResource(R.string.all) else selectedAlbum,
                        color = Color(0xFF333333),
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.width(3.dp))
                    Image(
                        painter = painterResource(R.drawable.dropdown),
                        contentDescription = stringResource(R.string.albums),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 80.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(filteredMedia, key = { it.uri.toString() }) { media ->
                    VaultGalleryItem(
                        media = media,
                        selected = selectedMedia.contains(media.uri),
                        selectionMode = true,
                        onClick = {
                            val newSet = selectedMedia.toMutableSet()
                            if (newSet.contains(media.uri)) newSet.remove(media.uri) else newSet.add(media.uri)
                            onSelectionChange(newSet)
                        },
                        onLongClick = { }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(65.dp).background(Color.White).padding(horizontal = 45.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.allimage),
                    contentDescription = stringResource(R.string.select_all),
                    colorFilter = ColorFilter.tint(if (allSelected) Color(0xFF0396FF) else Color(0xFF818181)),
                    modifier = Modifier.size(28.dp).clickable(
                        indication = null, interactionSource = remember { MutableInteractionSource() }
                    ) {
                        val newSet = selectedMedia.toMutableSet()
                        if (allSelected) filteredMedia.forEach { newSet.remove(it.uri) }
                        else filteredMedia.forEach { newSet.add(it.uri) }
                        onSelectionChange(newSet)
                    }
                )
                Image(
                    painter = painterResource(R.drawable.locked),
                    contentDescription = stringResource(R.string.hide),
                    colorFilter = ColorFilter.tint(if (selectedMedia.isNotEmpty()) Color(0xFF0396FF) else Color(0xFF818181)),
                    modifier = Modifier.size(28.dp).clickable(
                        indication = null, interactionSource = remember { MutableInteractionSource() }
                    ) { if (selectedMedia.isNotEmpty()) showHideDialog = true }
                )
            }
        }

        if (albumDropdownOpen) {
            Column(
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding()
                    .padding(start = 55.dp, end = 55.dp, top = 70.dp).fillMaxWidth().zIndex(20f)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
            ) {
                albums.forEach { album ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(
                            indication = null, interactionSource = remember { MutableInteractionSource() }
                        ) {
                            selectedAlbum = album
                            albumDropdownOpen = false
                        }.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (album == ALL_ALBUMS_KEY) stringResource(R.string.all) else album,
                            color = if (album == selectedAlbum) Color(0xFF0396FF) else Color(0xFF333333),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        if (showHideDialog) {
            AlertDialog(
                onDismissRequest = { showHideDialog = false },
                title = {
                    Text(if (isVideo) stringResource(R.string.hide_video) else stringResource(R.string.hide_image))
                },
                text = {
                    Text(if (isVideo) stringResource(R.string.hide_videos_question) else stringResource(R.string.hide_images_question))
                },
                confirmButton = {
                    TextButton(onClick = {
                        showHideDialog = false
                        val itemsToHide = filteredMedia.filter { selectedMedia.contains(it.uri) }
                        if (itemsToHide.isNotEmpty()) onHide(itemsToHide)
                    }) {
                        Text(stringResource(R.string.hide), color = Color(0xFF0396FF))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showHideDialog = false }) {
                        Text(stringResource(R.string.cancel), color = Color(0xFF818181))
                    }
                }
            )
        }
    }
}

private suspend fun loadVaultMedia(context: Context, isVideo: Boolean): List<VaultMediaItem> =
    withContext(Dispatchers.IO) {
        val result = mutableListOf<VaultMediaItem>()
        val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val bucketIndex = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex) ?: ""
                    val bucket = if (bucketIndex >= 0) cursor.getString(bucketIndex) ?: "Unknown" else "Unknown"
                    val uri = Uri.withAppendedPath(collection, id.toString())
                    result.add(VaultMediaItem(uri, name, bucket, isVideo))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }

private fun copyMediaToVault(context: Context, media: VaultMediaItem): VaultMediaItem? {
    return try {
        val vaultDir = File(context.filesDir, if (media.isVideo) "vault_videos" else "vault_photos")
        if (!vaultDir.exists()) vaultDir.mkdirs()

        val extension = media.name.substringAfterLast(".", if (media.isVideo) "mp4" else "jpg")
        val safeName = "${System.currentTimeMillis()}_${media.uri.lastPathSegment ?: media.name.hashCode()}.$extension"
        val vaultFile = File(vaultDir, safeName)

        context.contentResolver.openInputStream(media.uri)?.use { input ->
            vaultFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return null

        vaultThumbnailCache.remove("vault:${vaultFile.absolutePath}")
        VaultMediaItem(
            uri = media.uri,
            name = media.name,
            bucketName = media.bucketName,
            isVideo = media.isVideo,
            isVaultFile = true,
            vaultFilePath = vaultFile.absolutePath
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun saveHiddenMediaMetadata(context: Context, mediaList: List<VaultMediaItem>) {
    val prefs = context.getSharedPreferences("vault_hidden_media", Context.MODE_PRIVATE)
    val editor = prefs.edit()
    editor.clear()
    mediaList.forEachIndexed { index, media ->
        editor.putString("uri_$index", media.uri.toString())
        editor.putString("name_$index", media.name)
        editor.putString("bucket_$index", media.bucketName)
        editor.putBoolean("video_$index", media.isVideo)
        editor.putBoolean("vault_$index", media.isVaultFile)
        editor.putString("path_$index", media.vaultFilePath)
    }
    editor.putInt("count", mediaList.size)
    editor.apply()
}

private fun loadHiddenMediaMetadata(context: Context): List<VaultMediaItem> {
    val prefs = context.getSharedPreferences("vault_hidden_media", Context.MODE_PRIVATE)
    val count = prefs.getInt("count", 0)
    val result = mutableListOf<VaultMediaItem>()

    for (index in 0 until count) {
        val uriString = prefs.getString("uri_$index", null)
        if (uriString != null) {
            val isVideo = prefs.getBoolean("video_$index", false)
            val isVault = prefs.getBoolean("vault_$index", true)
            val path = prefs.getString("path_$index", null)
            if (isVault && path != null && !File(path).exists()) continue

            result.add(
                VaultMediaItem(
                    uri = Uri.parse(uriString),
                    name = prefs.getString("name_$index", "Media") ?: "Media",
                    bucketName = prefs.getString("bucket_$index", "Pictures") ?: "Pictures",
                    isVideo = isVideo,
                    isVaultFile = isVault,
                    vaultFilePath = path
                )
            )
        }
    }
    return result
}

private fun restoreMediaToGallery(context: Context, media: VaultMediaItem): Boolean {
    return try {
        val file = File(media.vaultFilePath ?: return false)
        if (!file.exists()) return false

        val collection = if (media.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val mimeType = if (media.isVideo) getVideoMimeType(media.name) else getImageMimeType(media.name)
        val originalAlbum = media.bucketName.trim().ifBlank { if (media.isVideo) "Movies" else "Pictures" }
        val safeAlbum = originalAlbum.replace("/", "_").replace("\\", "_").trim()
            .ifBlank { if (media.isVideo) "Movies" else "Pictures" }
        val relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (media.isVideo) "Movies/$safeAlbum" else "Pictures/$safeAlbum"
        } else null

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, media.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val newUri = context.contentResolver.insert(collection, values) ?: return false
        try {
            context.contentResolver.openOutputStream(newUri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: throw Exception("Unable to open gallery output stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completeValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                context.contentResolver.update(newUri, completeValues, null, null)
            }

            if (file.exists()) file.delete()
            vaultThumbnailCache.remove("vault:${file.absolutePath}")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            try { context.contentResolver.delete(newUri, null, null) }
            catch (deleteError: Exception) { deleteError.printStackTrace() }
            false
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private fun getImageMimeType(fileName: String): String =
    when (fileName.substringAfterLast(".", "").lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        else -> "image/jpeg"
    }

private fun getVideoMimeType(fileName: String): String =
    when (fileName.substringAfterLast(".", "").lowercase()) {
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "3gp" -> "video/3gpp"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "m4v" -> "video/x-m4v"
        else -> "video/mp4"
    }

private suspend fun loadVaultThumbnail(context: Context, uri: Uri, isVideo: Boolean): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            if (isVideo) {
                if (uri.scheme == "file" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val file = File(uri.path ?: return@withContext null)
                    if (!file.exists()) return@withContext null
                    return@withContext ThumbnailUtils.createVideoThumbnail(file, Size(300, 300), null)
                }

                val retriever = MediaMetadataRetriever()
                try {
                    when (uri.scheme) {
                        "file" -> retriever.setDataSource(uri.path)
                        "vault" -> retriever.setDataSource(uri.schemeSpecificPart)
                        else -> retriever.setDataSource(context, uri)
                    }
                    retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    retriever.release()
                }
            } else {
                when {
                    uri.scheme == "file" -> BitmapFactory.decodeFile(uri.path)
                    uri.scheme == "vault" -> BitmapFactory.decodeFile(uri.schemeSpecificPart)
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> context.contentResolver.loadThumbnail(uri, Size(300, 300), null)
                    else -> context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

private fun getOriginalUriFromVaultItem(media: VaultMediaItem): Uri = media.uri