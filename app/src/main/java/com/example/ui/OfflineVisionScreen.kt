package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.EmeraldTelemetry
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudSurface
import com.example.ui.theme.HudSurfaceElevated
import com.example.vision.OfflineVisionAnalysis
import java.util.concurrent.Executors

@Composable
fun OfflineVisionScreen(
    viewModel: LeonViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysis by viewModel.offlineVisionAnalysis.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var useFrontCamera by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    var liveScanPaused by remember { mutableStateOf(false) }
    var autoSpeakEnabled by remember { mutableStateOf(false) }
    var cameraErrorText by remember { mutableStateOf<String?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var lastAutoSpokenKey by remember { mutableStateOf("") }
    var lastAutoSpokenAt by remember { mutableLongStateOf(0L) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        viewModel.refreshTelemetry()
        if (!granted) {
            cameraErrorText = "कैमरा परमिशन नहीं मिली — नीचे 'फ़ोटो से पहचानें' भी बिना इंटरनेट काम करता है।"
        } else {
            cameraErrorText = null
        }
    }

    val offlinePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val bitmap = decodeScaledBitmap(context, uri)
                liveScanPaused = true
                viewModel.analyzeBitmapWithOfflineEye(bitmap, speakResult = true)
            }.onFailure {
                viewModel.reportError(it.localizedMessage ?: "फ़ोटो लोड नहीं हो सकी।")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Auto-speak newly detected objects in offline mode when user enables Auto-Speak
    LaunchedEffect(analysis.analyzedAtMillis, autoSpeakEnabled) {
        if (!autoSpeakEnabled || liveScanPaused || !analysis.hasDetections) return@LaunchedEffect
        val key = analysis.labels.firstOrNull()?.rawLabel
            ?: analysis.barcodes.firstOrNull()?.kindHindi
            ?: analysis.recognizedText.take(24)
        val now = System.currentTimeMillis()
        if (key.isNotBlank() && (key != lastAutoSpokenKey || now - lastAutoSpokenAt > 5500L) &&
            now - lastAutoSpokenAt > 3500L
        ) {
            lastAutoSpokenKey = key
            lastAutoSpokenAt = now
            viewModel.speakOfflineVision(analysis)
        }
    }

    LaunchedEffect(torchEnabled, boundCamera) {
        runCatching {
            boundCamera?.cameraControl?.enableTorch(torchEnabled)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .weight(1f)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // Top HUD Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("vision_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to HUD",
                        tint = ArcCyan
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.RemoveRedEye,
                    contentDescription = null,
                    tint = EmeraldTelemetry
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LEON OFFLINE EYE",
                        style = MaterialTheme.typography.titleMedium,
                        color = ArcCyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("vision_screen_title")
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WifiOff,
                            contentDescription = null,
                            tint = EmeraldTelemetry,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "100% बिना इंटरनेट • कैमरा पहचान व समझ",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldTelemetry,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = { torchEnabled = !torchEnabled },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (torchEnabled) ArcCyan.copy(alpha = 0.2f) else HudSurface)
                        .testTag("vision_torch_button")
                ) {
                    Icon(
                        imageVector = if (torchEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                        contentDescription = "Toggle Camera Torch",
                        tint = if (torchEnabled) ArcCyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        torchEnabled = false
                        useFrontCamera = !useFrontCamera
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(HudSurface)
                        .testTag("vision_flip_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cameraswitch,
                        contentDescription = "Switch Front or Back Camera",
                        tint = ArcCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Camera Preview + JARVIS HUD Reticle & Live Detection Boxes
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.54f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(DeepObsidian)
            ) {
                if (hasCameraPermission && cameraErrorText == null) {
                    OfflineCameraPreviewBox(
                        viewModel = viewModel,
                        lifecycleOwner = lifecycleOwner,
                        useFrontCamera = useFrontCamera,
                        paused = liveScanPaused,
                        analysis = analysis,
                        onCameraBound = { boundCamera = it },
                        onCameraError = { msg -> cameraErrorText = msg }
                    )
                } else {
                    OfflineCameraFallbackCard(
                        hasPermission = hasCameraPermission,
                        errorMessage = cameraErrorText,
                        onRequestPermission = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onPickPhoto = {
                            offlinePhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }

                // Corner Status Pill + Live/Pause chip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = DeepObsidian.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, EmeraldTelemetry.copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = if (liveScanPaused) "PAUSED" else "LIVE OFFLINE AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldTelemetry,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = autoSpeakEnabled,
                            onClick = { autoSpeakEnabled = !autoSpeakEnabled },
                            label = {
                                Text(
                                    text = if (autoSpeakEnabled) "Auto-Speak ON" else "Auto-Speak",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldTelemetry,
                                selectedLabelColor = DeepObsidian,
                                containerColor = DeepObsidian.copy(alpha = 0.8f),
                                labelColor = ArcCyan
                            ),
                            modifier = Modifier.testTag("vision_autospeak_chip")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Offline Visual Understanding Card
            Card(
                colors = CardDefaults.cardColors(containerColor = HudSurface),
                border = BorderStroke(1.dp, HudBorder),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.46f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Object chips row
                    if (analysis.labels.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            analysis.labels.forEach { label ->
                                val pct = (label.confidence * 100).toInt()
                                Surface(
                                    color = ArcCyan.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, ArcCyan.copy(alpha = 0.55f))
                                ) {
                                    Text(
                                        text = "${label.hindiName} ($pct%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ArcCyan,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Understood Hindi Summary
                    Text(
                        text = analysis.summaryHindi,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("vision_summary_text")
                    )

                    if (analysis.contextualInsightHindi.isNotBlank()) {
                        Surface(
                            color = EmeraldTelemetry.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, EmeraldTelemetry.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = analysis.contextualInsightHindi,
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldTelemetry,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (analysis.barcodes.isNotEmpty()) {
                        analysis.barcodes.forEach { code ->
                            Surface(
                                color = HudSurfaceElevated,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ArcCyan.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.QrCodeScanner,
                                        contentDescription = null,
                                        tint = ArcCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = code.kindHindi,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ArcCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = code.explanationHindi,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (analysis.recognizedText.isNotBlank()) {
                        Surface(
                            color = HudSurfaceElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, HudBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "पढ़ा गया टेक्स्ट (Offline OCR):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ArcCyan,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = analysis.recognizedText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Primary Offline Vision Action Buttons (all >= 48dp touch target)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isSpeaking) viewModel.stopSpeaking()
                                else viewModel.speakOfflineVision(analysis)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSpeaking) MaterialTheme.colorScheme.error else ArcCyan,
                                contentColor = DeepObsidian
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("vision_speak_button")
                        ) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = if (isSpeaking) "रोकें" else "बोलकर बताओ",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        OutlinedButton(
                            onClick = { liveScanPaused = !liveScanPaused },
                            border = BorderStroke(1.dp, ArcCyan),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("vision_pause_button")
                        ) {
                            Icon(
                                imageVector = if (liveScanPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = if (liveScanPaused) "Resume live scan" else "Pause live scan",
                                tint = ArcCyan
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                offlinePhotoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            border = BorderStroke(1.dp, HudBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("vision_gallery_scan_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ImageSearch,
                                contentDescription = null,
                                tint = ArcCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("फ़ोटो स्कैन", style = MaterialTheme.typography.labelMedium, color = ArcCyan)
                        }

                        OutlinedButton(
                            onClick = { viewModel.saveVisionSightingToMemory(analysis) },
                            border = BorderStroke(1.dp, EmeraldTelemetry.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("vision_save_memory_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BookmarkAdd,
                                contentDescription = null,
                                tint = EmeraldTelemetry,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("याद रखो", style = MaterialTheme.typography.labelMedium, color = EmeraldTelemetry)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun OfflineCameraPreviewBox(
    viewModel: LeonViewModel,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    useFrontCamera: Boolean,
    paused: Boolean,
    analysis: OfflineVisionAnalysis,
    onCameraBound: (Camera?) -> Unit,
    onCameraError: (String) -> Unit
) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                val providerFuture = ProcessCameraProvider.getInstance(context)
                if (providerFuture.isDone) {
                    providerFuture.get().unbindAll()
                }
            }
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(useFrontCamera, paused) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                try {
                    val cameraProvider = providerFuture.get()
                    val selector = if (useFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    if (!paused) {
                        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            viewModel.offlineVisionAnalyzer.analyzeImageProxy(imageProxy) { result ->
                                viewModel.updateOfflineVisionAnalysis(result)
                            }
                        }
                    } else {
                        imageAnalysis.clearAnalyzer()
                    }

                    cameraProvider.unbindAll()
                    val cam = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imageAnalysis
                    )
                    onCameraBound(cam)
                } catch (e: Exception) {
                    onCameraBound(null)
                    onCameraError("कैमरा उपलब्ध नहीं हो सका (${e.localizedMessage ?: "हार्डवेयर चेक करें"}) — नीचे 'फ़ोटो स्कैन' बिना इंटरनेट काम करेगा।")
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // HUD Targeting Reticle & Bounding Boxes
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cornerLen = 36f
            val margin = 28f
            val strokeW = 4f

            // Four cyberpunk corner brackets
            drawLine(ArcCyan, Offset(margin, margin), Offset(margin + cornerLen, margin), strokeW)
            drawLine(ArcCyan, Offset(margin, margin), Offset(margin, margin + cornerLen), strokeW)

            drawLine(ArcCyan, Offset(w - margin, margin), Offset(w - margin - cornerLen, margin), strokeW)
            drawLine(ArcCyan, Offset(w - margin, margin), Offset(w - margin, margin + cornerLen), strokeW)

            drawLine(ArcCyan, Offset(margin, h - margin), Offset(margin + cornerLen, h - margin), strokeW)
            drawLine(ArcCyan, Offset(margin, h - margin), Offset(margin, h - margin - cornerLen), strokeW)

            drawLine(ArcCyan, Offset(w - margin, h - margin), Offset(w - margin - cornerLen, h - margin), strokeW)
            drawLine(ArcCyan, Offset(w - margin, h - margin), Offset(w - margin, h - margin - cornerLen), strokeW)

            val scaleX = w / analysis.frameWidth.toFloat().coerceAtLeast(1f)
            val scaleY = h / analysis.frameHeight.toFloat().coerceAtLeast(1f)
            analysis.boxes.forEach { box ->
                val left = (box.left * scaleX).coerceIn(0f, w)
                val top = (box.top * scaleY).coerceIn(0f, h)
                val right = (box.right * scaleX).coerceIn(left, w)
                val bottom = (box.bottom * scaleY).coerceIn(top, h)
                drawRect(
                    color = EmeraldTelemetry,
                    topLeft = Offset(left, top),
                    size = Size((right - left).coerceAtLeast(12f), (bottom - top).coerceAtLeast(12f)),
                    style = Stroke(width = 4f)
                )
            }
        }
    }
}

@Composable
private fun OfflineCameraFallbackCard(
    hasPermission: Boolean,
    errorMessage: String?,
    onRequestPermission: () -> Unit,
    onPickPhoto: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.RemoveRedEye,
            contentDescription = null,
            tint = ArcCyan,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (!hasPermission) {
                "कैमरा से चीज़ पहचानने के लिए कैमरा परमिशन दें"
            } else {
                errorMessage ?: "कैमरा प्रीव्यू तैयार हो रहा है..."
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!hasPermission) {
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = DeepObsidian),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("कैमरा चालू करें")
                }
            }
            OutlinedButton(
                onClick = onPickPhoto,
                border = BorderStroke(1.dp, EmeraldTelemetry),
                modifier = Modifier.height(48.dp)
            ) {
                Text("गैलरी फ़ोटो स्कैन (Offline)", color = EmeraldTelemetry)
            }
        }
    }
}

private fun decodeScaledBitmap(context: android.content.Context, uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val maxDim = maxOf(info.size.width, info.size.height)
            if (maxDim > 1280) {
                val scale = 1280f / maxDim.toFloat()
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1)
                )
            }
        }
    } else {
        @Suppress("DEPRECATION")
        val decoded = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        val maxDim = maxOf(decoded.width, decoded.height)
        if (maxDim > 1280) {
            val scale = 1280f / maxDim.toFloat()
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            decoded
        }
    }
}
