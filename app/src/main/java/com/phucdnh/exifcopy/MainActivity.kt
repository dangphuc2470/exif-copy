package com.phucdnh.exifcopy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
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

class MainActivity : ComponentActivity() {
    private val sharedUrisState = mutableStateOf<List<Uri>>(emptyList())

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle incoming share intents when app is launched
        sharedUrisState.value = handleSendIntent(intent)

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
                                    "ExifCopy Expressive",
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
                    val sharedUris by sharedUrisState

                    MainScreen(
                        sharedUris = sharedUris,
                        onClearSharedUris = { sharedUrisState.value = emptyList() },
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
                            val uriToDelete = previewImageUri
                            if (uriToDelete != null) {
                                sharedUrisState.value = sharedUrisState.value.filter { it != uriToDelete }
                            }
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
        sharedUrisState.value = handleSendIntent(intent)
    }

    private fun handleSendIntent(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
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
        } catch (e: Exception) {
            // Ignore
        }
        return uris
    }
}

@Composable
fun MainScreen(
    sharedUris: List<Uri>,
    onClearSharedUris: () -> Unit,
    previewImageUri: Uri?,
    onPreviewImageChange: (Uri?) -> Unit,
    onRegisterDeleteCallback: ((() -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Main image lists
    val sourceImages = remember { mutableStateListOf<ImageItem>() }
    val targetImages = remember { mutableStateListOf<ImageItem>() }

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

    // Navigation Tab state
    var activeTab by remember { mutableStateOf(0) } // Default to Tab 0: Sao chép Exif

    // Handle incoming shared URIs from external apps without clearing existing images
    LaunchedEffect(sharedUris) {
        if (sharedUris.isNotEmpty()) {
            sharedUris.forEach { uri ->
                if (!targetImages.any { it.uri == uri }) {
                    targetImages.add(ImageItem(uri))
                }
                if (!sourceImages.any { it.uri == uri }) {
                    sourceImages.add(ImageItem(uri))
                }
            }
            onClearSharedUris()
        }
    }

    // EXIF Settings and configurations
    var exifSettings by remember { mutableStateOf(ExifSettings()) }
    var removeWatermark by remember { mutableStateOf(true) }
    var watermarkMode by remember { mutableStateOf(GeminiWatermarkRemover.WatermarkMode.AI_MODEL) }
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
            // Tab Row Navigation
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Sao chép EXIF", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.CopyAll, contentDescription = null) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Xóa nhãn AI", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.DeleteForever, contentDescription = null) }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("Nhật ký", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.List, contentDescription = null) }
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (activeTab) {
                    0 -> {
                        // -----------------------------------------------------------------
                        // TAB 0: SAO CHÉP EXIF
                        // -----------------------------------------------------------------
                    
                    // CONTAINER A: SOURCE IMAGES (Nguồn)
                    val isOverSource = controller.draggedItem != null && 
                            sourceContainerBounds.contains(globalDragPosition)
                    val sourceBorderColor = if (isOverSource) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    val sourceBorderWidth = if (isOverSource) 3.dp else 1.dp

                    Card(
                        modifier = Modifier
                            .weight(1f)
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
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ảnh Nguồn (A)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { pickSourceLauncher.launch(arrayOf("image/*")) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Chọn ảnh")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (sourceImages.isEmpty()) {
                                EmptyContainerState(
                                    message = "Chưa chọn ảnh nguồn.\nẢnh dùng để lấy thông tin EXIF.",
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                LazyRow(
                                    modifier = Modifier.weight(1f),
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
                            Text("Hoán đổi")
                        }

                        Button(
                            onClick = { showSettingsDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cài đặt EXIF")
                        }
                    }

                    // CONTAINER B: TARGET IMAGES (Đích)
                    val isOverTarget = controller.draggedItem != null && 
                            targetContainerBounds.contains(globalDragPosition)
                    val targetBorderColor = if (isOverTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    val targetBorderWidth = if (isOverTarget) 3.dp else 1.dp

                    Card(
                        modifier = Modifier
                            .weight(1f)
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
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ảnh Đích (B)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { pickTargetLauncher.launch(arrayOf("image/*")) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Chọn ảnh")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (targetImages.isEmpty()) {
                                EmptyContainerState(
                                    message = "Chưa chọn ảnh đích.\nẢnh sẽ được gán EXIF mới.",
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                LazyRow(
                                    modifier = Modifier.weight(1f),
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = removeWatermark,
                                onCheckedChange = { removeWatermark = it }
                            )
                            Text("Xóa Watermark", style = MaterialTheme.typography.bodyMedium)

                            if (removeWatermark) {
                                Spacer(modifier = Modifier.width(6.dp))
                                var expanded by remember { mutableStateOf(false) }

                                Box {
                                    OutlinedButton(
                                        onClick = { expanded = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(watermarkMode.displayName, fontSize = 11.sp)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        GeminiWatermarkRemover.WatermarkMode.values().forEach { mode ->
                                            DropdownMenuItem(
                                                text = { Text(mode.displayName, fontSize = 12.sp) },
                                                onClick = {
                                                    watermarkMode = mode
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ACTION BUTTON (Full Width)
                    Button(
                        onClick = {
                            if (sourceImages.isEmpty()) {
                                Toast.makeText(context, "Chọn ít nhất 1 ảnh nguồn!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (targetImages.isEmpty()) {
                                Toast.makeText(context, "Chọn ít nhất 1 ảnh đích!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            // Start execution in background thread
                            isProcessing = true
                            processingMessage = "Đang sao chép EXIF..."
                            
                            // Clear old logs and start new log stream
                            ExifMetadataHelper.clearLog(context)
                            ExifMetadataHelper.log(context, "Bắt đầu tiến trình sao chép EXIF cho ${targetImages.size} ảnh (Xóa Watermark=$removeWatermark, Mode=${watermarkMode.displayName})")
                            
                            scope.launch {
                                var successCount = 0
                                var lastOutputUri: Uri? = null
                                val targetCopy = targetImages.toList()
                                val sourceCopy = sourceImages.toList()

                                withContext(Dispatchers.IO) {
                                    for (i in targetCopy.indices) {
                                        val targetItem = targetCopy[i]
                                        val sourceItem = if (sourceCopy.size == 1) {
                                            sourceCopy[0]
                                        } else {
                                            sourceCopy[i % sourceCopy.size]
                                        }

                                        val activeSettings = exifSettings

                                        val outputUri = ExifMetadataHelper.copyMetadata(
                                            context = context,
                                            sourceUri = sourceItem.uri,
                                            targetUri = targetItem.uri,
                                            settings = activeSettings,
                                            itemIndex = i,
                                            replaceOriginal = false,
                                            removeWatermark = removeWatermark,
                                            watermarkMode = watermarkMode
                                        )
                                        if (outputUri != null) {
                                            successCount++
                                            lastOutputUri = outputUri
                                        }
                                    }
                                }

                                isProcessing = false
                                val resultMsg = "Đã lưu $successCount/${targetCopy.size} ảnh mới vào thư mục Pictures/ExifCopy!"
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
                        Text("Sao chép EXIF")
                    }
                    }
                    1 -> {
                        // -----------------------------------------------------------------
                        // TAB 1: XÓA NHÃN AI
                        // -----------------------------------------------------------------
                    
                    // CONTAINER B: TARGET IMAGES (Chỉ chọn ảnh cần xóa nhãn)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
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
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ảnh Cần Xóa Nhãn AI",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { pickTargetLauncher.launch(arrayOf("image/*")) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Chọn ảnh")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (targetImages.isEmpty()) {
                                EmptyContainerState(
                                    message = "Chưa chọn ảnh đích.\nChọn ảnh để thực hiện xóa nhãn Google AI.",
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                LazyRow(
                                    modifier = Modifier.weight(1f),
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

                    Spacer(modifier = Modifier.weight(1f))

                    // SETTINGS & CONTROL BAR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = removeWatermark,
                                onCheckedChange = { removeWatermark = it }
                            )
                            Text("Xóa Watermark", style = MaterialTheme.typography.bodyMedium)

                            if (removeWatermark) {
                                Spacer(modifier = Modifier.width(6.dp))
                                var expanded by remember { mutableStateOf(false) }

                                Box {
                                    OutlinedButton(
                                        onClick = { expanded = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(watermarkMode.displayName, fontSize = 11.sp)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        GeminiWatermarkRemover.WatermarkMode.values().forEach { mode ->
                                            DropdownMenuItem(
                                                text = { Text(mode.displayName, fontSize = 12.sp) },
                                                onClick = {
                                                    watermarkMode = mode
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ACTION BUTTON (Full Width)
                    Button(
                        onClick = {
                            if (targetImages.isEmpty()) {
                                Toast.makeText(context, "Chọn ảnh ở phần Ảnh Đích để xóa nhãn AI!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            isProcessing = true
                            processingMessage = if (removeWatermark) "Đang xóa nhãn AI & Watermark..." else "Đang xóa nhãn Google AI..."
                            
                            ExifMetadataHelper.clearLog(context)
                            ExifMetadataHelper.log(context, "Bắt đầu tiến trình xóa nhãn Google AI cho ${targetImages.size} ảnh (Xóa Watermark=$removeWatermark, Mode=${watermarkMode.displayName})")
                            
                            scope.launch {
                                var successCount = 0
                                var lastOutputUri: Uri? = null
                                val targetCopy = targetImages.toList()

                                withContext(Dispatchers.IO) {
                                    for (targetItem in targetCopy) {
                                        val outputUri = ExifMetadataHelper.cleanGoogleAiMetadata(
                                            context = context,
                                            targetUri = targetItem.uri,
                                            replaceOriginal = false,
                                            removeWatermark = removeWatermark,
                                            watermarkMode = watermarkMode
                                        )
                                        if (outputUri != null) {
                                            successCount++
                                            lastOutputUri = outputUri
                                        }
                                    }
                                }

                                isProcessing = false
                                val resultMsg = "Đã lưu $successCount/${targetCopy.size} ảnh làm sạch vào thư mục Pictures/ExifCopy!"
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
                        Text("Xóa nhãn AI")
                    }
                    }
                    2 -> {
                        LogTabScreen()
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
                    Toast.makeText(context, "Đã cập nhật cấu hình Random EXIF!", Toast.LENGTH_SHORT).show()
                }
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
        if (sharedUris.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = onClearSharedUris,
                title = { Text("Nhập ảnh từ bên ngoài") },
                text = { Text("Bạn muốn thêm ${sharedUris.size} ảnh đã chia sẻ vào danh sách nào?") },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        TextButton(onClick = {
                            sharedUris.forEach { uri ->
                                if (!sourceImages.any { it.uri == uri }) {
                                    sourceImages.add(ImageItem(uri))
                                }
                            }
                            onClearSharedUris()
                        }) {
                            Text("Ảnh Nguồn (A)")
                        }
                        Button(onClick = {
                            sharedUris.forEach { uri ->
                                if (!targetImages.any { it.uri == uri }) {
                                    targetImages.add(ImageItem(uri))
                                }
                            }
                            onClearSharedUris()
                        }) {
                            Text("Ảnh Đích (B)")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = onClearSharedUris) {
                        Text("Bỏ qua")
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
                title = { Text("Xử lý hoàn tất!", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Đã lưu ảnh mới vào thư mục Pictures/ExifCopy!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onPreviewImageChange(singleResultUri)
                                },
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = singleResultUri,
                                    contentDescription = "Output Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                                Surface(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(topStart = 8.dp),
                                    modifier = Modifier.align(Alignment.BottomEnd)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Chạm để phóng to", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        try {
                            val viewUri = if (singleResultUri?.scheme == "file" && singleResultUri?.path != null) {
                                val file = java.io.File(singleResultUri?.path!!)
                                androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                            } else {
                                singleResultUri
                            }

                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(viewUri, "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(Intent.createChooser(intent, "Xem ảnh"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Lỗi mở xem ảnh: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                        showOpenImageDialog = false
                    }) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mở bằng app ngoài")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showOpenImageDialog = false }) {
                        Text("Đóng")
                    }
                }
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

            // Selection / Index tick badge
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary 
                        else Color.Black.copy(alpha = 0.4f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                        shape = CircleShape
                    )
                    .align(Alignment.TopStart)
                    .clickable {
                        onToggleSelection(!isSelected)
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
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

            // Zoom preview button (bottom right)
            IconButton(
                onClick = { onPreview(item.uri) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = "Zoom Preview",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Remove button 'x'
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(22.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
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
                .background(Color.Black.copy(alpha = 0.25f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .wrapContentHeight(),
                contentAlignment = Alignment.TopEnd
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .shadow(32.dp, shape = RoundedCornerShape(32.dp))
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                if (scale > 1f) {
                                    offset += pan
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        },
                    shape = RoundedCornerShape(32.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Full Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .clip(RoundedCornerShape(32.dp)),
                        contentScale = ContentScale.FillWidth
                    )
                }

                if (onDelete != null) {
                    val context = LocalContext.current
                    FilledIconButton(
                        onClick = {
                            onDelete()
                            Toast.makeText(context, "Đã xóa ảnh!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .padding(12.dp)
                            .size(40.dp)
                            .shadow(8.dp, CircleShape),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
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
fun LogTabScreen() {
    val context = LocalContext.current
    var logText by remember { mutableStateOf("Đang tải nhật ký...") }
    val scope = rememberCoroutineScope()

    // Load logs on mount
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            logText = ExifMetadataHelper.getLogContents(context)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Nhật ký hoạt động (Log)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Refresh Button
                FilledIconButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            logText = "Đang làm mới..."
                            logText = ExifMetadataHelper.getLogContents(context)
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                }
                
                // Copy Button
                FilledIconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("ExifCopy Log", logText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Đã sao chép vào bộ nhớ tạm!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                }

                // Clear Button
                FilledIconButton(
                    onClick = {
                        ExifMetadataHelper.clearLog(context)
                        logText = "Nhật ký đã được xóa."
                        Toast.makeText(context, "Đã xóa nhật ký!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                }
            }
        }

        // Monospace text view for logs
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                val scrollState = rememberScrollState()
                
                // Set auto-scroll to bottom when logText changes
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
        
        Text(
            text = "Lưu ý: Log cũng được lưu tự động tại Pictures/ExifCopy/exif_log.txt để bạn có thể xem lại bất kỳ lúc nào.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
}