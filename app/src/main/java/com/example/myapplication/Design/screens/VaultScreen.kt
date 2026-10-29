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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import com.example.myapplication.vault.VaultMediaItem
import com.example.myapplication.vault.VaultRepository
import com.example.myapplication.vault.MediaPermissions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myapplication.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val ALL_ALBUMS_KEY = "ALL"
private val vaultThumbnailCache = object : LruCache<String, Bitmap>(32 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = (value.allocationByteCount / 1024).coerceAtLeast(1)
}

@Composable
fun VaultScreen(onPreviewStateChange: (Boolean) -> Unit = {}) {
    LimitFontScale { VaultScreenContent(onPreviewStateChange) }
}

@Composable
private fun VaultScreenContent(onPreviewStateChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var galleryOpen by rememberSaveable { mutableStateOf(false) }
    var selectedMedia by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var selectionMode by remember { mutableStateOf(false) }
    val repository = remember { VaultRepository.get(context) }
    val hiddenMedia by repository.items.collectAsState()
    var previewPaths by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val previewImages = previewPaths.mapNotNull { path -> hiddenMedia.find { it.vaultFilePath == path } }
    var previewImageIndex by rememberSaveable { mutableStateOf(0) }
    val mediaSaver = listSaver<List<VaultMediaItem>, String>(
        save = { media -> media.flatMap { listOf(it.uri.toString(), it.name, it.bucketName, it.isVideo.toString()) } },
        restore = { saved -> saved.chunked(4).map { VaultMediaItem(Uri.parse(it[0]), it[1], it[2], it[3].toBoolean()) } }
    )
    var pendingHideItems by rememberSaveable(stateSaver = mediaSaver) { mutableStateOf(emptyList()) }
    var consentRequest by rememberSaveable { mutableStateOf<IntentSenderRequest?>(null) }
    var awaitingConsent by rememberSaveable { mutableStateOf(false) }
    var pendingRestorePaths by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var showPermissionSettingsDialog by remember { mutableStateOf(false) }
    var waitingForPermissionSettings by remember { mutableStateOf(false) }
    var processingRequest by remember { mutableStateOf(false) }
    var requestingMediaPermission by rememberSaveable { mutableStateOf(false) }
    val repositoryBusy by repository.isBusy.collectAsState()
    val isVaultProcessing = awaitingConsent || processingRequest || repositoryBusy
    fun canStartTransfer() = !awaitingConsent && !processingRequest && !repository.isBusy.value

    LaunchedEffect(previewImages.isNotEmpty()) {
        onPreviewStateChange(previewImages.isNotEmpty())
    }
    DisposableEffect(Unit) { onDispose { onPreviewStateChange(false) } }

    fun hasMediaPermission() = MediaPermissions.canRead(context, selectedTab == 1) && MediaPermissions.canWriteLegacy(context)

    val mediaPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        requestingMediaPermission = false
        if (hasMediaPermission()) {
            waitingForPermissionSettings = false
            showPermissionSettingsDialog = false
            galleryOpen = true
        } else showPermissionSettingsDialog = true
    }

    fun openGallery() {
        if (requestingMediaPermission || !canStartTransfer()) return
        if (hasMediaPermission()) galleryOpen = true
        else {
            requestingMediaPermission = true
            mediaPermissionLauncher.launch(MediaPermissions.requested(selectedTab == 1))
        }
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

    fun showResult(result: VaultRepository.Result) {
        if (result.failures > 0) Toast.makeText(context, R.string.vault_operation_failed, Toast.LENGTH_LONG).show()
    }

    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        awaitingConsent = false
        val pending = pendingHideItems
        pendingHideItems = emptyList()
        if (result.resultCode == Activity.RESULT_OK && pending.isNotEmpty()) {
            processingRequest = true
            coroutineScope.launch {
                try {
                    val batch = if (Build.VERSION.SDK_INT >= 30) pending.take(2_000) else pending
                    val outcome = repository.hide(batch)
                    if (Build.VERSION.SDK_INT >= 30 && pending.size > batch.size) {
                        awaitingConsent = true
                        pendingHideItems = pending.drop(batch.size)
                        val request = MediaStore.createWriteRequest(context.contentResolver, pendingHideItems.take(2_000).map { it.uri })
                        consentRequest = IntentSenderRequest.Builder(request.intentSender).build()
                        showResult(outcome)
                    } else if (outcome.consent != null) {
                        awaitingConsent = true
                        pendingHideItems = pending.filter { source -> repository.items.value.any { it.uri == source.uri && it.pendingOriginalDelete } }
                        consentRequest = IntentSenderRequest.Builder(outcome.consent.intentSender).build()
                    } else showResult(outcome)
                }
                catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    awaitingConsent = false
                    pendingHideItems = emptyList()
                    Toast.makeText(context, R.string.vault_operation_failed, Toast.LENGTH_LONG).show()
                }
                finally { processingRequest = false; galleryOpen = false; selectedMedia = emptySet(); selectionMode = false }
            }
        } else processingRequest = false
    }

    LaunchedEffect(consentRequest) {
        consentRequest?.let { request ->
            consentRequest = null
            try { writeLauncher.launch(request) } catch (error: Exception) {
                awaitingConsent = false
                pendingHideItems = emptyList()
                Toast.makeText(context, R.string.vault_operation_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    suspend fun restoreItems(media: List<VaultMediaItem>) {
        processingRequest = true
        try {
            val result = repository.restore(media)
            showResult(result)
            if (result.failures == 0) { previewPaths = emptyList(); selectedMedia = emptySet(); selectionMode = false }
        } finally { processingRequest = false }
    }
    val restorePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        awaitingConsent = false
        val paths = pendingRestorePaths
        pendingRestorePaths = emptyList()
        if (granted) {
            processingRequest = true
            coroutineScope.launch {
                try { restoreItems(repository.snapshot().filter { it.vaultFilePath in paths }) }
                catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    Toast.makeText(context, R.string.vault_operation_failed, Toast.LENGTH_LONG).show()
                } finally { processingRequest = false }
            }
        }
        else Toast.makeText(context, R.string.permission_required, Toast.LENGTH_LONG).show()
    }
    fun requestRestore(media: List<VaultMediaItem>) {
        if (!canStartTransfer()) return
        if (MediaPermissions.canWriteLegacy(context)) {
            processingRequest = true
            coroutineScope.launch { restoreItems(media) }
        } else {
            awaitingConsent = true
            pendingRestorePaths = media.mapNotNull { it.vaultFilePath }
            restorePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    if (previewImages.isNotEmpty()) {
        BackHandler {
            if (!isVaultProcessing) {
                previewPaths = emptyList()
                previewImageIndex = 0
            }
        }

        Box(Modifier.fillMaxSize()) {
            VaultImagePreviewScreen(
                mediaList = previewImages,
                initialIndex = previewImageIndex,
                onBack = {
                    if (!isVaultProcessing) {
                        previewPaths = emptyList()
                        previewImageIndex = 0
                    }
                },
                onUnhide = { media -> requestRestore(listOf(media))
                }
            )
            if (isVaultProcessing) VaultProcessingOverlay(awaitingConsent)
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
                    if (itemsToHide.isEmpty() || !canStartTransfer()) return@VaultGalleryScreen

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            awaitingConsent = true
                            val request = MediaStore.createWriteRequest(context.contentResolver, itemsToHide.take(2_000).map { it.uri })
                            pendingHideItems = itemsToHide
                            consentRequest = IntentSenderRequest.Builder(request.intentSender).build()
                        } catch (error: Exception) {
                            awaitingConsent = false
                            Toast.makeText(context, R.string.vault_operation_failed, Toast.LENGTH_LONG).show()
                        }
                    } else {
                        processingRequest = true
                        coroutineScope.launch {
                            try {
                                val result = repository.hide(itemsToHide)
                                if (result.consent != null) {
                                    awaitingConsent = true
                                    pendingHideItems = itemsToHide
                                    consentRequest = IntentSenderRequest.Builder(result.consent.intentSender).build()
                                } else {
                                    showResult(result)
                                    galleryOpen = false
                                }
                            } finally { processingRequest = false; selectedMedia = emptySet(); selectionMode = false }
                        }
                    }
                }
            )
            if (isVaultProcessing) VaultProcessingOverlay(awaitingConsent)
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
                            } else {
                                val imageList = currentHidden
                                val index = imageList.indexOfFirst { it.vaultFilePath == media.vaultFilePath }
                                previewPaths = imageList.mapNotNull { it.vaultFilePath }
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
                        requestRestore(hiddenMedia.filter { selectedMedia.contains(it.uri) })
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

        if (isVaultProcessing) VaultProcessingOverlay(awaitingConsent)

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
private fun VaultProcessingOverlay(awaitingConsent: Boolean = false) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator(color = Color(0xFF0396FF), strokeWidth = 4.dp, modifier = Modifier.size(45.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(if (awaitingConsent) R.string.vault_waiting_permission else R.string.processing),
                color = Color.White, fontSize = 16.sp)
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
        modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = media.name }.combinedClickable(
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

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Image(painter = painterResource(R.drawable.backarrow),
                contentDescription = stringResource(R.string.back), colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(24.dp).clickable { onBack() })
            Spacer(Modifier.width(20.dp))
            Text(currentMedia.name, color = Color.White, fontSize = 16.sp, maxLines = 1,
                softWrap = false, modifier = Modifier.weight(1f).horizontalScroll(filenameScrollState))
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth(),
            key = { mediaList[it].vaultFilePath ?: mediaList[it].uri.toString() }) { page ->
            val media = mediaList[page]
            if (media.isVideo) VaultVideoPlayer(media, active = page == currentPage, modifier = Modifier.fillMaxSize())
            else coil.compose.AsyncImage(model = media.vaultFilePath?.let(::File) ?: media.uri,
                contentDescription = media.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 35.dp, vertical = 15.dp).height(56.dp)
            .clip(RoundedCornerShape(10.dp)).background(Color(0xFF0396FF)).clickable { onUnhide(currentMedia) },
            contentAlignment = Alignment.Center) {
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
        mediaList = loadVaultMedia(context, isVideo)
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
                    retriever.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 300, 300)
                } finally {
                    retriever.release()
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content") {
                    context.contentResolver.loadThumbnail(uri, Size(300, 300), null)
                } else {
                    fun openThumbnailSource() = when (uri.scheme) {
                        "file" -> uri.path?.let(::File)?.inputStream()
                        "vault" -> File(uri.schemeSpecificPart).inputStream()
                        else -> context.contentResolver.openInputStream(uri)
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    openThumbnailSource()?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 300).coerceAtLeast(1)
                    }
                    openThumbnailSource()?.use { BitmapFactory.decodeStream(it, null, options) }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

private fun getOriginalUriFromVaultItem(media: VaultMediaItem): Uri = media.uri
