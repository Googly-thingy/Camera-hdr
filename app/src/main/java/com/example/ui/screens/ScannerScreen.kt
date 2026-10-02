package com.example.ui.screens

import android.graphics.PointF
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldAccent
import com.example.ui.viewmodel.GestureViewModel
import com.example.vision.DetectionResult
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(
    viewModel: GestureViewModel,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val isServicePaused by viewModel.isServicePaused.collectAsState()
    val currentDetection by viewModel.currentDetection.collectAsState()
    val latestActionBanner by viewModel.latestActionBanner.collectAsState()
    val mappings by viewModel.allMappings.collectAsState()

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Pulse animation for scanner
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Camera bind effect when background service is not running
    DisposableEffect(hasCameraPermission, isServiceRunning) {
        if (hasCameraPermission && !isServiceRunning) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                } catch (_: Exception) {
                }
            }, ContextCompat.getMainExecutor(context))
        } else {
            cameraProvider?.unbindAll()
            cameraProvider = null
        }

        onDispose {
            cameraProvider?.unbindAll()
            cameraProvider = null
            cameraExecutor.shutdown()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Hand Gesture Scanner",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Ultra-low latency front camera tracking",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Real-time Latency Pill
            val latency = currentDetection?.latencyMs ?: 8L
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (latency < 20) EmeraldAccent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (latency < 20) EmeraldAccent else MaterialTheme.colorScheme.outline
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Low Latency",
                        tint = if (latency < 20) EmeraldAccent else CyanPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${latency}ms",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (latency < 20) EmeraldAccent else CyanPrimary
                    )
                }
            }
        }

        // Camera Feed Viewport / Scanner View
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .testTag("camera_preview_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (currentDetection?.handDetected == true) CyanPrimary else MaterialTheme.colorScheme.outline
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!hasCameraPermission) {
                    // Permission Prompt View
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraFront,
                            contentDescription = "Camera Permission",
                            tint = CyanPrimary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Camera Permission Required",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "GestureFlow uses the front camera to identify hand movements in real-time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRequestCameraPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            modifier = Modifier.testTag("grant_camera_permission_button")
                        ) {
                            Text("Grant Camera Access", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (!isServiceRunning) {
                    // Local Foreground CameraX Preview
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            }

                            val providerFuture = ProcessCameraProvider.getInstance(ctx)
                            providerFuture.addListener({
                                try {
                                    val provider = providerFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }

                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                                        .build()

                                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                        viewModel.processInAppFrame(imageProxy, ctx)
                                    }

                                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                                    provider.unbindAll()
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageAnalysis
                                    )
                                } catch (_: Exception) {
                                }
                            }, ContextCompat.getMainExecutor(ctx))

                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Background Service Active Mode Visualizer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFF0F2027), Color(0xFF070B14))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .border(2.dp, CyanPrimary.copy(alpha = pulseAlpha), CircleShape)
                                    .padding(12.dp)
                                    .border(1.dp, EmeraldAccent.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sensors,
                                    contentDescription = "Background Scanner Active",
                                    tint = if (isServicePaused) Color.Gray else EmeraldAccent,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isServicePaused) "Background Engine Paused" else "Background Camera Stream Active",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isServicePaused) Color.LightGray else EmeraldAccent
                            )
                            Text(
                                text = "Running in persistent foreground service",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }

                // Dynamic Computer Vision Hand Landmark & Skeleton Overlay
                currentDetection?.let { det ->
                    GestureOverlayCanvas(
                        detection = det,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // HUD Top Badges (FPS & Status)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (currentDetection?.handDetected == true) EmeraldAccent else Color.Red)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (currentDetection?.handDetected == true) "HAND TRACKED" else "SCANNING...",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = "${(currentDetection?.fps ?: 30f).toInt()} FPS",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary
                        )
                    }
                }

                // HUD Bottom Recognition Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(12.dp)
                ) {
                    val gesture = currentDetection?.gesture
                    if (gesture != null) {
                        val mapping = mappings.find { it.gestureId == gesture.name }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(CyanPrimary.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = gesture.getIcon(),
                                        contentDescription = gesture.title,
                                        tint = CyanPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = gesture.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                    Text(
                                        text = mapping?.action?.title ?: "No action mapped",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = EmeraldAccent
                                    )
                                }
                            }

                            // Confidence badge
                            Text(
                                text = "${((currentDetection?.confidence ?: 0.9f) * 100).toInt()}% conf",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color.LightGray
                            )
                        }
                    } else {
                        Text(
                            text = "Show hand gesture to front camera to trigger shortcut",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }

        // Action Trigger Notification Toast Banner
        AnimatedVisibility(
            visible = !latestActionBanner.isNullOrBlank(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = EmeraldAccent.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldAccent)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Action Triggered",
                        tint = EmeraldAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = latestActionBanner ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // Background Service Master Controller Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isServiceRunning) EmeraldAccent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isServiceRunning) EmeraldAccent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "Service Status",
                                tint = if (isServiceRunning) EmeraldAccent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Background Engine",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isServiceRunning) {
                                    if (isServicePaused) "Service paused" else "Active: Scans when app is closed"
                                } else {
                                    "Disabled - Works in app only"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isServiceRunning && !isServicePaused) EmeraldAccent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isServiceRunning,
                        onCheckedChange = { enable ->
                            if (enable) {
                                viewModel.startService(context)
                            } else {
                                viewModel.stopService(context)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EmeraldAccent,
                            checkedTrackColor = EmeraldAccent.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.testTag("background_service_switch")
                    )
                }

                if (isServiceRunning) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.togglePauseService(context) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isServicePaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isServicePaused) "Resume" else "Pause",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isServicePaused) "Resume" else "Pause")
                        }

                        Button(
                            onClick = { viewModel.stopService(context) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Stop Service")
                        }
                    }
                }
            }
        }

        // Quick Gesture Test Deck
        Text(
            text = "Quick Gesture Preview & Test",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            mappings.take(4).forEach { mapping ->
                Surface(
                    onClick = { viewModel.simulateTrigger(context, mapping) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = mapping.gesture.getIcon(),
                            contentDescription = mapping.gesture.title,
                            tint = CyanPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = mapping.gesture.title.take(8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Test",
                            fontSize = 9.sp,
                            color = EmeraldAccent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureOverlayCanvas(
    detection: DetectionResult,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (!detection.handDetected) return@Canvas

        val w = size.width
        val h = size.height

        // Front camera image is mirrored horizontally on display
        fun normX(x: Float): Float = (1f - x) * w
        fun normY(y: Float): Float = y * h

        val centerPx = Offset(normX(detection.palmCenter.x), normY(detection.palmCenter.y))
        val radiusPx = (detection.palmRadius * w).coerceAtLeast(18f)

        // Draw Bounding Box with dashed cyber style
        val box = detection.boundingBox
        if (box.width() > 0.05f) {
            val left = normX(box.right)
            val top = normY(box.top)
            val boxW = box.width() * w
            val boxH = box.height() * h

            drawRoundRect(
                color = CyanPrimary.copy(alpha = 0.45f),
                topLeft = Offset(left, top),
                size = Size(boxW, boxH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
                style = Stroke(
                    width = 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
                )
            )
        }

        // Draw Palm Center Circle
        drawCircle(
            color = CyanPrimary.copy(alpha = 0.25f),
            radius = radiusPx,
            center = centerPx
        )
        drawCircle(
            color = CyanPrimary,
            radius = radiusPx,
            center = centerPx,
            style = Stroke(width = 2f)
        )
        drawCircle(
            color = Color.White,
            radius = 5f,
            center = centerPx
        )

        // Draw Skeleton Lines from Palm to Fingertips
        detection.fingertips.forEach { tip ->
            val tipPx = Offset(normX(tip.x), normY(tip.y))

            // Skeleton bone ray
            drawLine(
                color = CyanPrimary.copy(alpha = 0.8f),
                start = centerPx,
                end = tipPx,
                strokeWidth = 3f
            )

            // Fingertip node
            drawCircle(
                color = EmeraldAccent.copy(alpha = 0.4f),
                radius = 12f,
                center = tipPx
            )
            drawCircle(
                color = EmeraldAccent,
                radius = 6f,
                center = tipPx
            )
            drawCircle(
                color = Color.White,
                radius = 2.5f,
                center = tipPx
            )
        }
    }
}
