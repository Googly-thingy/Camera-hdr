package com.example.ui.screens

import android.graphics.PointF
import android.graphics.RectF
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.action.ActionExecutor
import com.example.data.model.GestureType
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldAccent
import com.example.ui.viewmodel.GestureViewModel
import com.example.vision.DetectionResult
import java.util.concurrent.Executors

@Composable
fun TrainingScreen(
    viewModel: GestureViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentDetection by viewModel.currentDetection.collectAsState()
    val trainedGestureIds by viewModel.trainedGestureIds.collectAsState()
    val allTrainingSamples by viewModel.allTrainingSamples.collectAsState()
    val requireTrainedOnly by viewModel.requireTrainedOnly.collectAsState()
    val matchThreshold by viewModel.matchThreshold.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()

    var selectedGestureToTrain by remember { mutableStateOf(GestureType.OPEN_PALM) }
    var currentSampleStep by remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showCapturedFlash by remember { mutableStateOf(false) }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(isServiceRunning) {
        if (!isServiceRunning) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                } catch (_: Exception) {
                }
            }, ContextCompat.getMainExecutor(context))
        }

        onDispose {
            cameraProvider?.unbindAll()
            cameraProvider = null
            cameraExecutor.shutdown()
        }
    }

    val samplesForSelected = allTrainingSamples.filter { it.gestureId == selectedGestureToTrain.name }
    val isSelectedTrained = samplesForSelected.isNotEmpty()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            // Screen Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Gesture Training Studio",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Train and inspect live node tracking to eliminate face false triggers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = EmeraldAccent.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Accuracy",
                            tint = EmeraldAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${trainedGestureIds.size}/${GestureType.entries.size} Calibrated",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldAccent
                        )
                    }
                }
            }
        }

        // Gesture Selector Carousel
        item {
            Text(
                text = "1. Choose Gesture to Train:",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GestureType.entries) { gesture ->
                    val isTrained = trainedGestureIds.contains(gesture.name)
                    val isSelected = selectedGestureToTrain == gesture
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedGestureToTrain = gesture
                            currentSampleStep = 0
                            statusMessage = null
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isTrained) Icons.Default.CheckCircle else gesture.getIcon(),
                                contentDescription = gesture.title,
                                tint = if (isSelected) CyanPrimary else if (isTrained) EmeraldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = {
                            Text(
                                text = gesture.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanPrimary.copy(alpha = 0.18f),
                            selectedLabelColor = CyanPrimary
                        )
                    )
                }
            }
        }

        // 2. LIVE CAMERA SECTION WITH VISIBLE HAND NODES
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "2. Live Camera & Node Visualizer:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Pose hand inside camera",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = CyanPrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp)
                        .testTag("training_camera_viewport"),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (currentDetection?.handDetected == true) CyanPrimary else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Live Camera Viewport
                        if (!isServiceRunning) {
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
                            // If background service runs, visualizer mode
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF070C18)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Background Engine Active • Analyzing Front Camera",
                                    color = EmeraldAccent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // HIGH-PRECISION NODE & SKELETON CANVAS OVERLAY
                        currentDetection?.let { det ->
                            TrainingNodesCanvas(
                                detection = det,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Top Badges on Camera: Node Count & Face Status
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Hand Node Status Pill
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Black.copy(alpha = 0.75f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (currentDetection?.handDetected == true) EmeraldAccent else Color.Gray
                                )
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
                                        text = if (currentDetection?.handDetected == true) {
                                            "HAND: ${currentDetection?.rawFingerCount ?: 0} TIPS FOUND"
                                        } else "NO HAND IN VIEW",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            // ML Kit Face Exclusion Indicator Pill
                            if (currentDetection?.faceDetected == true) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Red.copy(alpha = 0.25f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Face,
                                            contentDescription = "Face",
                                            tint = Color.Red,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "FACE MASKED OUT",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // Bottom Recognition & Node Info Bar
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
                            val det = currentDetection
                            if (det?.handDetected == true) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = if (det.gesture != null) "Detected: ${det.gesture.title}" else "Analyzing hand geometry...",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (det.isTrainedMatch) EmeraldAccent else Color.White
                                        )
                                        Text(
                                            text = "Nodes: Palm center + ${det.fingertips.size} fingertips mapped",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 10.sp,
                                            color = CyanPrimary
                                        )
                                    }

                                    if (det.matchScore > 0f) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (det.isTrainedMatch) EmeraldAccent.copy(alpha = 0.25f) else Color.DarkGray
                                        ) {
                                            Text(
                                                text = "${(det.matchScore * 100).toInt()}% Match",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (det.isTrainedMatch) EmeraldAccent else Color.White,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "Hold your hand up to see real-time skeleton nodes",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = Color.LightGray,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. CAPTURE SAMPLES ACTION BENCH
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    val handReady = currentDetection?.handDetected == true
                    val currentSig = currentDetection?.signature
                    val targetSamples = 3
                    val progress = (currentSampleStep.toFloat() / targetSamples).coerceIn(0f, 1f)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Training: ${selectedGestureToTrain.title}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Capture $targetSamples snapshots of your hand pose",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "$currentSampleStep / $targetSamples Samples",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (currentSampleStep >= targetSamples) EmeraldAccent else CyanPrimary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (currentSig != null) {
                                    val nextStep = currentSampleStep + 1
                                    viewModel.saveTrainingSample(
                                        gesture = selectedGestureToTrain,
                                        sampleIndex = nextStep,
                                        signature = currentSig
                                    )
                                    ActionExecutor.triggerHaptic(context)
                                    ActionExecutor.playBeep()

                                    currentSampleStep = nextStep
                                    statusMessage = if (nextStep >= targetSamples) {
                                        "Calibration complete! ${selectedGestureToTrain.title} is now strictly matched with your hand."
                                    } else {
                                        "Captured sample $nextStep of $targetSamples. Tilt or adjust hand slightly and take next sample."
                                    }
                                } else {
                                    statusMessage = "Hold hand steadily in camera view first!"
                                }
                            },
                            enabled = handReady,
                            modifier = Modifier
                                .weight(2f)
                                .testTag("capture_training_sample_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Capture", tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (currentSampleStep < targetSamples) "Capture Sample (${currentSampleStep + 1}/$targetSamples)" else "Add Extra Sample",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isSelectedTrained || currentSampleStep > 0) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.deleteTrainingForGesture(selectedGestureToTrain.name)
                                    currentSampleStep = 0
                                    statusMessage = "Cleared custom profiles for ${selectedGestureToTrain.title}."
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset")
                            }
                        }
                    }

                    AnimatedVisibility(visible = !statusMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CyanPrimary.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = statusMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyanPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. ANTI-FALSE-POSITIVE STRICT MODE SWITCH & TOLERANCE
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Anti-False-Positive Engine",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Strict Trained Profiles Only",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Completely ignores your face, background clutter, and any movement not trained.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = requireTrainedOnly,
                            onCheckedChange = { viewModel.setRequireTrainedOnly(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldAccent,
                                checkedTrackColor = EmeraldAccent.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.testTag("strict_trained_only_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Match Strictness Threshold: ${(matchThreshold * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (matchThreshold >= 0.85f) "High Precision" else "Balanced",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = EmeraldAccent
                        )
                    }
                    Slider(
                        value = matchThreshold,
                        onValueChange = { viewModel.setMatchThreshold(it) },
                        valueRange = 0.72f..0.94f,
                        steps = 6,
                        colors = SliderDefaults.colors(thumbColor = CyanPrimary, activeTrackColor = CyanPrimary)
                    )
                }
            }
        }

        // List of all gestures with training status
        item {
            Text(
                text = "Trained Profiles Status",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        items(GestureType.entries) { gesture ->
            val samples = allTrainingSamples.filter { it.gestureId == gesture.name }
            val isTrained = samples.isNotEmpty()

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isTrained) EmeraldAccent.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = gesture.getIcon(),
                            contentDescription = gesture.title,
                            tint = if (isTrained) EmeraldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = gesture.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isTrained) "${samples.size} custom samples saved" else "Untrained (Default rules)",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = if (isTrained) EmeraldAccent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                selectedGestureToTrain = gesture
                                currentSampleStep = samples.size
                                statusMessage = "Selected ${gesture.title} for calibration."
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text(if (isTrained) "Retrain" else "Train", fontSize = 11.sp)
                        }

                        if (isTrained) {
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { viewModel.deleteTrainingForGesture(gesture.name) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun TrainingNodesCanvas(
    detection: DetectionResult,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        fun normX(x: Float): Float = (1f - x) * w
        fun normY(y: Float): Float = y * h

        // 1. Draw Face Exclusion Zone if Face is Detected
        val faceBox = detection.faceBoundingBox
        if (detection.faceDetected && faceBox != null) {
            val fLeft = normX(faceBox.right)
            val fTop = normY(faceBox.top)
            val fW = faceBox.width() * w
            val fH = faceBox.height() * h

            drawRoundRect(
                color = Color.Red.copy(alpha = 0.35f),
                topLeft = Offset(fLeft, fTop),
                size = Size(fW, fH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f, 20f),
                style = Stroke(
                    width = 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )
            )
        }

        if (!detection.handDetected) return@Canvas

        val centerPx = Offset(normX(detection.palmCenter.x), normY(detection.palmCenter.y))
        val radiusPx = (detection.palmRadius * w).coerceAtLeast(18f)

        // 2. Draw Hand Bounding Box with Cyber Brackets
        val box = detection.boundingBox
        if (box.width() > 0.05f) {
            val left = normX(box.right)
            val top = normY(box.top)
            val boxW = box.width() * w
            val boxH = box.height() * h

            drawRoundRect(
                color = CyanPrimary.copy(alpha = 0.5f),
                topLeft = Offset(left, top),
                size = Size(boxW, boxH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
                style = Stroke(width = 2f)
            )
        }

        // 3. Draw Palm Center Node with Radar Rings
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
            radius = 6f,
            center = centerPx
        )

        // 4. Draw Radial Skeleton Lines & Glowing Fingertip Nodes
        detection.fingertips.forEachIndexed { index, tip ->
            val tipPx = Offset(normX(tip.x), normY(tip.y))

            // Bone ray from palm to tip
            drawLine(
                color = CyanPrimary,
                start = centerPx,
                end = tipPx,
                strokeWidth = 3.5f
            )

            // Fingertip Node Outer Glow
            drawCircle(
                color = EmeraldAccent.copy(alpha = 0.45f),
                radius = 16f,
                center = tipPx
            )
            // Fingertip Node Solid Ring
            drawCircle(
                color = EmeraldAccent,
                radius = 8f,
                center = tipPx
            )
            // Fingertip Node Center Pip
            drawCircle(
                color = Color.White,
                radius = 3.5f,
                center = tipPx
            )
        }
    }
}
