package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.data.model.ActionCategory
import com.example.data.model.ActionType
import com.example.data.model.GestureMapping
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldAccent
import com.example.ui.viewmodel.GestureViewModel
import com.example.util.InstalledAppInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MappingsScreen(
    viewModel: GestureViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mappings by viewModel.allMappings.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()

    var editingMapping by remember { mutableStateOf<GestureMapping?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        viewModel.loadInstalledApps(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Screen Header
        Text(
            text = "Gesture Actions",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Customize system shortcuts or app launches for each movement",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Gestures List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(mappings, key = { it.gestureId }) { mapping ->
                GestureMappingCard(
                    mapping = mapping,
                    onToggleEnabled = { isChecked ->
                        viewModel.toggleMapping(mapping.gestureId, isChecked)
                    },
                    onCustomize = {
                        editingMapping = mapping
                    },
                    onTestTrigger = {
                        viewModel.simulateTrigger(context, mapping)
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Customization Sheet
    editingMapping?.let { currentMapping ->
        ModalBottomSheet(
            onDismissRequest = { editingMapping = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            CustomizeGestureSheet(
                mapping = currentMapping,
                installedApps = installedApps,
                onDismiss = { editingMapping = null },
                onSave = { updated ->
                    viewModel.updateMapping(updated)
                    editingMapping = null
                },
                onTest = { testMapping ->
                    viewModel.simulateTrigger(context, testMapping)
                }
            )
        }
    }
}

@Composable
fun GestureMappingCard(
    mapping: GestureMapping,
    onToggleEnabled: (Boolean) -> Unit,
    onCustomize: () -> Unit,
    onTestTrigger: () -> Unit
) {
    val gesture = mapping.gesture
    val action = mapping.action

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("gesture_card_${gesture.name}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (mapping.isEnabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (mapping.isEnabled) CyanPrimary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Gesture Icon + Title + Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(CyanPrimary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = gesture.getIcon(),
                            contentDescription = gesture.title,
                            tint = CyanPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = gesture.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = gesture.description,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = mapping.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyanPrimary,
                        checkedTrackColor = CyanPrimary.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.testTag("toggle_${gesture.name}")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(12.dp))

            // Assigned Action & Timing Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Action Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = EmeraldAccent.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = action.getIcon(),
                            contentDescription = action.title,
                            tint = EmeraldAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (action == ActionType.LAUNCH_APP && !mapping.targetAppName.isNullOrBlank()) {
                                "App: ${mapping.targetAppName}"
                            } else if (action == ActionType.OPEN_URL && !mapping.targetUrl.isNullOrBlank()) {
                                "Web: ${mapping.targetUrl}"
                            } else {
                                action.title
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = EmeraldAccent,
                            maxLines = 1
                        )
                    }
                }

                // Timing & Configure Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onTestTrigger,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Test Trigger",
                            tint = CyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    OutlinedButton(
                        onClick = onCustomize,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("customize_button_${gesture.name}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Customize",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomizeGestureSheet(
    mapping: GestureMapping,
    installedApps: List<InstalledAppInfo>,
    onDismiss: () -> Unit,
    onSave: (GestureMapping) -> Unit,
    onTest: (GestureMapping) -> Unit
) {
    var selectedAction by remember { mutableStateOf(mapping.action) }
    var selectedPackage by remember { mutableStateOf(mapping.targetPackageName) }
    var selectedAppName by remember { mutableStateOf(mapping.targetAppName) }
    var targetUrl by remember { mutableStateOf(mapping.targetUrl ?: "https://www.google.com") }
    var holdDuration by remember { mutableFloatStateOf(mapping.holdTimeMs.toFloat()) }
    var cooldownDuration by remember { mutableFloatStateOf(mapping.cooldownMs.toFloat()) }

    var appSearchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<ActionCategory?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .height(580.dp)
    ) {
        // Sheet Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Configure ${mapping.gesture.title}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Select what happens when you perform this gesture",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Category Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategoryFilter == null,
                onClick = { selectedCategoryFilter = null },
                label = { Text("All", fontSize = 11.sp) }
            )
            ActionCategory.entries.forEach { cat ->
                FilterChip(
                    selected = selectedCategoryFilter == cat,
                    onClick = { selectedCategoryFilter = cat },
                    label = { Text(cat.name, fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Actions & App Selector Area
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val availableActions = ActionType.entries.filter {
                selectedCategoryFilter == null || it.category == selectedCategoryFilter
            }

            items(availableActions) { action ->
                val isSelected = selectedAction == action
                Surface(
                    onClick = { selectedAction = action },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) CyanPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) CyanPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = action.getIcon(),
                                contentDescription = action.title,
                                tint = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = action.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = action.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = CyanPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // If Launch App is selected, show installed apps picker
            if (selectedAction == ActionType.LAUNCH_APP) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Choose Application to Launch:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search installed apps...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                val filteredApps = installedApps.filter {
                    it.appName.contains(appSearchQuery, ignoreCase = true) ||
                            it.packageName.contains(appSearchQuery, ignoreCase = true)
                }

                items(filteredApps.take(15)) { app ->
                    val isAppSelected = selectedPackage == app.packageName
                    Surface(
                        onClick = {
                            selectedPackage = app.packageName
                            selectedAppName = app.appName
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isAppSelected) EmeraldAccent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isAppSelected) EmeraldAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = app.appName,
                                tint = if (isAppSelected) EmeraldAccent else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isAppSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected App",
                                    tint = EmeraldAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // If Open URL is selected, show URL input
            if (selectedAction == ActionType.OPEN_URL) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Website URL:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = targetUrl,
                        onValueChange = { targetUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://example.com") },
                        leadingIcon = { Icon(Icons.Default.Language, contentDescription = "URL") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Timing Settings Section
            item {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Pose Hold Duration: ${holdDuration.toInt()} ms",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "How long to hold hand pose to confirm detection",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = holdDuration,
                    onValueChange = { holdDuration = it },
                    valueRange = 100f..700f,
                    steps = 5,
                    colors = SliderDefaults.colors(thumbColor = CyanPrimary, activeTrackColor = CyanPrimary)
                )

                Text(
                    text = "Cooldown Window: ${cooldownDuration.toInt()} ms",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Prevents rapid accidental repeated triggers",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = cooldownDuration,
                    onValueChange = { cooldownDuration = it },
                    valueRange = 500f..2500f,
                    steps = 7,
                    colors = SliderDefaults.colors(thumbColor = EmeraldAccent, activeTrackColor = EmeraldAccent)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bottom Action Buttons
        val constructedMapping = mapping.copy(
            actionId = selectedAction.name,
            targetPackageName = if (selectedAction == ActionType.LAUNCH_APP) selectedPackage else null,
            targetAppName = if (selectedAction == ActionType.LAUNCH_APP) selectedAppName else null,
            targetUrl = if (selectedAction == ActionType.OPEN_URL) targetUrl else null,
            holdTimeMs = holdDuration.toLong(),
            cooldownMs = cooldownDuration.toLong()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { onTest(constructedMapping) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Test", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Test Now")
            }

            Button(
                onClick = { onSave(constructedMapping) },
                modifier = Modifier.weight(1.5f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
            ) {
                Text("Save Action", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}
