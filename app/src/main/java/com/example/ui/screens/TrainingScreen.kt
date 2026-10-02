package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.action.ActionExecutor
import com.example.data.model.GestureSignature
import com.example.data.model.GestureType
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldAccent
import com.example.ui.viewmodel.GestureViewModel

@Composable
fun TrainingScreen(
    viewModel: GestureViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentDetection by viewModel.currentDetection.collectAsState()
    val trainedGestureIds by viewModel.trainedGestureIds.collectAsState()
    val allTrainingSamples by viewModel.allTrainingSamples.collectAsState()
    val requireTrainedOnly by viewModel.requireTrainedOnly.collectAsState()
    val matchThreshold by viewModel.matchThreshold.collectAsState()

    var selectedGestureToTrain by remember { mutableStateOf(GestureType.OPEN_PALM) }
    var currentSampleStep by remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

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
            // Header
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
                        text = "Train custom hand profiles to eliminate false positives",
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
                            text = "${trainedGestureIds.size}/${GestureType.entries.size} Trained",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldAccent
                        )
                    }
                }
            }
        }

        // Training Selector Carousel
        item {
            Text(
                text = "Select Gesture to Train:",
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
                            selectedContainerColor = CyanPrimary.copy(alpha = 0.15f),
                            selectedLabelColor = CyanPrimary
                        )
                    )
                }
            }
        }

        // Active Training Bench Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("training_studio_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (currentDetection?.handDetected == true) CyanPrimary else MaterialTheme.colorScheme.outline
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Gesture Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(CyanPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = selectedGestureToTrain.getIcon(),
                                    contentDescription = selectedGestureToTrain.title,
                                    tint = CyanPrimary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = selectedGestureToTrain.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = selectedGestureToTrain.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isSelectedTrained) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = EmeraldAccent.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "TRAINED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldAccent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live Hand Detection Status in Camera
                    val handReady = currentDetection?.handDetected == true
                    val currentSig = currentDetection?.signature
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (handReady) EmeraldAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (handReady) EmeraldAccent.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (handReady) EmeraldAccent else Color.Red)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (handReady) "Hand in frame: Ready to capture" else "Show hand to camera",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (handReady) {
                                Text(
                                    text = "${currentDetection?.rawFingerCount ?: 0} tips",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = EmeraldAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sample Step Indicators (3 samples)
                    val totalTargetSamples = 3
                    val progress = (currentSampleStep.toFloat() / totalTargetSamples).coerceIn(0f, 1f)

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Training Progress",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$currentSampleStep / $totalTargetSamples samples",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = CyanPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = CyanPrimary,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Capture / Retrain Buttons
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
                                    if (nextStep >= totalTargetSamples) {
                                        statusMessage = "Profile calibrated! ${selectedGestureToTrain.title} will now be recognized accurately."
                                    } else {
                                        statusMessage = "Captured sample $nextStep of $totalTargetSamples. Move hand slightly and capture again."
                                    }
                                } else {
                                    statusMessage = "Hold hand clearly in front of front camera first!"
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
                                text = if (currentSampleStep < totalTargetSamples) "Capture Sample (${currentSampleStep + 1}/$totalTargetSamples)" else "Add Another Sample",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isSelectedTrained || currentSampleStep > 0) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.deleteTrainingForGesture(selectedGestureToTrain.name)
                                    currentSampleStep = 0
                                    statusMessage = "Cleared training for ${selectedGestureToTrain.title}."
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

                    // Status Message Banner
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

        // Strict Matching & Anti-False Positive Controls
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

                    // Strict Mode Switch: Trained Profiles Only
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Strict Trained Matching Only",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Completely ignores any hand movement that does not match your trained profiles. Prevents face and background false triggers.",
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

                    // Match Threshold Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Matching Confidence Threshold: ${(matchThreshold * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (matchThreshold >= 0.85f) "High Strictness" else "Balanced",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = EmeraldAccent
                        )
                    }
                    Text(
                        text = "Higher percentage requires your hand to be an exact match to the recorded sample.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = matchThreshold,
                        onValueChange = { viewModel.setMatchThreshold(it) },
                        valueRange = 0.70f..0.92f,
                        steps = 5,
                        colors = SliderDefaults.colors(thumbColor = CyanPrimary, activeTrackColor = CyanPrimary)
                    )
                }
            }
        }

        // List of all gestures with Training Status
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
                                text = if (isTrained) "${samples.size} sample profiles saved" else "Untrained (Default generic rules)",
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
                                statusMessage = "Selected ${gesture.title} for training."
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
