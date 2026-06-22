package com.sdai.news.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sdai.news.data.ScanResult
import com.sdai.news.ui.theme.Sdai
import com.sdai.news.viewmodel.ScanUiState
import com.sdai.news.viewmodel.ScanViewModel
import java.util.concurrent.Executors
import kotlin.math.round

@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit = {},
) {
    val vm: ScanViewModel = viewModel()
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val torchOn by vm.torchOn.collectAsStateWithLifecycle()
    val showTimeoutHint by vm.showTimeoutHint.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.onPermissionGranted() else vm.onPermissionDenied()
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) vm.onPermissionGranted() else permLauncher.launch(Manifest.permission.CAMERA)
    }

    var showManualSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    var imageCapturer by remember { mutableStateOf<ImageCapture?>(null) }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { captureExecutor.shutdown() } }
    val barcodeEnabled = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    // Barcode scanner active only while in Scanning state
    LaunchedEffect(uiState) {
        barcodeEnabled.set(uiState is ScanUiState.Scanning)
    }

    // Auto-open manual entry when label scan can't identify the product
    LaunchedEffect(uiState) {
        if (uiState is ScanUiState.PromptManualEntry) {
            showManualSheet = true
            vm.resumeScanning()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        if (uiState != ScanUiState.PermissionDenied) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                torchOn = torchOn,
                barcodeEnabled = barcodeEnabled,
                onCaptureReady = { imageCapturer = it },
                onBarcodeDetected = vm::onBarcodeDetected,
            )
            ScannerOverlay(modifier = Modifier.fillMaxSize())
        }

        when (uiState) {
            ScanUiState.PermissionDenied -> {
                PermissionDeniedView(
                    modifier = Modifier.fillMaxSize(),
                    onOpenSettings = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .apply { data = Uri.fromParts("package", ctx.packageName, null) }
                        )
                    },
                )
            }
            is ScanUiState.Resolving -> {
                ResolvingOverlay()
            }
            is ScanUiState.Found -> {
                ResultSheet(
                    result = (uiState as ScanUiState.Found).result,
                    sheetState = sheetState,
                    onScanAgain = { vm.resumeScanning() },
                    onDismiss = { vm.resumeScanning() },
                )
            }
            is ScanUiState.NotFound -> {
                NotFoundSheet(
                    query = (uiState as ScanUiState.NotFound).code,
                    sheetState = sheetState,
                    onScanAgain = { vm.resumeScanning() },
                    onDismiss = { vm.resumeScanning() },
                )
            }
            is ScanUiState.ScanError -> {
                ErrorSheet(
                    message = (uiState as ScanUiState.ScanError).message,
                    sheetState = sheetState,
                    onRetry = { vm.resumeScanning() },
                    onDismiss = { vm.resumeScanning() },
                )
            }
            else -> {}
        }

        if (uiState != ScanUiState.PermissionDenied) {
            ScanTopBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp),
                torchOn = torchOn,
                onBack = onBack,
                onToggleTorch = vm::toggleTorch,
                onOpenHistory = onOpenHistory,
            )

            if (uiState is ScanUiState.Scanning) {
                CaptureButton(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 40.dp),
                    onClick = {
                        val capturer = imageCapturer ?: return@CaptureButton
                        capturer.takePicture(
                            captureExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                @OptIn(ExperimentalGetImage::class)
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    try {
                                        val rotation = image.imageInfo.rotationDegrees
                                        val bmp = image.toBitmap()
                                        image.close()
                                        val scaled = scaleBitmapForOcr(bmp)
                                        if (scaled !== bmp) bmp.recycle()
                                        vm.onImageCaptured(scaled, rotation)
                                    } catch (e: Exception) {
                                        image.close()
                                        vm.onCaptureError("Image processing failed: ${e.message}")
                                    }
                                }
                                override fun onError(exc: ImageCaptureException) {
                                    vm.onCaptureError(exc.message ?: "Capture failed")
                                }
                            },
                        )
                    },
                )
                Text(
                    text = "Can't scan? Search by name",
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 14.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.30f))
                        .clickable { showManualSheet = true }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }

        }

        AnimatedVisibility(
            visible = showTimeoutHint,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 72.dp, start = 24.dp, end = 24.dp),
        ) {
            Text(
                text = "Frame the barcode clearly and hold steady. Make sure it's well-lit and in focus.",
                color = Color.White,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xCC1A1A2E))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }

    if (showManualSheet) {
        ManualSearchSheet(
            sheetState = sheetState,
            onDismiss = { showManualSheet = false },
            onSubmit = { name ->
                showManualSheet = false
                vm.onInputReceived(name)
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Camera preview — binds Preview + ImageCapture + ImageAnalysis
// ---------------------------------------------------------------------------

@Composable
private fun CameraPreview(
    modifier: Modifier,
    torchOn: Boolean,
    barcodeEnabled: java.util.concurrent.atomic.AtomicBoolean,
    onCaptureReady: (ImageCapture) -> Unit,
    onBarcodeDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestCaptureReady = rememberUpdatedState(onCaptureReady)
    val latestBarcodeCallback = rememberUpdatedState(onBarcodeDetected)
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    LaunchedEffect(torchOn) {
        cameraControl?.enableTorch(torchOn)
    }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)

        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageCapturer = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { ia ->
                    ia.setAnalyzer(executor, BarcodeAnalyzer(barcodeEnabled) { code ->
                        latestBarcodeCallback.value(code)
                    })
                }
            try {
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapturer,
                    analysis,
                )
                cameraControl = camera.cameraControl
                latestCaptureReady.value(imageCapturer)
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { future.get().unbindAll() }
            executor.shutdown()
            cameraControl = null
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

// ---------------------------------------------------------------------------
// Scale bitmap to max 1920px for ML Kit OCR
// ---------------------------------------------------------------------------

private fun scaleBitmapForOcr(bitmap: Bitmap): Bitmap {
    val maxSide = 1920
    val w = bitmap.width; val h = bitmap.height
    val scale = maxSide.toFloat() / maxOf(w, h)
    return if (scale < 1f)
        Bitmap.createScaledBitmap(bitmap, (w * scale).toInt(), (h * scale).toInt(), true)
    else bitmap
}

// ---------------------------------------------------------------------------
// Capture button — gradient circle with camera icon
// ---------------------------------------------------------------------------

@Composable
private fun CaptureButton(modifier: Modifier, onClick: () -> Unit) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF1D4ED8))))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.CameraAlt, "Capture & Analyze", tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text("Capture & Analyze", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------
// Barcode analyzer — continuous ML Kit scanner, only fires when enabled
// ---------------------------------------------------------------------------

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private class BarcodeAnalyzer(
    private val enabled: java.util.concurrent.atomic.AtomicBoolean,
    private val onDetected: (String) -> Unit,
) : androidx.camera.core.ImageAnalysis.Analyzer {

    private val options = com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
        .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ALL_FORMATS)
        .build()
    private val scanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient(options)

    override fun analyze(imageProxy: androidx.camera.core.ImageProxy) {
        if (!enabled.get()) { imageProxy.close(); return }
        val mediaImage = imageProxy.image
        if (mediaImage == null) { imageProxy.close(); return }
        val image = com.google.mlkit.vision.common.InputImage.fromMediaImage(
            mediaImage, imageProxy.imageInfo.rotationDegrees
        )
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                    ?.rawValue?.let { onDetected(it) }
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}

// ---------------------------------------------------------------------------
// Scanner overlay — dim mask + transparent capture window + corner brackets
// ---------------------------------------------------------------------------

@Composable
private fun ScannerOverlay(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "scan")
    val scanY by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scanY",
    )

    androidx.compose.foundation.Canvas(
        modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        drawRect(Color.Black.copy(alpha = 0.25f))

        val pad = 40f
        val rw = size.width - pad * 2
        val topPad = size.height * 0.13f
        val botPad = size.height * 0.28f
        val rh = size.height - topPad - botPad
        val rx = pad
        val ry = topPad
        val radius = CornerRadius(16f)

        // Clear the capture window — nearly full screen
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(rx, ry),
            size = Size(rw, rh),
            cornerRadius = radius,
            blendMode = BlendMode.Clear,
        )

        // Animated scan line
        val lineY = ry + rh * scanY
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color(0xBB059669), Color(0xFF059669), Color(0xBB059669), Color.Transparent),
                startX = rx + 12f,
                endX = rx + rw - 12f,
            ),
            start = Offset(rx + 12f, lineY),
            end = Offset(rx + rw - 12f, lineY),
            strokeWidth = 2.5f,
            blendMode = BlendMode.SrcOver,
        )

        // Corner brackets
        val cl = 48f
        val white = Color.White

        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(white, Offset(x, y + dy * cl), Offset(x, y), strokeWidth = 4f)
            drawLine(white, Offset(x, y), Offset(x + dx * cl, y), strokeWidth = 4f)
        }
        corner(rx, ry, 1f, 1f)
        corner(rx + rw, ry, -1f, 1f)
        corner(rx, ry + rh, 1f, -1f)
        corner(rx + rw, ry + rh, -1f, -1f)
    }
}

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

