package com.phucdnh.exifcopy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import org.json.JSONArray
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalView
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
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
            var previewImageUri by remember { mutableStateOf<Uri?>(null) }
            var onDeleteCurrentPreview: (() -> Unit)? by remember { mutableStateOf(null) }

            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(if (previewImageUri != null) 24.dp else 0.dp),
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
                        previewImageUri = previewImageUri,
                        onPreviewImageChange = { previewImageUri = it },
                        onRegisterDeleteCallback = { callback -> onDeleteCurrentPreview = callback },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }

                previewImageUri?.let { uri ->
                    ImagePreviewDialog(
                        uri = uri,
                        onDismiss = { previewImageUri = null },
                        onDelete = {
                            onDeleteCurrentPreview?.invoke()
                            previewImageUri = null
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

@Composable
fun MainScreen(
    shareData: ShareIntentData,
    onClearShareData: () -> Unit,
    previewImageUri: Uri?,
    onPreviewImageChange: (Uri?) -> Unit,
    onRegisterDeleteCallback: ((() -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Main image lists (persisted across multi-target share intents and multitasking)
    val sourceImages = MainActivity.globalSourceImages
    val targetImages = MainActivity.globalTargetImages

    // Register delete callback when previewImageUri changes
    LaunchedEffect(previewImageUri) {
        if (previewImageUri != null) {
            onRegisterDeleteCallback?.invoke {
                sourceImages.removeAll { it.uri == previewImageUri }
                targetImages.removeAll { it.uri == previewImageUri }
            }
        }
    }

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
    var watermarkMode by remember {
        val savedMode = prefs.getString("saved_watermark_mode", GeminiWatermarkRemover.WatermarkMode.REVERSE_ALPHA.name)
        mutableStateOf(
            GeminiWatermarkRemover.WatermarkMode.values().find { it.name == savedMode }
                ?: GeminiWatermarkRemover.WatermarkMode.REVERSE_ALPHA
        )
    }
    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var singleResultUri by remember { mutableStateOf<Uri?>(null) }
    var showOpenImageDialog by remember { mutableStateOf(false) }

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
            .blur(if (previewImageUri != null) 24.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Tab Row Navigation (3 Tabs)
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text(Strings.tabCopyExif(isVi), fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.CopyAll, contentDescription = null) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text(Strings.tabRemoveAi(isVi), fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.DeleteForever, contentDescription = null) }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text(Strings.tabSettings(isVi), fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) }
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
                                                    onPreview = { onPreviewImageChange(it) }
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
                                            onPreview = { onPreviewImageChange(it) }
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
                                            onPreview = { onPreviewImageChange(it) }
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
                            onClearResults = { resultImages.clear() },
                            onRemoveResult = { resultImages.remove(it) },
                            onPreview = { onPreviewImageChange(it) },
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
                                        var expanded by remember { mutableStateOf(false) }

                                        Box(modifier = Modifier.weight(1f, fill = false)) {
                                            OutlinedButton(
                                                onClick = { expanded = true },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.heightIn(min = 34.dp)
                                            ) {
                                                Text(
                                                    text = watermarkMode.getDisplayName(isVi),
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                            DropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false }
                                            ) {
                                                GeminiWatermarkRemover.WatermarkMode.values().forEach { mode ->
                                                    DropdownMenuItem(
                                                        text = { Text(mode.getDisplayName(isVi), fontSize = 12.sp) },
                                                        onClick = {
                                                            watermarkMode = mode
                                                            prefs.edit().putString("saved_watermark_mode", mode.name).apply()
                                                            expanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
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
                                        processingMessage = Strings.processingCopyExif(isVi)
                                        
                                        // Clear old logs and start new log stream
                                        ExifMetadataHelper.clearLog(context)
                                        ExifMetadataHelper.log(context, "Bắt đầu tiến trình sao chép EXIF cho ${targetImages.size} ảnh (Xóa Watermark=$removeWatermark, Mode=${watermarkMode.displayName})")
                                        
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

                                                    val outputUris = ExifMetadataHelper.copyMetadataList(
                                                        context = context,
                                                        sourceUri = sourceItem.uri,
                                                        targetUri = targetItem.uri,
                                                        settings = activeSettings,
                                                        replaceOriginal = false,
                                                        removeWatermark = removeWatermark,
                                                        watermarkMode = watermarkMode
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
                                                            onPreview = { onPreviewImageChange(it) }
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
                                            onClearResults = { resultImages.clear() },
                                            onRemoveResult = { resultImages.remove(it) },
                                            onPreview = { onPreviewImageChange(it) },
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
                                        var expanded by remember { mutableStateOf(false) }

                                        Box(modifier = Modifier.weight(1f, fill = false)) {
                                            OutlinedButton(
                                                onClick = { expanded = true },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.heightIn(min = 34.dp)
                                            ) {
                                                Text(
                                                    text = watermarkMode.getDisplayName(isVi),
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                            DropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false }
                                            ) {
                                                GeminiWatermarkRemover.WatermarkMode.values().forEach { mode ->
                                                    DropdownMenuItem(
                                                        text = { Text(mode.getDisplayName(isVi), fontSize = 12.sp) },
                                                        onClick = {
                                                            watermarkMode = mode
                                                            prefs.edit().putString("saved_watermark_mode", mode.name).apply()
                                                            expanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
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
                                                    val outputUris = ExifMetadataHelper.cleanGoogleAiMetadataList(
                                                        context = context,
                                                        targetUri = targetItem.uri,
                                                        replaceOriginal = false,
                                                        removeWatermark = removeWatermark,
                                                        watermarkMode = watermarkMode
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
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(Strings.removeAiAction(isVi))
                                }
                            }
                        }
                    }
                    2 -> {
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
                                    onPreviewImageChange(singleResultUri)
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
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window?.attributes = window?.attributes?.apply {
                blurBehindRadius = 60
            }
        }
        onDispose {}
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (scale <= 1.05f) {
                                onDismiss()
                            } else {
                                scale = 1f
                                offset = Offset.Zero
                            }
                        },
                        onDoubleTap = {
                            if (scale > 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                                offset = Offset.Zero
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Full Screen Zoomable & Pannable Image (Scales entire image freely across screen)
            AsyncImage(
                model = uri,
                contentDescription = "Full Preview",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 8f)
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    },
                contentScale = ContentScale.Fit
            )

            // Top action bar (Pinned outside the scaling layer)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Close button
                FilledIconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(42.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng",
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Delete button
                if (onDelete != null) {
                    val context = LocalContext.current
                    FilledIconButton(
                        onClick = {
                            onDelete()
                            Toast.makeText(context, "Đã xóa ảnh!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(42.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Xóa ảnh",
                            modifier = Modifier.size(20.dp)
                        )
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
    onOpenExifSettings: () -> Unit,
    isVi: Boolean
) {
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
                    text = if (isVi) "Lưu ý: Nhật ký cũng được lưu tự động tại Pictures/ExifCopy/exif_log.txt để bạn có thể xem lại bất kỳ lúc nào." else "Note: Logs are also automatically saved to Pictures/ExifCopy/exif_log.txt for review.",
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