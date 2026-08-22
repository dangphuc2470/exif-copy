package com.phucdnh.exifcopy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import kotlin.math.roundToInt
import org.json.JSONArray
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.phucdnh.exifcopy.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ShareTargetType {
    DEFAULT,
    SOURCE,
    DESTINATION,
    REMOVE_AI
}

data class ShareIntentData(
    val uris: List<Uri> = emptyList(),
    val targetType: ShareTargetType = ShareTargetType.DEFAULT
)

class MainActivity : ComponentActivity() {
    companion object {
        val globalSourceImages = mutableStateListOf<ImageItem>()
        val globalTargetImages = mutableStateListOf<ImageItem>()
    }

    private val shareDataState = mutableStateOf(ShareIntentData())

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle incoming share intents when app is launched
        shareDataState.value = handleSendIntent(intent)

        enableEdgeToEdge()
        setContent {
            var previewState by remember { mutableStateOf<PreviewState?>(null) }

            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(if (previewState != null) 24.dp else 0.dp),
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    "EXIF Copy",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                            )
                        )
                    }
                ) { innerPadding ->
                    val shareData by shareDataState

                    MainScreen(
                        shareData = shareData,
                        onClearShareData = { shareDataState.value = ShareIntentData() },
                        isPreviewOpen = (previewState != null),
                        onOpenPreview = { uri, isProcessed, onAction ->
                            previewState = PreviewState(uri, isProcessed, onAction)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }

                previewState?.let { state ->
                    ImagePreviewDialog(
                        uri = state.uri,
                        isProcessed = state.isProcessed,
                        onDismiss = { previewState = null },
                        onAction = {
                            state.onAction?.invoke()
                            previewState = null
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shareDataState.value = handleSendIntent(intent)
    }

    private fun handleSendIntent(intent: Intent?): ShareIntentData {
        if (intent == null) return ShareIntentData()
        val uris = mutableListOf<Uri>()
        try {
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    if (intent.type?.startsWith("image/") == true) {
                        val stream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                        }
                        if (stream != null) uris.add(stream)
                    }
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    if (intent.type?.startsWith("image/") == true) {
                        val streams = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                        }
                        if (streams != null) uris.addAll(streams)
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore
        }

        val componentName = intent.component?.className ?: ""
        val targetType = when {
            componentName.endsWith("ShareSourceActivity") -> ShareTargetType.SOURCE
            componentName.endsWith("ShareTargetActivity") -> ShareTargetType.DESTINATION
            componentName.endsWith("ShareRemoveAiActivity") -> ShareTargetType.REMOVE_AI
            else -> ShareTargetType.DEFAULT
        }
        return ShareIntentData(uris = uris, targetType = targetType)
    }
}

data class PreviewState(
    val uri: Uri,
    val isProcessed: Boolean = false,
    val onAction: (() -> Unit)? = null
)

fun deleteProcessedImage(context: Context, uri: Uri) {
    try {
        if (uri.scheme == "content") {
            context.contentResolver.delete(uri, null, null)
        } else if (uri.scheme == "file" || uri.path != null) {
            java.io.File(uri.path!!).delete()
        }
    } catch (_: Exception) {}
}

@Composable
fun MainScreen(
    shareData: ShareIntentData,
    onClearShareData: () -> Unit,
    isPreviewOpen: Boolean = false,
    onOpenPreview: (Uri, Boolean, (() -> Unit)?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Main image lists (persisted across multi-target share intents and multitasking)
    val sourceImages = MainActivity.globalSourceImages
    val targetImages = MainActivity.globalTargetImages

    // Multi-selection states
    val selectedSourceIds = remember { mutableStateListOf<String>() }
    val selectedTargetIds = remember { mutableStateListOf<String>() }

    // SharedPreferences for persistent settings
    val prefs = remember { context.getSharedPreferences("exif_copy_prefs", Context.MODE_PRIVATE) }

    // Persistent recent source images list
    val recentSourceImages = remember {
        mutableStateListOf<Uri>().apply {
            val jsonStr = prefs.getString("recent_source_images", null)
            if (!jsonStr.isNullOrBlank()) {
                try {
                    val array = JSONArray(jsonStr)
                    for (i in 0 until array.length()) {
                        val uriStr = array.getString(i)
                        if (uriStr.isNotBlank()) {
                            add(Uri.parse(uriStr))
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }
    var isRecentExpanded by remember { mutableStateOf(prefs.getBoolean("is_recent_expanded", true)) }

    fun saveRecentUris() {
        try {
            val array = JSONArray()
            recentSourceImages.forEach { array.put(it.toString()) }
            prefs.edit().putString("recent_source_images", array.toString()).apply()
        } catch (_: Exception) {}
    }

    fun addUrisToRecent(uris: List<Uri>) {
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            recentSourceImages.remove(uri)
            recentSourceImages.add(0, uri)
        }
        while (recentSourceImages.size > 30) {
            recentSourceImages.removeAt(recentSourceImages.lastIndex)
        }
        saveRecentUris()
    }

    var appLanguage by remember {
        mutableStateOf(prefs.getString("app_language", AppLanguage.SYSTEM.code) ?: AppLanguage.SYSTEM.code)
    }
    val isVi = remember(appLanguage) { Strings.isVietnamese(appLanguage) }

    // Navigation Tab state
    var activeTab by remember { mutableStateOf(0) } // Default to Tab 0: Sao chép EXIF
    val resultImages = remember { mutableStateListOf<ImageItem>() }

    // Handle incoming shared URIs from external apps based on selected share target
    LaunchedEffect(shareData) {
        if (shareData.uris.isNotEmpty() && shareData.targetType != ShareTargetType.DEFAULT) {
            when (shareData.targetType) {
                ShareTargetType.SOURCE -> {
                    activeTab = 0
                    shareData.uris.forEach { uri ->
                        if (!sourceImages.any { it.uri == uri }) {
                            sourceImages.add(ImageItem(uri))
                        }
                    }
                }
                ShareTargetType.DESTINATION -> {
                    activeTab = 0
                    shareData.uris.forEach { uri ->
                        if (!targetImages.any { it.uri == uri }) {
                            targetImages.add(ImageItem(uri))
                        }
                    }
                }
                ShareTargetType.REMOVE_AI -> {
                    activeTab = 1
                    shareData.uris.forEach { uri ->
                        if (!targetImages.any { it.uri == uri }) {
                            targetImages.add(ImageItem(uri))
                        }
                    }
                }
                ShareTargetType.DEFAULT -> {}
            }
            onClearShareData()
        }
    }

    // EXIF Settings and configurations
    var exifSettings by remember { mutableStateOf(ExifSettings()) }
    var removeWatermark by remember { mutableStateOf(true) }
    var enabledWatermarkModes by remember {
        val saved = prefs.getStringSet("saved_enabled_watermark_modes", null)
        if (saved != null && saved.isNotEmpty()) {
            mutableStateOf(saved.toSet())
        } else {
            mutableStateOf(GeminiWatermarkRemover.WatermarkMode.values().map { it.name }.toSet())
        }
    }
    val visibleWatermarkModes = remember(enabledWatermarkModes) {
        GeminiWatermarkRemover.WatermarkMode.values().filter { enabledWatermarkModes.contains(it.name) }.ifEmpty {
            listOf(GeminiWatermarkRemover.WatermarkMode.REVERSE_ALPHA_AUG19)
        }
    }
    var watermarkMode by remember {
        val savedMode = prefs.getString("saved_watermark_mode", GeminiWatermarkRemover.WatermarkMode.REVERSE_ALPHA_AUG19.name)
        val initialMode = GeminiWatermarkRemover.WatermarkMode.values().find { it.name == savedMode }
            ?: GeminiWatermarkRemover.WatermarkMode.REVERSE_ALPHA_AUG19
        mutableStateOf(
            if (visibleWatermarkModes.contains(initialMode)) initialMode else visibleWatermarkModes.first()
        )
    }
    var autoPrecomputeWatermark by remember {
        mutableStateOf(prefs.getBoolean("saved_auto_precompute_watermark", true))
    }
    var watermarkKeepFileName by remember {
        mutableStateOf(prefs.getBoolean("saved_watermark_keep_file_name", false))
    }
    var watermarkKeepDateTime by remember {
        mutableStateOf(prefs.getBoolean("saved_watermark_keep_date_time", false))
    }
    var reverseAlphaAutoDetect by remember {
        mutableStateOf(prefs.getBoolean("saved_reverse_alpha_auto_detect", false))
    }
    var customWatermarkOffsetX by remember {
        mutableStateOf(prefs.getInt("saved_custom_watermark_offset_x", 0))
    }
    var customWatermarkOffsetY by remember {
        mutableStateOf(prefs.getInt("saved_custom_watermark_offset_y", 0))
    }
    var customWatermarkSize by remember {
        mutableStateOf(prefs.getInt("saved_custom_watermark_size", 48))
    }
    var customWatermarkAutoDetect by remember {
        mutableStateOf(prefs.getBoolean("saved_custom_watermark_auto_detect", false))
    }
    var showWatermarkAdjustment by remember {
        mutableStateOf(prefs.getBoolean("saved_show_watermark_adjustment", true))
    }
    var watermarkPreviewCache by remember {
        mutableStateOf<Map<GeminiWatermarkRemover.WatermarkMode, Bitmap>>(emptyMap())
    }
    var isPrecomputingPreviews by remember {
        mutableStateOf(false)
    }

    val currentTargetItem = targetImages.firstOrNull()
    val currentTargetId = currentTargetItem?.id
    val currentTargetUri = currentTargetItem?.uri

    LaunchedEffect(
        currentTargetId,
        autoPrecomputeWatermark,
        reverseAlphaAutoDetect,
        watermarkMode,
        customWatermarkOffsetX,
        customWatermarkOffsetY,
        customWatermarkSize,
        customWatermarkAutoDetect,
        showWatermarkAdjustment
    ) {
        if (autoPrecomputeWatermark && currentTargetUri != null) {
            isPrecomputingPreviews = true
            watermarkPreviewCache = emptyMap()
            withContext(Dispatchers.IO) {
                val effAutoDetect = if (!showWatermarkAdjustment) true else customWatermarkAutoDetect
                val effOffsetX = if (effAutoDetect) 0 else customWatermarkOffsetX
                val effOffsetY = if (effAutoDetect) 0 else customWatermarkOffsetY
                val effSize = if (effAutoDetect) null else customWatermarkSize
                val previews = WatermarkTimelineHelper.precomputeWatermarkPreviews(
                    context = context,
                    imageUri = currentTargetUri,
                    autoDetectForReverseAlpha = reverseAlphaAutoDetect,
                    preferredMode = watermarkMode,
                    customOffsetX = effOffsetX,
                    customOffsetY = effOffsetY,
                    customSize = effSize
                )
                withContext(Dispatchers.Main) {
                    watermarkPreviewCache = previews ?: emptyMap()
                    isPrecomputingPreviews = false
                }
            }
        } else {
            watermarkPreviewCache = emptyMap()
            isPrecomputingPreviews = false
        }
    }
    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var singleResultUri by remember { mutableStateOf<Uri?>(null) }
    var showOpenImageDialog by remember { mutableStateOf(false) }
    var upscaleBlend by remember { mutableStateOf(false) }

    // State for Tab 2: AI Upscale & Blend
    val blendOriginalImages = remember { mutableStateListOf<ImageItem>() }
    val blendEditedImages = remember { mutableStateListOf<ImageItem>() }
    val blendResultImages = remember { mutableStateListOf<ImageItem>() }
    val selectedBlendOrigIds = remember { mutableStateListOf<String>() }
    val selectedBlendEditIds = remember { mutableStateListOf<String>() }
    var blendDiffThreshold by remember { mutableFloatStateOf(AiImageBlender.DEFAULT_DIFF_THRESHOLD) }
    var blendFeatherSigma by remember { mutableFloatStateOf(AiImageBlender.DEFAULT_FEATHER_SIGMA) }
    var blendProgressState by remember { mutableStateOf<AiImageBlender.BlendProgress?>(null) }
    var isVisualBlendRunning by remember { mutableStateOf(false) }

    // Realtime mask preview state & thumbnail cache for instant slider updates
    var realtimeMaskData by remember { mutableStateOf<AiImageBlender.MaskPreviewData?>(null) }
    var cachedOrigThumb by remember { mutableStateOf<Bitmap?>(null) }
    var cachedEditThumb by remember { mutableStateOf<Bitmap?>(null) }

    val firstOrigUri = blendOriginalImages.firstOrNull()?.uri
    val firstEditUri = blendEditedImages.firstOrNull()?.uri

    LaunchedEffect(firstOrigUri, firstEditUri) {
        if (firstOrigUri != null && firstEditUri != null) {
            withContext(Dispatchers.IO) {
                try {
                    val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                    val rawOrig = context.contentResolver.openInputStream(firstOrigUri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                    val rawEdit = context.contentResolver.openInputStream(firstEditUri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                    if (rawOrig != null && rawEdit != null) {
                        val maxDim = 720
                        val scale = if (kotlin.math.max(rawEdit.width, rawEdit.height) > maxDim) {
                            maxDim.toFloat() / kotlin.math.max(rawEdit.width, rawEdit.height)
                        } else 1.0f
                        val tw = (rawEdit.width * scale).roundToInt().coerceAtLeast(1)
                        val th = (rawEdit.height * scale).roundToInt().coerceAtLeast(1)

                        val thumbOrig = Bitmap.createScaledBitmap(rawOrig, tw, th, true)
                        val thumbEdit = Bitmap.createScaledBitmap(rawEdit, tw, th, true)
                        rawOrig.recycle()
                        rawEdit.recycle()

                        withContext(Dispatchers.Main) {
                            cachedOrigThumb?.recycle()
                            cachedEditThumb?.recycle()
                            cachedOrigThumb = thumbOrig
                            cachedEditThumb = thumbEdit
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RealtimePreview", "Error loading thumbnails: ${e.message}")
                }
            }
        } else {
            cachedOrigThumb?.recycle()
            cachedEditThumb?.recycle()
            cachedOrigThumb = null
            cachedEditThumb = null
            realtimeMaskData = null
        }
    }

    LaunchedEffect(blendDiffThreshold, cachedOrigThumb, cachedEditThumb) {
        val orig = cachedOrigThumb
        val edit = cachedEditThumb
        if (orig != null && edit != null && !orig.isRecycled && !edit.isRecycled) {
            withContext(Dispatchers.Default) {
                val preview = AiImageBlender.generateQuickMaskPreview(
                    origLowBitmap = orig,
                    editedLowBitmap = edit,
                    diffThreshold = blendDiffThreshold
                )
                withContext(Dispatchers.Main) {
                    realtimeMaskData = preview
                }
            }
        } else {
            realtimeMaskData = null
        }
    }

    val pickBlendOriginalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            if (!blendOriginalImages.any { it.uri == uri }) {
                blendOriginalImages.add(ImageItem(uri))
            }
        }
    }

    val pickBlendEditedLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            if (!blendEditedImages.any { it.uri == uri }) {
                blendEditedImages.add(ImageItem(uri))
            }
        }
    }


    // Drag-and-drop controller
    val controller = remember {
        DragDropController(
            sourceList = sourceImages,
            targetList = targetImages
        )
    }

    // Global position trackers for the containers to detect drops
    var sourceContainerBounds by remember { mutableStateOf(Rect.Zero) }
    var targetContainerBounds by remember { mutableStateOf(Rect.Zero) }
    var globalDragPosition by remember { mutableStateOf(Offset.Zero) }
    var mainScreenWindowBounds by remember { mutableStateOf(Rect.Zero) }

    // Launcher to pick source images
    val pickSourceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            if (!sourceImages.any { it.uri == uri }) {
                sourceImages.add(ImageItem(uri))
            }
        }
    }

    // Launcher to pick target images
    val pickTargetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            if (!targetImages.any { it.uri == uri }) {
                targetImages.add(ImageItem(uri))
            }
        }
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { mainScreenWindowBounds = it.boundsInWindow() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Tab Row Navigation (3 Tabs - Đã ẩn Upscale & Blend)
            TabRow(
                selectedTabIndex = activeTab.coerceAtMost(2),
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text(Strings.tabCopyExif(isVi), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(Icons.Default.CopyAll, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text(Strings.tabRemoveAi(isVi), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text(Strings.tabSettings(isVi), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    0 -> {
                        // -----------------------------------------------------------------
                        // TAB 0: SAO CHÉP EXIF
                        // -----------------------------------------------------------------
                        Column(modifier = Modifier.fillMaxSize()) {
                            val tab0ScrollState = rememberScrollState()

                            // SCROLLABLE MIDDLE CONTENT WRAPPER WITH FADING GRADIENT
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(tab0ScrollState)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                    
                    // CONTAINER A: SOURCE IMAGES (Nguồn)
                    val isOverSource = controller.draggedItem != null && 
                            sourceContainerBounds.contains(globalDragPosition)
                    val sourceBorderColor = if (isOverSource) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    val sourceBorderWidth = if (isOverSource) 3.dp else 1.dp

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { sourceContainerBounds = it.boundsInWindow() }
                            .border(
                                border = BorderStroke(sourceBorderWidth, sourceBorderColor),
                                shape = CardDefaults.shape
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                    Text(
                                        text = Strings.sourceImagesTitle(isVi),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = { pickSourceLauncher.launch(arrayOf("image/*")) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(Strings.selectImages(isVi))
                                    }
                            }

                            // RECENT IMAGES ROW (AT THE TOP)
                            if (recentSourceImages.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                isRecentExpanded = !isRecentExpanded
                                                prefs.edit().putBoolean("is_recent_expanded", isRecentExpanded).apply()
                                            }
                                            .padding(vertical = 2.dp, horizontal = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = Strings.recentImages(isVi, recentSourceImages.size),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            imageVector = if (isRecentExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isRecentExpanded) "Ẩn danh sách" else "Hiện danh sách",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (isRecentExpanded) {
                                        Text(
                                            text = Strings.clearRecent(isVi),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .clickable {
                                                    recentSourceImages.clear()
                                                    saveRecentUris()
                                                }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = isRecentExpanded) {
                                    Column {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            contentPadding = PaddingValues(bottom = 4.dp)
                                        ) {
                                            items(recentSourceImages.size, key = { recentSourceImages[it].toString() }) { idx ->
                                                val recentUri = recentSourceImages[idx]
                                                val isAdded = sourceImages.any { it.uri == recentUri }
                                                RecentImageItemCard(
                                                    uri = recentUri,
                                                    isAlreadyAdded = isAdded,
                                                    onAdd = {
                                                        if (!isAdded) {
                                                            sourceImages.add(ImageItem(recentUri))
                                                            Toast.makeText(context, Strings.addedToSource(isVi), Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            Toast.makeText(context, Strings.alreadyInSource(isVi), Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    onRemove = {
                                                        recentSourceImages.remove(recentUri)
                                                        saveRecentUris()
                                                    },
                                                    onPreview = {
                                                        onOpenPreview(it, false) {
                                                            recentSourceImages.remove(recentUri)
                                                            saveRecentUris()
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            // MAIN SOURCE IMAGES LIST
                            if (sourceImages.isEmpty()) {
                                EmptyContainerState(
                                    message = Strings.emptySourceMsg(isVi),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(115.dp)
                                )
                            } else {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    itemsIndexed(sourceImages, key = { _, item -> item.id }) { index, item ->
                                        ImageItemCard(
                                            item = item,
                                            index = index,
                                            isSource = true,
                                            isSelected = selectedSourceIds.contains(item.id),
                                            onToggleSelection = { selected ->
                                                if (selected) {
                                                    selectedSourceIds.add(item.id)
                                                } else {
                                                    selectedSourceIds.remove(item.id)
                                                }
                                            },
                                            selectedIds = selectedSourceIds.toSet(),
                                            onRemove = {
                                                sourceImages.remove(item)
                                                selectedSourceIds.remove(item.id)
                                            },
                                            onGlobalPositionUpdate = { globalDragPosition = it },
                                            controller = controller,
                                            sourceBounds = sourceContainerBounds,
                                            targetBounds = targetContainerBounds,
                                            onDragEnded = { droppedOnTarget ->
                                                if (droppedOnTarget == true) {
                                                    selectedSourceIds.clear()
                                                }
                                            },
                                            onPreview = {
                                                onOpenPreview(it, false) {
                                                    sourceImages.remove(item)
                                                    selectedSourceIds.remove(item.id)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // MIDDLE CONTROLS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                controller.swapAll()
                                val tempSelect = selectedSourceIds.toList()
                                selectedSourceIds.clear()
                                selectedSourceIds.addAll(selectedTargetIds)
                                selectedTargetIds.clear()
                                selectedTargetIds.addAll(tempSelect)
                            }
                        ) {
                            Icon(Icons.Default.SwapVert, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(Strings.swap(isVi))
                        }

                        Button(
                            onClick = { showSettingsDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Strings.exifSettingsBtn(isVi))
                        }
                    }

                    // CONTAINER B: TARGET IMAGES (Đích)
                    val isOverTarget = controller.draggedItem != null && 
                            targetContainerBounds.contains(globalDragPosition)
                    val targetBorderColor = if (isOverTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    val targetBorderWidth = if (isOverTarget) 3.dp else 1.dp

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { targetContainerBounds = it.boundsInWindow() }
                            .border(
                                border = BorderStroke(targetBorderWidth, targetBorderColor),
                                shape = CardDefaults.shape
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = Strings.targetImagesTitle(isVi),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { pickTargetLauncher.launch(arrayOf("image/*")) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(Strings.selectImages(isVi))
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (targetImages.isEmpty()) {
                                EmptyContainerState(
                                    message = Strings.emptyTargetMsg(isVi),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(115.dp)
                                )
                            } else {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    itemsIndexed(targetImages, key = { _, item -> item.id }) { index, item ->
                                        ImageItemCard(
                                            item = item,
                                            index = index,
                                            isSource = false,
                                            isSelected = selectedTargetIds.contains(item.id),
                                            onToggleSelection = { selected ->
                                                if (selected) {
                                                    selectedTargetIds.add(item.id)
                                                } else {
                                                    selectedTargetIds.remove(item.id)
                                                }
                                            },
                                            selectedIds = selectedTargetIds.toSet(),
                                            onRemove = {
                                                targetImages.remove(item)
                                                selectedTargetIds.remove(item.id)
                                            },
                                            onGlobalPositionUpdate = { globalDragPosition = it },
                                            controller = controller,
                                            sourceBounds = sourceContainerBounds,
                                            targetBounds = targetContainerBounds,
                                            onDragEnded = { droppedOnSource ->
                                                if (droppedOnSource == false) {
                                                    selectedTargetIds.clear()
                                                }
                                            },
                                            onPreview = {
                                                onOpenPreview(it, false) {
                                                    targetImages.remove(item)
                                                    selectedTargetIds.remove(item.id)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // CONTAINER C: RESULT IMAGES
                    if (resultImages.isNotEmpty()) {
                        ResultImagesSection(
                            resultImages = resultImages,
                            onClearResults = {
                                resultImages.forEach { deleteProcessedImage(context, it.uri) }
                                resultImages.clear()
                            },
                            onRemoveResult = {
                                deleteProcessedImage(context, it.uri)
                                resultImages.remove(it)
                            },
                            onPreview = { uri ->
                                onOpenPreview(uri, true) {
                                    deleteProcessedImage(context, uri)
                                    resultImages.removeAll { it.uri == uri }
                                }
                            },
                            isVi = isVi
                        )
                    }
                                }
                                // Bottom fading gradient when content can scroll further
                                if (tab0ScrollState.canScrollForward) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .height(36.dp)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                                                        MaterialTheme.colorScheme.background
                                                    )
                                                )
                                            )
                                    )
                                }
                            }

                            // STICKY BOTTOM BAR (Always pinned at the bottom)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // SETTINGS & CONTROL BAR
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .clickable { removeWatermark = !removeWatermark }
                                    ) {
                                        Checkbox(
                                            checked = removeWatermark,
                                            onCheckedChange = { removeWatermark = it }
                                        )
                                        Text(
                                            text = Strings.removeWatermarkCheckbox(isVi),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (removeWatermark) {
                                        Spacer(modifier = Modifier.width(12.dp))
                                        InteractiveWatermarkDropdown(
                                            selectedMode = watermarkMode,
                                            visibleModes = visibleWatermarkModes,
                                            onModeSelected = { newMode ->
                                                watermarkMode = newMode
                                                prefs.edit().putString("saved_watermark_mode", newMode.name).apply()
                                            },
                                            isVi = isVi,
                                            autoPrecompute = autoPrecomputeWatermark,
                                            previewCache = watermarkPreviewCache,
                                            isComputing = isPrecomputingPreviews,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    }
                                }

                                if (showWatermarkAdjustment && removeWatermark && !GeminiWatermarkRemover.isReverseAlphaMode(watermarkMode)) {
                                    WatermarkAdjustmentCard(
                                        offsetX = customWatermarkOffsetX,
                                        onOffsetXChange = { customWatermarkOffsetX = it },
                                        offsetY = customWatermarkOffsetY,
                                        onOffsetYChange = { customWatermarkOffsetY = it },
                                        boxSize = customWatermarkSize,
                                        onBoxSizeChange = { customWatermarkSize = it },
                                        autoDetect = customWatermarkAutoDetect,
                                        onAutoDetectChange = {
                                            customWatermarkAutoDetect = it
                                            prefs.edit().putBoolean("saved_custom_watermark_auto_detect", it).apply()
                                        },
                                        onSave = {
                                            prefs.edit()
                                                .putInt("saved_custom_watermark_offset_x", customWatermarkOffsetX)
                                                .putInt("saved_custom_watermark_offset_y", customWatermarkOffsetY)
                                                .putInt("saved_custom_watermark_size", customWatermarkSize)
                                                .putBoolean("saved_custom_watermark_auto_detect", customWatermarkAutoDetect)
                                                .apply()
                                            Toast.makeText(context, Strings.savedCustomPosToast(isVi), Toast.LENGTH_SHORT).show()
                                        },
                                        onReset = {
                                            customWatermarkOffsetX = 0
                                            customWatermarkOffsetY = 0
                                            customWatermarkSize = 48
                                            customWatermarkAutoDetect = false
                                            prefs.edit()
                                                .putInt("saved_custom_watermark_offset_x", 0)
                                                .putInt("saved_custom_watermark_offset_y", 0)
                                                .putInt("saved_custom_watermark_size", 48)
                                                .putBoolean("saved_custom_watermark_auto_detect", false)
                                                .apply()
                                            Toast.makeText(context, Strings.resetCustomPosToast(isVi), Toast.LENGTH_SHORT).show()
                                        },
                                        targetImageUri = currentTargetUri,
                                        isVi = isVi
                                    )
                                }

                                // Upscale & Blend checkbox (same row below, or separate row)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .clickable { upscaleBlend = !upscaleBlend }
                                    ) {
                                        Checkbox(
                                            checked = upscaleBlend,
                                            onCheckedChange = { upscaleBlend = it }
                                        )
                                        Text(
                                            text = Strings.upscaleBlendCheckbox(isVi),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (upscaleBlend) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            Icons.Default.AutoFixHigh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }


                                // ACTION BUTTON (Full Width)
                                Button(
                                    onClick = {
                                        if (sourceImages.isEmpty()) {
                                            Toast.makeText(context, Strings.selectAtLeastOneSource(isVi), Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        if (targetImages.isEmpty()) {
                                            Toast.makeText(context, Strings.selectAtLeastOneTarget(isVi), Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }

                                        // Start execution in background thread
                                        isProcessing = true
                                        processingMessage = if (upscaleBlend) Strings.processingUpscaleBlend(isVi) else Strings.processingCopyExif(isVi)
                                        
                                        // Clear old logs and start new log stream
                                        ExifMetadataHelper.clearLog(context)
                                        ExifMetadataHelper.log(context, "Bắt đầu tiến trình sao chép EXIF cho ${targetImages.size} ảnh (Xóa Watermark=$removeWatermark, Mode=${watermarkMode.displayName}, Upscale&Blend=$upscaleBlend)")
                                        
                                        scope.launch {
                                            var successCount = 0
                                            var lastOutputUri: Uri? = null
                                            val targetCopy = targetImages.toList()
                                            val sourceCopy = sourceImages.toList()
                                            val createdUris = mutableListOf<Uri>()

                                            // Save source images to recent only upon copying EXIF
                                            addUrisToRecent(sourceCopy.map { it.uri })

                                            withContext(Dispatchers.IO) {
                                                for (i in targetCopy.indices) {
                                                    val targetItem = targetCopy[i]
                                                    val sourceItem = if (sourceCopy.size == 1) {
                                                        sourceCopy[0]
                                                    } else {
                                                        sourceCopy[i % sourceCopy.size]
                                                    }

                                                    val activeSettings = exifSettings
                                                    val effAutoDetect = if (!showWatermarkAdjustment) true else customWatermarkAutoDetect
                                                    val effOffsetX = if (effAutoDetect) 0 else customWatermarkOffsetX
                                                    val effOffsetY = if (effAutoDetect) 0 else customWatermarkOffsetY
                                                    val effSize = if (effAutoDetect) null else customWatermarkSize

                                                    val outputUris = ExifMetadataHelper.copyMetadataList(
                                                        context = context,
                                                        sourceUri = sourceItem.uri,
                                                        targetUri = targetItem.uri,
                                                        settings = activeSettings,
                                                        replaceOriginal = false,
                                                        removeWatermark = removeWatermark,
                                                        watermarkMode = watermarkMode,
                                                        upscaleBlend = upscaleBlend,
                                                        customOffsetX = effOffsetX,
                                                        customOffsetY = effOffsetY,
                                                        customSize = effSize,
                                                        itemIndex = i
                                                    )
                                                    if (outputUris.isNotEmpty()) {
                                                        successCount += outputUris.size
                                                        lastOutputUri = outputUris.last()
                                                        createdUris.addAll(outputUris)
                                                    }
                                                }
                                            }

                                            isProcessing = false
                                            createdUris.forEach { uri ->
                                                if (!resultImages.any { it.uri == uri }) {
                                                    resultImages.add(0, ImageItem(uri))
                                                }
                                            }
                                            val resultMsg = Strings.savedResultToast(isVi, successCount, targetCopy.size)
                                            Toast.makeText(context, resultMsg, Toast.LENGTH_LONG).show()
                                            if (lastOutputUri != null) {
                                                singleResultUri = lastOutputUri
                                                showOpenImageDialog = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(Strings.copyExifAction(isVi))
                                }
                                Spacer(modifier = Modifier.height(40.dp))
                            }
                        }
                    }
                    1 -> {
                        // -----------------------------------------------------------------
                        // TAB 1: XÓA NHÃN AI
                        // -----------------------------------------------------------------
                        Column(modifier = Modifier.fillMaxSize()) {
                            val tab1ScrollState = rememberScrollState()

                            // SCROLLABLE MIDDLE CONTENT WRAPPER WITH FADING GRADIENT
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(tab1ScrollState)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // CONTAINER B: TARGET IMAGES (Chỉ chọn ảnh cần xóa nhãn)
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                shape = CardDefaults.shape
                                            ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = Strings.targetImagesToProcessTitle(isVi),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Button(
                                                    onClick = { pickTargetLauncher.launch(arrayOf("image/*")) },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(Strings.selectImages(isVi))
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            if (targetImages.isEmpty()) {
                                                EmptyContainerState(
                                                    message = Strings.emptyAiTargetMsg(isVi),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(140.dp)
                                                )
                                            } else {
                                                LazyRow(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(130.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    contentPadding = PaddingValues(vertical = 4.dp)
                                                ) {
                                                    itemsIndexed(targetImages, key = { _, item -> item.id }) { index, item ->
                                                        ImageItemCard(
                                                            item = item,
                                                            index = index,
                                                            isSource = false,
                                                            isSelected = selectedTargetIds.contains(item.id),
                                                            onToggleSelection = { selected ->
                                                                if (selected) {
                                                                    selectedTargetIds.add(item.id)
                                                                } else {
                                                                    selectedTargetIds.remove(item.id)
                                                                }
                                                            },
                                                            selectedIds = selectedTargetIds.toSet(),
                                                            onRemove = {
                                                                targetImages.remove(item)
                                                                selectedTargetIds.remove(item.id)
                                                            },
                                                            onGlobalPositionUpdate = { },
                                                            controller = controller,
                                                            sourceBounds = Rect.Zero,
                                                            targetBounds = Rect.Zero,
                                                            onDragEnded = { },
                                                            onPreview = {
                                                                onOpenPreview(it, false) {
                                                                    targetImages.remove(item)
                                                                    selectedTargetIds.remove(item.id)
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // CONTAINER C: RESULT IMAGES
                                    if (resultImages.isNotEmpty()) {
                                        ResultImagesSection(
                                            resultImages = resultImages,
                                            onClearResults = {
                                                resultImages.forEach { deleteProcessedImage(context, it.uri) }
                                                resultImages.clear()
                                            },
                                            onRemoveResult = {
                                                deleteProcessedImage(context, it.uri)
                                                resultImages.remove(it)
                                            },
                                            onPreview = { uri ->
                                                onOpenPreview(uri, true) {
                                                    deleteProcessedImage(context, uri)
                                                    resultImages.removeAll { it.uri == uri }
                                                }
                                            },
                                            isVi = isVi
                                        )
                                    }
                                }

                                // Bottom fading gradient when content can scroll further
                                if (tab1ScrollState.canScrollForward) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .height(36.dp)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                                                        MaterialTheme.colorScheme.background
                                                    )
                                                )
                                            )
                                    )
                                }
                            }

                            // STICKY BOTTOM BAR (Always pinned at the bottom)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // SETTINGS & CONTROL BAR
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .clickable { removeWatermark = !removeWatermark }
                                    ) {
                                        Checkbox(
                                            checked = removeWatermark,
                                            onCheckedChange = { removeWatermark = it }
                                        )
                                        Text(
                                            text = Strings.removeWatermarkCheckbox(isVi),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (removeWatermark) {
                                        Spacer(modifier = Modifier.width(12.dp))
                                        InteractiveWatermarkDropdown(
                                            selectedMode = watermarkMode,
                                            visibleModes = visibleWatermarkModes,
                                            onModeSelected = { newMode ->
                                                watermarkMode = newMode
                                                prefs.edit().putString("saved_watermark_mode", newMode.name).apply()
                                            },
                                            isVi = isVi,
                                            autoPrecompute = autoPrecomputeWatermark,
                                            previewCache = watermarkPreviewCache,
                                            isComputing = isPrecomputingPreviews,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    }
                                }

                                if (showWatermarkAdjustment && removeWatermark && !GeminiWatermarkRemover.isReverseAlphaMode(watermarkMode)) {
                                    WatermarkAdjustmentCard(
                                        offsetX = customWatermarkOffsetX,
                                        onOffsetXChange = { customWatermarkOffsetX = it },
                                        offsetY = customWatermarkOffsetY,
                                        onOffsetYChange = { customWatermarkOffsetY = it },
                                        boxSize = customWatermarkSize,
                                        onBoxSizeChange = { customWatermarkSize = it },
                                        autoDetect = customWatermarkAutoDetect,
                                        onAutoDetectChange = {
                                            customWatermarkAutoDetect = it
                                            prefs.edit().putBoolean("saved_custom_watermark_auto_detect", it).apply()
                                        },
                                        onSave = {
                                            prefs.edit()
                                                .putInt("saved_custom_watermark_offset_x", customWatermarkOffsetX)
                                                .putInt("saved_custom_watermark_offset_y", customWatermarkOffsetY)
                                                .putInt("saved_custom_watermark_size", customWatermarkSize)
                                                .putBoolean("saved_custom_watermark_auto_detect", customWatermarkAutoDetect)
                                                .apply()
                                            Toast.makeText(context, Strings.savedCustomPosToast(isVi), Toast.LENGTH_SHORT).show()
                                        },
                                        onReset = {
                                            customWatermarkOffsetX = 0
                                            customWatermarkOffsetY = 0
                                            customWatermarkSize = 48
                                            customWatermarkAutoDetect = false
                                            prefs.edit()
                                                .putInt("saved_custom_watermark_offset_x", 0)
                                                .putInt("saved_custom_watermark_offset_y", 0)
                                                .putInt("saved_custom_watermark_size", 48)
                                                .putBoolean("saved_custom_watermark_auto_detect", false)
                                                .apply()
                                            Toast.makeText(context, Strings.resetCustomPosToast(isVi), Toast.LENGTH_SHORT).show()
                                        },
                                        targetImageUri = currentTargetUri,
                                        isVi = isVi
                                    )
                                }

                                // ACTION BUTTON (Full Width)
                                Button(
                                    onClick = {
                                        if (targetImages.isEmpty()) {
                                            Toast.makeText(context, Strings.selectAiTarget(isVi), Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }

                                        isProcessing = true
                                        processingMessage = if (removeWatermark) Strings.processingRemoveAiAndWatermark(isVi) else Strings.processingRemoveAiOnly(isVi)
                                        
                                        ExifMetadataHelper.clearLog(context)
                                        ExifMetadataHelper.log(context, "Bắt đầu tiến trình xóa nhãn Google AI cho ${targetImages.size} ảnh (Xóa Watermark=$removeWatermark, Mode=${watermarkMode.displayName})")
                                        
                                        scope.launch {
                                            var successCount = 0
                                            var lastOutputUri: Uri? = null
                                            val targetCopy = targetImages.toList()
                                            val createdUris = mutableListOf<Uri>()

                                            withContext(Dispatchers.IO) {
                                                for (i in targetCopy.indices) {
                                                    val targetItem = targetCopy[i]
                                                    val effAutoDetect = if (!showWatermarkAdjustment) true else customWatermarkAutoDetect
                                                    val effOffsetX = if (effAutoDetect) 0 else customWatermarkOffsetX
                                                    val effOffsetY = if (effAutoDetect) 0 else customWatermarkOffsetY
                                                    val effSize = if (effAutoDetect) null else customWatermarkSize

                                                    val outputUris = ExifMetadataHelper.cleanGoogleAiMetadataList(
                                                        context = context,
                                                        targetUri = targetItem.uri,
                                                        replaceOriginal = false,
                                                        removeWatermark = removeWatermark,
                                                        watermarkMode = watermarkMode,
                                                        keepOriginalFileName = watermarkKeepFileName,
                                                        keepOriginalDateTime = watermarkKeepDateTime,
                                                        autoDetectForReverseAlpha = reverseAlphaAutoDetect,
                                                        customOffsetX = effOffsetX,
                                                        customOffsetY = effOffsetY,
                                                        customSize = effSize
                                                    )
                                                    if (outputUris.isNotEmpty()) {
                                                        successCount += outputUris.size
                                                        lastOutputUri = outputUris.last()
                                                        createdUris.addAll(outputUris)
                                                    }
                                                }
                                            }

                                            isProcessing = false
                                            createdUris.forEach { uri ->
                                                if (!resultImages.any { it.uri == uri }) {
                                                    resultImages.add(0, ImageItem(uri))
                                                }
                                            }
                                            val resultMsg = Strings.savedResultToast(isVi, successCount, targetCopy.size)
                                            Toast.makeText(context, resultMsg, Toast.LENGTH_LONG).show()
                                            if (lastOutputUri != null) {
                                                singleResultUri = lastOutputUri
                                                showOpenImageDialog = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(Strings.removeAiAction(isVi))
                                }
                                Spacer(modifier = Modifier.height(40.dp))
                            }
                        }
                    }
                    99 -> {
                        // -----------------------------------------------------------------
                        // TAB: GHÉP & PHÓNG TO AI (UPSCALE & BLEND - ẨN)
                        // -----------------------------------------------------------------
                        Column(modifier = Modifier.fillMaxSize()) {
                            val tab2ScrollState = rememberScrollState()

                            // SCROLLABLE MIDDLE CONTENT WRAPPER WITH FADING GRADIENT
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(tab2ScrollState)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // CONTAINER 1: ORIGINAL HIGH-RES (A)
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                shape = CardDefaults.shape
                                            ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = Strings.upscaleOriginalTitle(isVi),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Button(
                                                    onClick = { pickBlendOriginalLauncher.launch(arrayOf("image/*")) },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(Strings.selectImages(isVi))
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            if (blendOriginalImages.isEmpty()) {
                                                EmptyContainerState(
                                                    message = Strings.emptyUpscaleOriginalMsg(isVi),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(130.dp)
                                                )
                                            } else {
                                                LazyRow(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(130.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    contentPadding = PaddingValues(vertical = 4.dp)
                                                ) {
                                                    itemsIndexed(blendOriginalImages, key = { _, item -> item.id }) { index, item ->
                                                        ImageItemCard(
                                                            item = item,
                                                            index = index,
                                                            isSource = true,
                                                            isSelected = selectedBlendOrigIds.contains(item.id),
                                                            onToggleSelection = { selected ->
                                                                if (selected) selectedBlendOrigIds.add(item.id) else selectedBlendOrigIds.remove(item.id)
                                                            },
                                                            selectedIds = selectedBlendOrigIds.toSet(),
                                                            onRemove = {
                                                                blendOriginalImages.remove(item)
                                                                selectedBlendOrigIds.remove(item.id)
                                                            },
                                                            onGlobalPositionUpdate = { },
                                                            controller = controller,
                                                            sourceBounds = Rect.Zero,
                                                            targetBounds = Rect.Zero,
                                                            onDragEnded = { },
                                                            onPreview = {
                                                                onOpenPreview(it, false) {
                                                                    blendOriginalImages.remove(item)
                                                                    selectedBlendOrigIds.remove(item.id)
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // CONTAINER 2: AI EDITED LOW-RES (B)
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                shape = CardDefaults.shape
                                            ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = Strings.upscaleEditedTitle(isVi),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Button(
                                                    onClick = { pickBlendEditedLauncher.launch(arrayOf("image/*")) },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(Strings.selectImages(isVi))
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            if (blendEditedImages.isEmpty()) {
                                                EmptyContainerState(
                                                    message = Strings.emptyUpscaleEditedMsg(isVi),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(130.dp)
                                                )
                                            } else {
                                                LazyRow(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(130.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    contentPadding = PaddingValues(vertical = 4.dp)
                                                ) {
                                                    itemsIndexed(blendEditedImages, key = { _, item -> item.id }) { index, item ->
                                                        ImageItemCard(
                                                            item = item,
                                                            index = index,
                                                            isSource = false,
                                                            isSelected = selectedBlendEditIds.contains(item.id),
                                                            onToggleSelection = { selected ->
                                                                if (selected) selectedBlendEditIds.add(item.id) else selectedBlendEditIds.remove(item.id)
                                                            },
                                                            selectedIds = selectedBlendEditIds.toSet(),
                                                            onRemove = {
                                                                blendEditedImages.remove(item)
                                                                selectedBlendEditIds.remove(item.id)
                                                            },
                                                            onGlobalPositionUpdate = { },
                                                            controller = controller,
                                                            sourceBounds = Rect.Zero,
                                                            targetBounds = Rect.Zero,
                                                            onDragEnded = { },
                                                            onPreview = {
                                                                onOpenPreview(it, false) {
                                                                    blendEditedImages.remove(item)
                                                                    selectedBlendEditIds.remove(item.id)
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // TUNING SETTINGS ACCORDION CARD
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Tune,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = Strings.upscaleBlendSettingsTitle(isVi),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }

                                            // Slider 1: Diff Threshold (Expanded range 2..200)
                                            Column {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = Strings.diffThresholdSlider(isVi, blendDiffThreshold.toInt()),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = "2 - 200",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                }
                                                Slider(
                                                    value = blendDiffThreshold,
                                                    onValueChange = { blendDiffThreshold = it },
                                                    valueRange = 2f..200f,
                                                    modifier = Modifier.height(24.dp)
                                                )
                                            }

                                            // Slider 2: Feather Sigma (Expanded range 0..40px)
                                            Column {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = Strings.featherSigmaSlider(isVi, blendFeatherSigma.toInt()),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = "0 - 40px",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                }
                                                Slider(
                                                    value = blendFeatherSigma,
                                                    onValueChange = { blendFeatherSigma = it },
                                                    valueRange = 0f..40f,
                                                    modifier = Modifier.height(24.dp)
                                                )
                                            }

                                            // Realtime Mask Preview Display (Expanded HD Aspect Ratio)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                            Spacer(modifier = Modifier.height(6.dp))

                                            val maskData = realtimeMaskData
                                            if (maskData != null) {
                                                Column(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(8.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color.Red)
                                                            )
                                                            Text(
                                                                text = Strings.realtimeMaskPreviewTitle(isVi),
                                                                style = MaterialTheme.typography.labelMedium,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                        if (maskData.diffPixelCount > 0 && maskData.totalPixelCount > 0) {
                                                            val pct = (maskData.diffPixelCount.toFloat() / maskData.totalPixelCount.toFloat()) * 100f
                                                            Text(
                                                                text = Strings.diffPixelsDetectedShort(isVi, maskData.diffPixelCount, pct),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = Color(0xFFFF5252),
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        }
                                                    }

                                                    val previewBitmap = maskData.previewBitmap
                                                    val aspect = (previewBitmap.width.toFloat() / previewBitmap.height.toFloat().coerceAtLeast(1f)).coerceIn(0.5f, 2.2f)

                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(Color.Black)
                                                            .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Image(
                                                            bitmap = previewBitmap.asImageBitmap(),
                                                            contentDescription = "Realtime Mask Preview",
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .aspectRatio(aspect)
                                                                .clip(RoundedCornerShape(10.dp)),
                                                            contentScale = ContentScale.Fit
                                                        )
                                                    }
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = Strings.selectBothImagesToPreview(isVi),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }



                                    // CONTAINER 3: RESULT IMAGES
                                    if (blendResultImages.isNotEmpty()) {
                                        ResultImagesSection(
                                            resultImages = blendResultImages,
                                            onClearResults = {
                                                blendResultImages.forEach { deleteProcessedImage(context, it.uri) }
                                                blendResultImages.clear()
                                            },
                                            onRemoveResult = {
                                                deleteProcessedImage(context, it.uri)
                                                blendResultImages.remove(it)
                                            },
                                            onPreview = { uri ->
                                                onOpenPreview(uri, true) {
                                                    deleteProcessedImage(context, uri)
                                                    blendResultImages.removeAll { it.uri == uri }
                                                }
                                            },
                                            isVi = isVi
                                        )
                                    }
                                }

                                // Bottom fading gradient
                                if (tab2ScrollState.canScrollForward) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .height(36.dp)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                                                        MaterialTheme.colorScheme.background
                                                    )
                                                )
                                            )
                                    )
                                }
                            }

                            // STICKY BOTTOM ACTION BUTTON
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (blendOriginalImages.isEmpty() || blendEditedImages.isEmpty()) {
                                            Toast.makeText(context, Strings.selectBothOriginalAndEdited(isVi), Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }

                                        isVisualBlendRunning = true
                                        blendProgressState = AiImageBlender.BlendProgress(
                                            step = 1,
                                            percent = 0.05f,
                                            messageVi = "Đang chuẩn bị dữ liệu...",
                                            messageEn = "Preparing image data..."
                                        )

                                        ExifMetadataHelper.clearLog(context)
                                        ExifMetadataHelper.log(context, "Bắt đầu Upscale & Blend độc lập (Ngưỡng=$blendDiffThreshold, Feather=$blendFeatherSigma)")

                                        scope.launch {
                                            var lastOutputUri: Uri? = null
                                            val origCopy = blendOriginalImages.toList()
                                            val editCopy = blendEditedImages.toList()
                                            val createdUris = mutableListOf<Uri>()

                                            withContext(Dispatchers.IO) {
                                                for (i in editCopy.indices) {
                                                    val editItem = editCopy[i]
                                                    val origItem = if (origCopy.size == 1) origCopy[0] else origCopy[i % origCopy.size]

                                                    val outputUri = ExifMetadataHelper.runUpscaleBlendWithProgress(
                                                        context = context,
                                                        sourceUri = origItem.uri,
                                                        editedUri = editItem.uri,
                                                        diffThreshold = blendDiffThreshold,
                                                        featherSigma = blendFeatherSigma,
                                                        onProgress = { progress ->
                                                            scope.launch(Dispatchers.Main) {
                                                                blendProgressState = progress
                                                            }
                                                        }
                                                    )

                                                    if (outputUri != null) {
                                                        lastOutputUri = outputUri
                                                        createdUris.add(outputUri)
                                                    }
                                                }
                                            }

                                            // Small delay so user sees 100% complete state
                                            kotlinx.coroutines.delay(400)
                                            isVisualBlendRunning = false

                                            createdUris.forEach { uri ->
                                                if (!blendResultImages.any { it.uri == uri }) {
                                                    blendResultImages.add(0, ImageItem(uri))
                                                }
                                            }

                                            Toast.makeText(context, Strings.blendSuccessToast(isVi), Toast.LENGTH_LONG).show()
                                            if (lastOutputUri != null) {
                                                singleResultUri = lastOutputUri
                                                showOpenImageDialog = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(Strings.upscaleBlendAction(isVi))
                                }
                            }
                        }
                    }
                    2 -> {
                        // -----------------------------------------------------------------
                        // TAB 2: CÀI ĐẶT (SETTINGS)
                        // -----------------------------------------------------------------
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SettingsTabScreen(
                                currentLanguage = appLanguage,
                                onLanguageChange = { newLang ->
                                    appLanguage = newLang
                                    prefs.edit().putString("app_language", newLang).apply()
                                },
                                watermarkMode = watermarkMode,
                                onWatermarkModeChange = { newMode ->
                                    watermarkMode = newMode
                                    prefs.edit().putString("saved_watermark_mode", newMode.name).apply()
                                },
                                enabledWatermarkModes = enabledWatermarkModes,
                                onToggleWatermarkMode = { mode ->
                                    val current = enabledWatermarkModes.toMutableSet()
                                    if (current.contains(mode.name)) {
                                        if (current.size > 1) {
                                            current.remove(mode.name)
                                        }
                                    } else {
                                        current.add(mode.name)
                                    }
                                    enabledWatermarkModes = current
                                    prefs.edit().putStringSet("saved_enabled_watermark_modes", current).apply()
                                    if (!current.contains(watermarkMode.name)) {
                                        val fallback = GeminiWatermarkRemover.WatermarkMode.values().find { current.contains(it.name) }
                                            ?: GeminiWatermarkRemover.WatermarkMode.REVERSE_ALPHA_AUG19
                                        watermarkMode = fallback
                                        prefs.edit().putString("saved_watermark_mode", fallback.name).apply()
                                    }
                                },
                                autoPrecomputeWatermark = autoPrecomputeWatermark,
                                onToggleAutoPrecompute = { enabled ->
                                    autoPrecomputeWatermark = enabled
                                    prefs.edit().putBoolean("saved_auto_precompute_watermark", enabled).apply()
                                },
                                watermarkKeepFileName = watermarkKeepFileName,
                                onToggleWatermarkKeepFileName = { enabled ->
                                    watermarkKeepFileName = enabled
                                    prefs.edit().putBoolean("saved_watermark_keep_file_name", enabled).apply()
                                },
                                watermarkKeepDateTime = watermarkKeepDateTime,
                                onToggleWatermarkKeepDateTime = { enabled ->
                                    watermarkKeepDateTime = enabled
                                    prefs.edit().putBoolean("saved_watermark_keep_date_time", enabled).apply()
                                },
                                reverseAlphaAutoDetect = reverseAlphaAutoDetect,
                                onToggleReverseAlphaAutoDetect = { enabled ->
                                    reverseAlphaAutoDetect = enabled
                                    prefs.edit().putBoolean("saved_reverse_alpha_auto_detect", enabled).apply()
                                },
                                showWatermarkAdjustment = showWatermarkAdjustment,
                                onToggleShowWatermarkAdjustment = { enabled ->
                                    showWatermarkAdjustment = enabled
                                    prefs.edit().putBoolean("saved_show_watermark_adjustment", enabled).apply()
                                },
                                onOpenExifSettings = { showSettingsDialog = true },
                                isVi = isVi
                            )
                        }
                    }
                }
            }
        }

        // -----------------------------------------------------------------
        // SETTINGS DIALOG
        // -----------------------------------------------------------------
        if (showSettingsDialog) {
            Box(modifier = Modifier.blur(if (isPreviewOpen) 24.dp else 0.dp)) {
                SettingsDialog(
                    currentSettings = exifSettings,
                    onDismiss = { showSettingsDialog = false },
                    onSave = {
                        exifSettings = it
                        showSettingsDialog = false
                        Toast.makeText(context, Strings.updatedSettings(isVi), Toast.LENGTH_SHORT).show()
                    },
                    isVi = isVi
                )
            }
        }

        // -----------------------------------------------------------------
        // PROCESSING LOADING OVERLAY
        // -----------------------------------------------------------------
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.width(280.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = processingMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // -----------------------------------------------------------------
        // VISUAL UPSCALE & BLEND LOADING DIALOG (WITH RED MASK PREVIEW)
        // -----------------------------------------------------------------
        if (isVisualBlendRunning) {
            VisualBlendLoadingDialog(
                progress = blendProgressState,
                isVi = isVi
            )
        }


        // -----------------------------------------------------------------
        // DRAG GHOST FLOATING THUMBNAIL
        // -----------------------------------------------------------------
        val density = LocalContext.current.resources.displayMetrics.density
        controller.draggedItem?.let { item ->
            val halfSizePx = (40 * density).toInt()
            val localDragX = globalDragPosition.x - mainScreenWindowBounds.left
            val localDragY = globalDragPosition.y - mainScreenWindowBounds.top
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(
                    (localDragX - halfSizePx).toInt(),
                    (localDragY - halfSizePx).toInt()
                )
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .shadow(8.dp, shape = RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.LightGray)
                ) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                }
            }
        }

        // -----------------------------------------------------------------
        // INCOMING SHARED IMAGES CHOICE DIALOG
        // -----------------------------------------------------------------
        if (shareData.uris.isNotEmpty() && shareData.targetType == ShareTargetType.DEFAULT) {
            AlertDialog(
                modifier = Modifier.blur(if (isPreviewOpen) 24.dp else 0.dp),
                onDismissRequest = onClearShareData,
                title = { Text(Strings.externalShareTitle(isVi)) },
                text = { Text(Strings.externalShareMsg(isVi, shareData.uris.size)) },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        TextButton(onClick = {
                            shareData.uris.forEach { uri ->
                                if (!sourceImages.any { it.uri == uri }) {
                                    sourceImages.add(ImageItem(uri))
                                }
                            }
                            onClearShareData()
                        }) {
                            Text(Strings.sourceImagesTitle(isVi))
                        }
                        Button(onClick = {
                            shareData.uris.forEach { uri ->
                                if (!targetImages.any { it.uri == uri }) {
                                    targetImages.add(ImageItem(uri))
                                }
                            }
                            onClearShareData()
                        }) {
                            Text(Strings.targetImagesTitle(isVi))
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = onClearShareData) {
                        Text(Strings.skip(isVi))
                    }
                }
            )
        }

        // -----------------------------------------------------------------
        // COMPLETION OPEN IMAGE DIALOG
        // -----------------------------------------------------------------
        if (showOpenImageDialog && singleResultUri != null) {
            AlertDialog(
                modifier = Modifier.blur(if (isPreviewOpen) 24.dp else 0.dp),
                onDismissRequest = { showOpenImageDialog = false },
                title = { Text(Strings.completionDialogTitle(isVi), fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = Strings.completionDialogMsg(isVi),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(230.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    onOpenPreview(singleResultUri!!, true) {
                                        deleteProcessedImage(context, singleResultUri!!)
                                        resultImages.removeAll { it.uri == singleResultUri }
                                    }
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                // Ambient blurred backdrop to eliminate flat gray bars on sides
                                AsyncImage(
                                    model = singleResultUri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .blur(24.dp)
                                        .alpha(0.35f),
                                    contentScale = ContentScale.Crop
                                )
                                // Crisp Foreground Image
                                AsyncImage(
                                    model = singleResultUri,
                                    contentDescription = "Output Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                                // Zoom Pill Badge
                                Surface(
                                    color = Color.Black.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(Strings.tapToZoom(isVi), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = {
                                try {
                                    var targetUri: Uri? = singleResultUri
                                    val path = if (singleResultUri?.scheme == "file") {
                                        singleResultUri?.path
                                    } else null

                                    // Try to resolve to MediaStore content URI for full Edit/Delete permissions in Gallery
                                    if (path != null) {
                                        try {
                                            val file = java.io.File(path)
                                            val projection = arrayOf(MediaStore.Images.Media._ID)
                                            val selection = "${MediaStore.Images.Media.DATA} = ?"
                                            val selectionArgs = arrayOf(file.absolutePath)
                                            context.contentResolver.query(
                                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                                projection,
                                                selection,
                                                selectionArgs,
                                                null
                                            )?.use { cursor ->
                                                if (cursor.moveToFirst()) {
                                                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                                                    targetUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                                                }
                                            }
                                        } catch (_: Exception) {}
                                    }

                                    val finalUri = if (targetUri?.scheme == "file" && targetUri?.path != null) {
                                        val file = java.io.File(targetUri?.path!!)
                                        androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                    } else {
                                        targetUri
                                    }

                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(finalUri, "image/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }

                                    try {
                                        // Open directly with default photo viewer/gallery
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        // Fallback to chooser
                                        context.startActivity(Intent.createChooser(intent, if (isVi) "Xem ảnh" else "View image"))
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Lỗi mở xem ảnh: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                                showOpenImageDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isVi) "Mở ảnh" else "Open image")
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        TextButton(
                            onClick = { showOpenImageDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(Strings.close(isVi))
                        }
                    }
                },
                dismissButton = null
            )
        }

    }
}

@Composable
fun EmptyContainerState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Draw custom dotted borders
        val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
            )
            drawRoundRect(
                color = color,
                style = stroke
            )
        }
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageItemCard(
    item: ImageItem,
    index: Int,
    isSource: Boolean,
    isSelected: Boolean,
    onToggleSelection: (Boolean) -> Unit,
    selectedIds: Set<String>,
    onRemove: () -> Unit,
    onGlobalPositionUpdate: (Offset) -> Unit,
    controller: DragDropController,
    sourceBounds: Rect,
    targetBounds: Rect,
    onDragEnded: (Boolean?) -> Unit,
    onPreview: (Uri) -> Unit = {}
) {
    var dragStartOffset by remember { mutableStateOf(Offset.Zero) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var itemWindowBounds by remember { mutableStateOf(Rect.Zero) }

    val isThisItemDragged = controller.draggedItem == item
    val alphaValue by animateFloatAsState(if (isThisItemDragged) 0.3f else 1.0f)

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .fillMaxHeight()
            .clickable { onPreview(item.uri) }
            .onGloballyPositioned { itemWindowBounds = it.boundsInWindow() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main image preview
            AsyncImage(
                model = item.uri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPreview(item.uri) },
                contentScale = ContentScale.Crop,
                alpha = alphaValue
            )

            // Selection / Index tick badge (top start) - generous 34dp touch area
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(36.dp)
                    .clickable { onToggleSelection(!isSelected) },
                contentAlignment = Alignment.TopStart
            ) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary 
                            else Color.Black.copy(alpha = 0.5f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                    } else {
                        Text(
                            text = (index + 1).toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Zoom preview button (bottom right) - generous 36dp touch area
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(36.dp)
                    .clickable { onPreview(item.uri) },
                contentAlignment = Alignment.BottomEnd
            ) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Zoom Preview",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Remove button 'x' (top right) - generous 36dp touch area
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(36.dp)
                    .clickable { onRemove() },
                contentAlignment = Alignment.TopEnd
            ) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RecentImageItemCard(
    uri: Uri,
    isAlreadyAdded: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onPreview: (Uri) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .size(62.dp)
            .clickable { onAdd() }
            .then(
                if (isAlreadyAdded) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                } else Modifier
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = uri,
                contentDescription = "Recent Image",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            // Status badge (Small check icon if added, small plus if not)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(3.dp)
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(
                        if (isAlreadyAdded) MaterialTheme.colorScheme.primary 
                        else Color.Black.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAlreadyAdded) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = if (isAlreadyAdded) "Đã thêm" else "Thêm",
                    tint = Color.White,
                    modifier = Modifier.size(9.dp)
                )
            }

            // Close / Remove 'x' button (generous 30dp tap area)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(30.dp)
                    .clickable { onRemove() },
                contentAlignment = Alignment.TopEnd
            ) {
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Xóa khỏi gần đây",
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ImagePreviewDialog(
    uri: Uri,
    isProcessed: Boolean = false,
    onDismiss: () -> Unit,
    onAction: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scaleAnim = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }
    val fadeAlpha = remember { Animatable(0f) }
    val deleteScale = remember { Animatable(1f) }
    val deleteOffsetY = remember { Animatable(0f) }
    val deleteAlpha = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()
    var isClosing by remember { mutableStateOf(false) }

    // Pre-calculate exact aspect ratio synchronously from image header to eliminate initial layout flash
    val initialAspect = remember(uri) {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            if (options.outWidth > 0 && options.outHeight > 0) {
                options.outWidth.toFloat() / options.outHeight.toFloat()
            } else null
        } catch (_: Exception) {
            null
        }
    }
    var imageAspectRatio by remember(uri) { mutableStateOf(initialAspect) }

    // Pure snappy fade-in on enter
    LaunchedEffect(Unit) {
        fadeAlpha.animateTo(1f, tween(durationMillis = 120, easing = LinearOutSlowInEasing))
    }

    val handleDismiss = {
        if (!isClosing) {
            isClosing = true
            coroutineScope.launch {
                fadeAlpha.animateTo(0f, tween(durationMillis = 100, easing = FastOutLinearInEasing))
                onDismiss()
            }
        }
    }

    val prefs = remember { context.getSharedPreferences("exif_copy_prefs", Context.MODE_PRIVATE) }
    val appLanguage = remember { prefs.getString("app_language", AppLanguage.SYSTEM.code) ?: AppLanguage.SYSTEM.code }
    val isVi = remember(appLanguage) { Strings.isVietnamese(appLanguage) }

    val handleAction = {
        if (!isClosing) {
            isClosing = true
            coroutineScope.launch {
                // Distinct snappy action animation: photo shrinks, sinks down and fades away into trash
                launch { deleteScale.animateTo(0.65f, tween(durationMillis = 140, easing = FastOutSlowInEasing)) }
                launch { deleteOffsetY.animateTo(70f, tween(durationMillis = 140, easing = FastOutSlowInEasing)) }
                launch { deleteAlpha.animateTo(0f, tween(durationMillis = 120, easing = FastOutLinearInEasing)) }
                launch { fadeAlpha.animateTo(0f, tween(durationMillis = 140, easing = FastOutLinearInEasing)) }
                kotlinx.coroutines.delay(140)
                onAction?.invoke()
                val msg = if (isProcessed) {
                    if (isVi) "Đã xóa tệp ảnh khỏi thiết bị!" else "Deleted image file!"
                } else {
                    if (isVi) "Đã bỏ ảnh khỏi danh sách!" else "Removed image from list!"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        // Disable Android OS default slide/pop animation completely
        window?.setWindowAnimations(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window?.attributes = window?.attributes?.apply {
                blurBehindRadius = 24
            }
        }
        onDispose {}
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = { handleDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        BackHandler {
            handleDismiss()
        }

        var lastTapTime by remember { mutableStateOf(0L) }
        var photoRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f * fadeAlpha.value))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downPos = down.position
                        val downTime = System.currentTimeMillis()
                        var hasTransform = false
                        var totalDragY = 0f
                        var totalDragX = 0f
                        val rect = photoRect
                        val isOnPhoto = rect != null && rect.contains(downPos)

                        do {
                            val event = awaitPointerEvent()
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()

                            if (zoom != 1f || pan.getDistance() > 2f) {
                                hasTransform = true
                                val currentScale = scaleAnim.value
                                val newScale = (currentScale * zoom).coerceIn(0.7f, 8f)

                                if (newScale <= 1.05f && isOnPhoto && event.changes.size == 1) {
                                    // At 1x on photo with 1 finger: swipe-to-dismiss drag
                                    val dy = pan.y
                                    val dx = pan.x
                                    totalDragY += dy
                                    totalDragX += dx
                                    coroutineScope.launch {
                                        offsetYAnim.snapTo(offsetYAnim.value + dy)
                                    }
                                } else {
                                    // Zoomed or pinch: normal pan/zoom
                                    val dampening = if (newScale < 1f) 0.35f else 1f
                                    val panFactor = dampening / currentScale.coerceAtLeast(1f)
                                    val newOffsetX = offsetXAnim.value + pan.x * panFactor * currentScale
                                    val newOffsetY = offsetYAnim.value + pan.y * panFactor * currentScale
                                    coroutineScope.launch {
                                        scaleAnim.snapTo(newScale)
                                        offsetXAnim.snapTo(newOffsetX)
                                        offsetYAnim.snapTo(newOffsetY)
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })

                        val elapsed = System.currentTimeMillis() - downTime
                        val swipeThreshold = 90f   // px before commit dismiss
                        val absDragY = kotlin.math.abs(totalDragY)
                        val swipeVelocity = if (elapsed > 0) absDragY / elapsed * 1000f else 0f

                        if (!hasTransform && elapsed < 320) {
                            // Pure tap
                            val now = System.currentTimeMillis()
                            if (isOnPhoto && now - lastTapTime < 320) {
                                // Double tap on photo -> toggle zoom
                                lastTapTime = 0L
                                coroutineScope.launch {
                                    if (scaleAnim.value > 1.05f) {
                                        launch { scaleAnim.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 380f)) }
                                        launch { offsetXAnim.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 380f)) }
                                        launch { offsetYAnim.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 380f)) }
                                    } else {
                                        launch { scaleAnim.animateTo(2.5f, spring(dampingRatio = 0.82f, stiffness = 380f)) }
                                    }
                                }
                            } else if (isOnPhoto) {
                                lastTapTime = now
                            } else {
                                // Tap on background -> dismiss instantly
                                handleDismiss()
                            }
                        } else if (isOnPhoto && scaleAnim.value <= 1.05f && absDragY > 0) {
                            // Was a swipe drag on photo at 1x
                            if (absDragY > swipeThreshold || swipeVelocity > 400f) {
                                // Commit dismiss: fly out in swipe direction
                                val flyDir = if (totalDragY > 0) 1f else -1f
                                coroutineScope.launch {
                                    launch { offsetYAnim.animateTo(flyDir * size.height.toFloat(), tween(160, easing = FastOutLinearInEasing)) }
                                    launch { fadeAlpha.animateTo(0f, tween(140, easing = FastOutLinearInEasing)) }
                                    kotlinx.coroutines.delay(160)
                                    onDismiss()
                                }
                            } else {
                                // Snap back to center
                                coroutineScope.launch {
                                    launch { offsetYAnim.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 400f)) }
                                    launch { offsetXAnim.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 400f)) }
                                }
                            }
                        } else if (scaleAnim.value <= 1.05f && hasTransform) {
                            coroutineScope.launch {
                                launch { scaleAnim.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 340f)) }
                                launch { offsetXAnim.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 340f)) }
                                launch { offsetYAnim.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 340f)) }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Main Image with rounded frame that moves and scales strictly with the photo
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 76.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .then(
                            if (imageAspectRatio != null) {
                                Modifier.aspectRatio(imageAspectRatio!!)
                            } else {
                                Modifier.fillMaxSize()
                            }
                        )
                        .graphicsLayer(
                            scaleX = scaleAnim.value * deleteScale.value,
                            scaleY = scaleAnim.value * deleteScale.value,
                            alpha = fadeAlpha.value * deleteAlpha.value,
                            translationX = offsetXAnim.value,
                            translationY = offsetYAnim.value + deleteOffsetY.value,
                            clip = false
                        )
                        .shadow(elevation = 16.dp, shape = RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .onGloballyPositioned { coords ->
                            photoRect = coords.boundsInRoot()
                        }
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Full Preview",
                        onState = { state ->
                            if (state is coil.compose.AsyncImagePainter.State.Success) {
                                val size = state.painter.intrinsicSize
                                if (size.width > 0 && size.height > 0) {
                                    imageAspectRatio = size.width / size.height
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Top action bar (Pinned outside the scaling layer)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .alpha(fadeAlpha.value),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Close button
                FilledIconButton(
                    onClick = { handleDismiss() },
                    modifier = Modifier.size(42.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.65f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = if (isVi) "Đóng" else "Close",
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Action button (Delete for processed, Remove for imported)
                if (onAction != null) {
                    FilledIconButton(
                        onClick = { handleAction() },
                        modifier = Modifier.size(42.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(
                            imageVector = if (isProcessed) Icons.Default.Delete else Icons.Default.Remove,
                            contentDescription = if (isProcessed) {
                                if (isVi) "Xóa tệp ảnh" else "Delete image file"
                            } else {
                                if (isVi) "Bỏ ảnh khỏi danh sách" else "Remove image from list"
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveWatermarkDropdown(
    selectedMode: GeminiWatermarkRemover.WatermarkMode,
    visibleModes: List<GeminiWatermarkRemover.WatermarkMode>,
    onModeSelected: (GeminiWatermarkRemover.WatermarkMode) -> Unit,
    isVi: Boolean,
    autoPrecompute: Boolean,
    previewCache: Map<GeminiWatermarkRemover.WatermarkMode, Bitmap>,
    isComputing: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var activeHoverMode by remember { mutableStateOf<GeminiWatermarkRemover.WatermarkMode?>(null) }
    var fingerYOffsetPx by remember { mutableStateOf<Float?>(null) }
    var singleItemHeightPx by remember { mutableStateOf(0) }
    var itemsHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    val currentHover = activeHoverMode ?: selectedMode
    val activeIndex = remember(currentHover, visibleModes) {
        val idx = visibleModes.indexOf(currentHover)
        if (idx >= 0) idx else 0
    }

    val previewSizeDp = 116.dp
    val previewSizePx = with(density) { previewSizeDp.toPx() }

    // Target Y: continuously follows finger in real-time when dragging, or rests near active option
    val targetYPx = remember(fingerYOffsetPx, activeIndex, singleItemHeightPx, previewSizePx, itemsHeightPx) {
        val rawY = if (fingerYOffsetPx != null) {
            fingerYOffsetPx!! - (previewSizePx / 2f)
        } else {
            val itemH = if (singleItemHeightPx > 0) singleItemHeightPx.toFloat() else with(density) { 48.dp.toPx() }
            (itemH * activeIndex) + (itemH / 2f) - (previewSizePx / 2f)
        }
        val maxBound = (itemsHeightPx - previewSizePx).coerceAtLeast(0f)
        rawY.coerceIn(0f, if (maxBound > 0f) maxBound else Float.MAX_VALUE)
    }

    // Ultra-responsive high-stiffness spring animation tracking finger movement
    val animatedYPx by animateFloatAsState(
        targetValue = targetYPx,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 850f),
        label = "FingerFollowPreviewAnimation"
    )

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = {
                expanded = true
                activeHoverMode = selectedMode
                fingerYOffsetPx = null
            },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.heightIn(min = 34.dp)
        ) {
            Text(
                text = selectedMode.getDisplayName(isVi),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                activeHoverMode = null
                fingerYOffsetPx = null
            },
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            border = null,
            modifier = Modifier.widthIn(
                min = if (autoPrecompute) 340.dp else 220.dp,
                max = if (autoPrecompute) 410.dp else 290.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.Top
            ) {
                // CỘT BÊN TRÁI: Ảnh preview lơ lửng, nền ngoài trong suốt, bo góc nhẹ 8dp, bóng nhẹ 5dp, di chuyển theo ngón tay
                if (autoPrecompute) {
                    Box(
                        modifier = Modifier
                            .width(previewSizeDp + 8.dp)
                            .padding(end = 8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            shadowElevation = 5.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .size(previewSizeDp)
                                .offset(y = with(density) { animatedYPx.toDp() })
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(androidx.compose.ui.graphics.Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Crossfade(
                                    targetState = currentHover,
                                    animationSpec = tween(140),
                                    label = "FloatingPreviewCrossfade"
                                ) { mode ->
                                    val bmp = previewCache[mode]
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Live Preview",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else if (isComputing) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(4.dp)
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = Strings.previewComputing(isVi),
                                                fontSize = 9.sp,
                                                color = androidx.compose.ui.graphics.Color.LightGray
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = Strings.previewNotReady(isVi),
                                            fontSize = 9.sp,
                                            color = androidx.compose.ui.graphics.Color.Gray,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // CỘT BÊN PHẢI: Danh sách các Option items có nền riêng biệt
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .onSizeChanged { itemsHeightPx = it.height }
                            .pointerInput(visibleModes) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    fingerYOffsetPx = down.position.y
                                    val totalItems = visibleModes.size
                                    if (totalItems > 0 && itemsHeightPx > 0) {
                                        val itemH = itemsHeightPx.toFloat() / totalItems
                                        val idx = (down.position.y / itemH).toInt().coerceIn(0, totalItems - 1)
                                        activeHoverMode = visibleModes[idx]
                                    }

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break
                                        if (!change.pressed) {
                                            fingerYOffsetPx = null
                                            break
                                        }
                                        fingerYOffsetPx = change.position.y
                                        val totalItems = visibleModes.size
                                        if (totalItems > 0 && itemsHeightPx > 0) {
                                            val itemH = itemsHeightPx.toFloat() / totalItems
                                            val idx = (change.position.y / itemH).toInt().coerceIn(0, totalItems - 1)
                                            activeHoverMode = visibleModes[idx]
                                        }
                                    }
                                }
                            }
                    ) {
                        visibleModes.forEachIndexed { index, mode ->
                            val isSelected = selectedMode == mode
                            val isHovered = currentHover == mode

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = mode.getDisplayName(isVi),
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected || isHovered) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                },
                                onClick = {
                                    onModeSelected(mode)
                                    expanded = false
                                    activeHoverMode = null
                                    fingerYOffsetPx = null
                                },
                                modifier = Modifier
                                    .onSizeChanged { if (index == 0) singleItemHeightPx = it.height }
                                    .background(
                                        if (isHovered) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                        else androidx.compose.ui.graphics.Color.Transparent
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTabScreen(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    watermarkMode: GeminiWatermarkRemover.WatermarkMode,
    onWatermarkModeChange: (GeminiWatermarkRemover.WatermarkMode) -> Unit,
    enabledWatermarkModes: Set<String>,
    onToggleWatermarkMode: (GeminiWatermarkRemover.WatermarkMode) -> Unit,
    autoPrecomputeWatermark: Boolean,
    onToggleAutoPrecompute: (Boolean) -> Unit,
    watermarkKeepFileName: Boolean,
    onToggleWatermarkKeepFileName: (Boolean) -> Unit,
    watermarkKeepDateTime: Boolean,
    onToggleWatermarkKeepDateTime: (Boolean) -> Unit,
    reverseAlphaAutoDetect: Boolean,
    onToggleReverseAlphaAutoDetect: (Boolean) -> Unit,
    showWatermarkAdjustment: Boolean,
    onToggleShowWatermarkAdjustment: (Boolean) -> Unit,
    onOpenExifSettings: () -> Unit,
    isVi: Boolean
) {
    val context = LocalContext.current
    var previewTimelineItem by remember { mutableStateOf<ReverseAlphaTimelineItem?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = Strings.settingsTabTitle(isVi),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // Section: Language Selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Strings.languageSectionTitle(isVi),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = Strings.languageSectionDesc(isVi),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                AppLanguage.values().forEach { lang ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onLanguageChange(lang.code) }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    ) {
                        RadioButton(
                            selected = currentLanguage == lang.code,
                            onClick = { onLanguageChange(lang.code) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = lang.getDisplayName(isVi),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (currentLanguage == lang.code) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Section: Reverse Alpha Timeline Versions & Black Preview
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Strings.reverseAlphaVersionsTitle(isVi),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = Strings.reverseAlphaVersionsDesc(isVi),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                WatermarkTimelineHelper.TIMELINE_ITEMS.forEach { item ->
                    val isEnabled = enabledWatermarkModes.contains(item.watermarkMode.name)
                    val previewBitmap = remember(item.blackAssetPath) {
                        WatermarkTimelineHelper.getCroppedWatermarkPreview(context, item.blackAssetPath)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Top Header: Date Range
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = item.getDateRange(isVi),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Black Background Preview Box
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(androidx.compose.ui.graphics.Color.Black)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .clickable { previewTimelineItem = item },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (previewBitmap != null) {
                                        Image(
                                            bitmap = previewBitmap.asImageBitmap(),
                                            contentDescription = "Watermark Preview",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Image,
                                            contentDescription = null,
                                            tint = androidx.compose.ui.graphics.Color.Gray
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.getDescription(isVi),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedButton(
                                        onClick = { previewTimelineItem = item },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(Strings.previewWatermark(isVi), fontSize = 11.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.height(4.dp))

                            // Bottom: Show in Dropdown Checkbox
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onToggleWatermarkMode(item.watermarkMode) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = Strings.showInDropdownCheckbox(isVi),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isEnabled) FontWeight.Medium else FontWeight.Normal
                                )
                                Checkbox(
                                    checked = isEnabled,
                                    onCheckedChange = { onToggleWatermarkMode(item.watermarkMode) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Dropdown Visibility Checkboxes
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Checklist, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Strings.watermarkDropdownVisibilityTitle(isVi),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = Strings.watermarkDropdownVisibilityDesc(isVi),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                GeminiWatermarkRemover.WatermarkMode.values().forEach { mode ->
                    val isChecked = enabledWatermarkModes.contains(mode.name)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleWatermarkMode(mode) }
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { onToggleWatermarkMode(mode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = mode.getDisplayName(isVi),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Section: Auto Precompute Watermark Previews Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Strings.autoPrecomputeWatermarkTitle(isVi),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Switch(
                        checked = autoPrecomputeWatermark,
                        onCheckedChange = onToggleAutoPrecompute
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = Strings.autoPrecomputeWatermarkDesc(isVi),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Section: Watermark Output File & DateTime Options
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Strings.watermarkOutputOptionsTitle(isVi),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Toggle 1: Keep Original Filename
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = Strings.keepOriginalFileNameTitle(isVi),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = Strings.keepOriginalFileNameDesc(isVi),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = watermarkKeepFileName,
                        onCheckedChange = onToggleWatermarkKeepFileName
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(12.dp))

                // Toggle 2: Keep Original Date & Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = Strings.keepOriginalDateTimeTitle(isVi),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = Strings.keepOriginalDateTimeDesc(isVi),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = watermarkKeepDateTime,
                        onCheckedChange = onToggleWatermarkKeepDateTime
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(12.dp))

                // Toggle 3: Auto-detect Position for Reverse Alpha
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = Strings.reverseAlphaAutoDetectTitle(isVi),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = Strings.reverseAlphaAutoDetectDesc(isVi),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = reverseAlphaAutoDetect,
                        onCheckedChange = onToggleReverseAlphaAutoDetect
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(12.dp))

                // Toggle 4: Show Watermark Region Adjustment Card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = Strings.showWatermarkAdjustmentTitle(isVi),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = Strings.showWatermarkAdjustmentDesc(isVi),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showWatermarkAdjustment,
                        onCheckedChange = onToggleShowWatermarkAdjustment
                    )
                }
            }
        }

        // Section: Default Watermark Removal Mode
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Strings.watermarkModeSectionTitle(isVi),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = Strings.watermarkModeSectionDesc(isVi),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                GeminiWatermarkRemover.WatermarkMode.values().forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onWatermarkModeChange(mode) }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    ) {
                        RadioButton(
                            selected = watermarkMode == mode,
                            onClick = { onWatermarkModeChange(mode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = mode.getDisplayName(isVi),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (watermarkMode == mode) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Section: EXIF Settings shortcut
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Strings.exifConfigSectionTitle(isVi),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = Strings.exifConfigSectionDesc(isVi),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOpenExifSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Strings.openExifConfigBtn(isVi))
                }
            }
        }

        // Section: Activity Logs (Nhật ký hoạt động)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val context = LocalContext.current
                var logText by remember { mutableStateOf("") }
                val scope = rememberCoroutineScope()

                // Load logs on mount or language switch
                LaunchedEffect(isVi) {
                    withContext(Dispatchers.IO) {
                        val content = ExifMetadataHelper.getLogContents(context)
                        logText = if (content.isBlank()) Strings.logsEmpty(isVi) else content
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Strings.logsTitle(isVi),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Refresh Button
                        FilledIconButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    logText = if (isVi) "Đang làm mới..." else "Refreshing..."
                                    val content = ExifMetadataHelper.getLogContents(context)
                                    logText = if (content.isBlank()) Strings.logsEmpty(isVi) else content
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                        }
                        
                        // Copy Button
                        FilledIconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("ExifCopy Log", logText)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, Strings.copiedToClipboard(isVi), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                        }

                        // Clear Button
                        FilledIconButton(
                            onClick = {
                                ExifMetadataHelper.clearLog(context)
                                logText = if (isVi) "Nhật ký đã được xóa." else "Logs cleared."
                                Toast.makeText(context, Strings.logsCleared(isVi), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Monospace text box for logs
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        
                        LaunchedEffect(logText) {
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }

                        Text(
                            text = logText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isVi) "Lưu ý: Nhật ký cũng được lưu tự động tại Pictures/EXIFCopy/exif_log.txt để bạn có thể xem lại bất kỳ lúc nào." else "Note: Logs are also automatically saved to Pictures/EXIFCopy/exif_log.txt for review.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }

        // Section: App Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "EXIF Copy",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = Strings.appVersion(isVi),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Preview Watermark Modal Dialog
    previewTimelineItem?.let { item ->
        val fullBitmap = remember(item.blackAssetPath) {
            WatermarkTimelineHelper.loadAssetBitmap(context, item.blackAssetPath)
        }
        val croppedPreview = remember(item.blackAssetPath) {
            WatermarkTimelineHelper.getCroppedWatermarkPreview(context, item.blackAssetPath)
        }
        AlertDialog(
            onDismissRequest = { previewTimelineItem = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Strings.previewWatermarkDialogTitle(isVi), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = item.getDateRange(isVi),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = item.getDescription(isVi),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Zoomed Cropped Watermark Area
                    Text(
                        text = if (isVi) "Vùng Watermark (Phóng to góc dưới phải):" else "Watermark Region (Bottom-right zoom):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(androidx.compose.ui.graphics.Color.Black)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (croppedPreview != null) {
                            Image(
                                bitmap = croppedPreview.asImageBitmap(),
                                contentDescription = "Watermark Zoom",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(if (isVi) "Không tải được ảnh" else "Image load failed", color = androidx.compose.ui.graphics.Color.Gray, fontSize = 11.sp)
                        }
                    }

                    // Full Black Canvas Sample (Square 1:1)
                    Text(
                        text = if (isVi) "Toàn bộ ảnh mẫu nền đen (1024x1024 - Hình vuông):" else "Full Black Canvas Sample (1024x1024 - Square):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(androidx.compose.ui.graphics.Color.Black)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (fullBitmap != null) {
                            Image(
                                bitmap = fullBitmap.asImageBitmap(),
                                contentDescription = "Full Sample",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { previewTimelineItem = null }) {
                    Text(Strings.close(isVi))
                }
            }
        )
    }
}

@Composable
fun ResultImagesSection(
    resultImages: List<ImageItem>,
    onClearResults: () -> Unit,
    onRemoveResult: (ImageItem) -> Unit,
    onPreview: (Uri) -> Unit,
    isVi: Boolean,
    modifier: Modifier = Modifier
) {
    if (resultImages.isEmpty()) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                shape = CardDefaults.shape
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = Strings.resultImagesTitle(isVi, resultImages.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onClearResults,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 32.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Strings.clearResults(isVi), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(resultImages, key = { _, item -> item.id }) { index, item ->
                    ResultImageItemCard(
                        uri = item.uri,
                        index = index,
                        onRemove = { onRemoveResult(item) },
                        onPreview = onPreview
                    )
                }
            }
        }
    }
}

@Composable
fun ResultImageItemCard(
    uri: Uri,
    index: Int,
    onRemove: () -> Unit,
    onPreview: (Uri) -> Unit = {}
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .fillMaxHeight()
            .clickable { onPreview(uri) },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = uri,
                contentDescription = "Result Image",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            // Index badge (top start)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (index + 1).toString(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Share / Action button (bottom start) - generous touch area
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(34.dp)
                    .clickable {
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/*"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, null))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot share: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                contentAlignment = Alignment.BottomStart
            ) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Zoom preview button (bottom right) - generous touch area
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(34.dp)
                    .clickable { onPreview(uri) },
                contentAlignment = Alignment.BottomEnd
            ) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Zoom Preview",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Remove button 'x' (top right) - generous touch area
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(34.dp)
                    .clickable { onRemove() },
                contentAlignment = Alignment.TopEnd
            ) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------
// VISUAL UPSCALE & BLEND LOADING DIALOG COMPONENT
// -----------------------------------------------------------------
@Composable
fun VisualBlendLoadingDialog(
    progress: AiImageBlender.BlendProgress?,
    isVi: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = Strings.visualLoadingTitle(isVi),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Visual Red Mask Preview Area
                val maskBitmap = progress?.maskPreviewBitmap
                if (maskBitmap != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFFF4444).copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                )
                                Text(
                                    text = Strings.maskPreviewHeader(isVi),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Image(
                                bitmap = maskBitmap.asImageBitmap(),
                                contentDescription = "Red Mask Preview",
                                modifier = Modifier
                                    .heightIn(max = 160.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Fit
                            )

                            if (progress.diffPixelCount > 0 && progress.totalPixelCount > 0) {
                                val pct = (progress.diffPixelCount.toFloat() / progress.totalPixelCount.toFloat()) * 100f
                                Text(
                                    text = Strings.diffPixelsDetected(isVi, progress.diffPixelCount, pct),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFF8888),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                text = progress?.getMessage(isVi) ?: "Đang xử lý...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Progress Bar & Percentage
                val currentPct = progress?.percent ?: 0f
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = progress?.getMessage(isVi) ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(currentPct * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { currentPct },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 5-Stage Checklist
                val curStep = progress?.step ?: 1
                val stepsVi = listOf(
                    "1. Đọc & Căn chỉnh kích thước ảnh",
                    "2. Phân tích sai biệt & Tạo Mask đỏ",
                    "3. Làm mềm đường biên chuyển giao",
                    "4. Nội suy Upscale Lanczos-3 độ nét cao",
                    "5. Hòa trộn Pixel & Ghi thông tin EXIF"
                )
                val stepsEn = listOf(
                    "1. Read & Align image dimensions",
                    "2. Analyze differences & Create Red Mask",
                    "3. Smooth boundary transitions",
                    "4. Lanczos-3 High-Precision Upscaling",
                    "5. Alpha-blend Pixels & Inject EXIF"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (i in 1..5) {
                        val stepTitle = if (isVi) stepsVi[i - 1] else stepsEn[i - 1]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when {
                                curStep > i || (curStep == 5 && currentPct >= 1.0f) -> {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                curStep == i -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                else -> {
                                    Icon(
                                        Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = stepTitle,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (curStep == i) FontWeight.Bold else FontWeight.Normal,
                                color = if (curStep == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = if (curStep > i) 0.8f else 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WatermarkAdjustmentCard(
    offsetX: Int,
    onOffsetXChange: (Int) -> Unit,
    offsetY: Int,
    onOffsetYChange: (Int) -> Unit,
    boxSize: Int,
    onBoxSizeChange: (Int) -> Unit,
    autoDetect: Boolean,
    onAutoDetectChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    targetImageUri: Uri?,
    isVi: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Load bottom-right corner of target image for visual live preview
    var roiBitmap by remember(targetImageUri) { mutableStateOf<Bitmap?>(null) }
    var roiSpan by remember(targetImageUri) { mutableStateOf(380) }

    LaunchedEffect(targetImageUri) {
        if (targetImageUri != null) {
            withContext(Dispatchers.IO) {
                try {
                    val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                    val bmp = context.contentResolver.openInputStream(targetImageUri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                    if (bmp != null) {
                        val w = bmp.width
                        val h = bmp.height
                        val span = 380.coerceAtMost(kotlin.math.min(w, h))
                        val cropX = w - span
                        val cropY = h - span
                        val cropped = Bitmap.createBitmap(bmp, cropX, cropY, span, span)
                        bmp.recycle()
                        withContext(Dispatchers.Main) {
                            roiBitmap = cropped
                            roiSpan = span
                        }
                    }
                } catch (e: Exception) {
                    roiBitmap = null
                }
            }
        } else {
            roiBitmap = null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Strings.manualAdjustmentTitle(isVi),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = Strings.manualAdjustmentDesc(isVi),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Note on Reverse Alpha exclusion
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = Strings.manualAdjustmentNote(isVi),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }

                    // Auto-detect Checkbox Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                            .clickable { onAutoDetectChange(!autoDetect) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = Strings.manualAdjustmentAutoDetect(isVi),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (autoDetect) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (autoDetect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Checkbox(
                            checked = autoDetect,
                            onCheckedChange = onAutoDetectChange
                        )
                    }

                    // Sliders & Controls (Disabled if autoDetect is enabled)
                    val controlAlpha = if (autoDetect) 0.4f else 1.0f
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(controlAlpha),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Offset X Control
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = Strings.offsetXLabel(isVi, offsetX),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { if (!autoDetect) onOffsetXChange(offsetX - 4) },
                                    enabled = !autoDetect,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "-4px", modifier = Modifier.size(16.dp))
                                }
                                Slider(
                                    value = offsetX.toFloat(),
                                    onValueChange = { if (!autoDetect) onOffsetXChange(it.toInt()) },
                                    valueRange = -150f..150f,
                                    enabled = !autoDetect,
                                    modifier = Modifier.width(130.dp)
                                )
                                IconButton(
                                    onClick = { if (!autoDetect) onOffsetXChange(offsetX + 4) },
                                    enabled = !autoDetect,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "+4px", modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        // Offset Y Control
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = Strings.offsetYLabel(isVi, offsetY),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { if (!autoDetect) onOffsetYChange(offsetY - 4) },
                                    enabled = !autoDetect,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "-4px", modifier = Modifier.size(16.dp))
                                }
                                Slider(
                                    value = offsetY.toFloat(),
                                    onValueChange = { if (!autoDetect) onOffsetYChange(it.toInt()) },
                                    valueRange = -150f..150f,
                                    enabled = !autoDetect,
                                    modifier = Modifier.width(130.dp)
                                )
                                IconButton(
                                    onClick = { if (!autoDetect) onOffsetYChange(offsetY + 4) },
                                    enabled = !autoDetect,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "+4px", modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        // Box Size Chips
                        Text(
                            text = Strings.boxSizeLabel(isVi, boxSize),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(24, 36, 48, 64, 96, 128).forEach { size ->
                                FilterChip(
                                    selected = boxSize == size,
                                    onClick = { if (!autoDetect) onBoxSizeChange(size) },
                                    enabled = !autoDetect,
                                    label = { Text("${size}px", fontSize = 11.sp) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }

                        // Action buttons: Save & Reset to Auto Pos (Placed right below sliders for instant access)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onOffsetXChange(0)
                                    onOffsetYChange(0)
                                    onBoxSizeChange(48)
                                    onAutoDetectChange(false)
                                    onReset()
                                },
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(Strings.resetCustomPosBtn(isVi), fontSize = 11.sp)
                            }
                            Button(
                                onClick = onSave,
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(Strings.saveCustomPosBtn(isVi), fontSize = 11.sp)
                            }
                        }
                    }

                    // VISUAL LIVE ROI PREVIEW WITH RED MASK OVERLAY
                    if (roiBitmap != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = Strings.manualAdjustmentPreviewTitle(isVi),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        val displaySizeDp = 140.dp
                        val displaySizePx = with(LocalDensity.current) { displaySizeDp.toPx() }
                        val scale = displaySizePx / roiSpan.toFloat()

                        // Calculate box rect inside the cropped ROI
                        // Baseline watermark on ROI (48px size with 96px margin from corner)
                        val baseWatermarkMargin = 96
                        val baseWatermarkSize = 48
                        val baseLocalX = roiSpan - baseWatermarkMargin - baseWatermarkSize
                        val baseLocalY = roiSpan - baseWatermarkMargin - baseWatermarkSize

                        val targetLocalX = if (autoDetect) baseLocalX else (baseLocalX + offsetX)
                        val targetLocalY = if (autoDetect) baseLocalY else (baseLocalY + offsetY)
                        val targetSize = if (autoDetect) baseWatermarkSize else boxSize

                        val rectLeftPx = (targetLocalX * scale).coerceAtLeast(0f)
                        val rectTopPx = (targetLocalY * scale).coerceAtLeast(0f)
                        val rectWidthPx = (targetSize * scale).coerceAtMost(displaySizePx - rectLeftPx)
                        val rectHeightPx = (targetSize * scale).coerceAtMost(displaySizePx - rectTopPx)

                        Box(
                            modifier = Modifier
                                .size(displaySizeDp)
                                .align(Alignment.CenterHorizontally)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        ) {
                            Image(
                                bitmap = roiBitmap!!.asImageBitmap(),
                                contentDescription = "ROI Preview",
                                modifier = Modifier.fillMaxSize()
                            )

                            // Red mask translucent overlay box
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Draw red translucent fill
                                drawRect(
                                    color = Color(0xFFE53935).copy(alpha = 0.35f),
                                    topLeft = Offset(rectLeftPx, rectTopPx),
                                    size = androidx.compose.ui.geometry.Size(rectWidthPx, rectHeightPx)
                                )
                                // Draw red solid border
                                drawRect(
                                    color = Color(0xFFE53935),
                                    topLeft = Offset(rectLeftPx, rectTopPx),
                                    size = androidx.compose.ui.geometry.Size(rectWidthPx, rectHeightPx),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