@Composable
private fun ScanTopBar(
    modifier: Modifier,
    torchOn: Boolean,
    onBack: () -> Unit,
    onToggleTorch: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBackIosNew, "Back", tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text(
            text = "Scan to Know",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onOpenHistory) {
            Icon(Icons.Outlined.History, "Scan History", tint = Color.White, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onToggleTorch) {
            Icon(
                imageVector = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = if (torchOn) "Flash on" else "Flash off",
                tint = if (torchOn) Color(0xFFFFD700) else Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Resolving overlay
// ---------------------------------------------------------------------------

@Composable
private fun ResolvingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xF0111118))
                .padding(28.dp),
        ) {
            CircularProgressIndicator(color = Color(0xFF059669), modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(16.dp))
            Text("Looking up product...", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("Searching Open Food Facts database", color = Color(0xFF8A8AA0), fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// Permission denied
// ---------------------------------------------------------------------------

@Composable
private fun PermissionDeniedView(modifier: Modifier, onOpenSettings: () -> Unit) {
    Column(
        modifier = modifier.background(Color(0xFF05050F)).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.CameraAlt,
            contentDescription = null,
            tint = Color(0xFF059669),
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Camera Access Required",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Awarely needs camera access to photograph product labels and ingredients. Your camera is used only while the scanner is open and images are not stored.",
            color = Color(0xFF8A8AA0),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onOpenSettings,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open App Settings", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Result sheet
// ---------------------------------------------------------------------------

@Composable
private fun ResultSheet(
    result: ScanResult,
    sheetState: SheetState,
    onScanAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0E0E1A),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 24.dp),
        ) {
            // Handle + Scan Another at the same level
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(40.dp, 4.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color(0xFF3A3A4A)),
                )
                TextButton(
                    onClick = onScanAgain,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(14.dp), tint = Color(0xFF059669))
                    Spacer(Modifier.width(4.dp))
                    Text("Scan Another", color = Color(0xFF059669), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    if (result.category.isNotBlank()) {
                        Text(
                            result.category.uppercase(),
                            color = Color(0xFF059669),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(result.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
                    if (result.brand.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(result.brand, color = Color(0xFF8A8AA0), fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.width(12.dp))
                SafetyBadge(label = result.safetyLabel)
            }

            Spacer(Modifier.height(16.dp))
            StarRating(rating = result.overallRating)
            if (result.safetyReason.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Based on ${result.safetyReason.lowercase()}",
                    color = Color(0xFF8A8AA0),
                    fontSize = 12.sp,
                )
            }

            if (result.ratingBreakdown.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = Color(0xFF22222E))
                Spacer(Modifier.height(16.dp))
                Text("Rating Breakdown", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                result.ratingBreakdown.forEach { (label, score) ->
                    RatingBreakdownRow(label = label, score = score)
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (result.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = Color(0xFF22222E))
                Spacer(Modifier.height(16.dp))
                Text("What does this mean?", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(result.description, color = Color(0xFFC2C2D0), fontSize = 13.sp, lineHeight = 21.sp)
            }

            if (result.keyFacts.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF22222E))
                Spacer(Modifier.height(16.dp))
                Text("Key Facts", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                result.keyFacts.forEach { fact ->
                    Row(modifier = Modifier.padding(vertical = 3.dp)) {
                        Text("•", color = Color(0xFF059669), fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp, top = 1.dp))
                        Text(fact, color = Color(0xFFC2C2D0), fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
            }

            if (result.relatedAlerts.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF22222E))
                Spacer(Modifier.height(16.dp))
                Text("Relevant News & Alerts", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                result.relatedAlerts.forEach { alert ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1A1A26))
                            .padding(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B))
                                .align(Alignment.Top)
                                .padding(top = 5.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(alert, color = Color(0xFFC2C2D0), fontSize = 12.sp, lineHeight = 19.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

        }
    }
}

// ---------------------------------------------------------------------------
// Not-found sheet
// ---------------------------------------------------------------------------

@Composable
private fun NotFoundSheet(
    query: String,
    sheetState: SheetState,
    onScanAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0E0E1A),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp, 4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3A3A4A)),
            )
            Spacer(Modifier.height(24.dp))
            Text("Product Not Found", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "\"$query\" was not found in Open Food Facts. Search Google to check its safety profile.",
                color = Color(0xFF8A8AA0),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1D4ED8), Color(0xFF059669))))
                    .clickable {
                        val q = java.net.URLEncoder.encode("$query Product Safety level check", "UTF-8")
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$q"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Search on Google", fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onScanAgain,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Scan Again", color = Color(0xFF8A8AA0), fontSize = 13.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Error sheet
// ---------------------------------------------------------------------------

@Composable
private fun ErrorSheet(
    message: String,
    sheetState: SheetState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0E0E1A),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp, 4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3A3A4A)),
            )
            Spacer(Modifier.height(24.dp))
            Text("Analysis Failed", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = Color(0xFF8A8AA0), fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Try Again", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Manual product name search sheet
// ---------------------------------------------------------------------------

@Composable
private fun ManualSearchSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0E0E1A),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp, 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .background(Color(0xFF3A3A4A)),
            )
            Spacer(Modifier.height(20.dp))
            Text("Search by Product Name", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Type the product name to search Open Food Facts.", color = Color(0xFF8A8AA0), fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("e.g. Lays Classic, Amul Butter, Maggi Noodles", color = Color(0xFF5A5A6E)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF059669),
                    unfocusedBorderColor = Color(0xFF22222E),
                    cursorColor = Color(0xFF059669),
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    if (text.isNotBlank()) onSubmit(text.trim())
                }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    keyboard?.hide()
                    if (text.isNotBlank()) onSubmit(text.trim())
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Search, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Look Up Product", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Safety badge, star rating, breakdown row
// ---------------------------------------------------------------------------

@Composable
private fun SafetyBadge(label: String) {
    val (bg, fg) = when (label) {
        "Safe" -> Color(0xFF22C55E).copy(alpha = 0.15f) to Color(0xFF22C55E)
        "Moderate" -> Color(0xFFF59E0B).copy(alpha = 0.15f) to Color(0xFFF59E0B)
        "Low" -> Color(0xFFEF4444).copy(alpha = 0.15f) to Color(0xFFEF4444)
        else -> Color(0xFF8A8AA0).copy(alpha = 0.15f) to Color(0xFF8A8AA0)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StarRating(rating: Float) {
    val full = rating.toInt()
    val half = (rating - full) >= 0.4f
    val empty = 5 - full - (if (half) 1 else 0)
    val displayRating = (round(rating * 10) / 10.0).toString()
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(full) { Icon(Icons.Filled.Star, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(20.dp)) }
        if (half) Icon(Icons.Filled.StarHalf, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(20.dp))
        repeat(empty) { Icon(Icons.Outlined.StarOutline, null, tint = Color(0xFF5A5A6E), modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(8.dp))
        Text(displayRating, color = Color(0xFFFBBF24), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(" / 5", color = Color(0xFF8A8AA0), fontSize = 13.sp)
    }
}

@Composable
private fun RatingBreakdownRow(label: String, score: Float) {
    val barColor = when {
        score >= 4.0f -> Color(0xFF22C55E)
        score >= 3.0f -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Color(0xFFC2C2D0), fontSize = 12.sp, modifier = Modifier.width(160.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF22222E)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(score / 5f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = (round(score * 10) / 10.0).toString(),
            color = barColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(28.dp),
        )
    }
}
