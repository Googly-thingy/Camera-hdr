package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.db.AppDatabase
import com.example.data.repository.GestureRepository
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.MappingsScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.GestureFlowTheme
import com.example.ui.viewmodel.GestureViewModel
import com.example.ui.viewmodel.GestureViewModelFactory

import androidx.compose.material.icons.filled.ModelTraining
import com.example.ui.screens.TrainingScreen

enum class AppTab(
    val title: String,
    val icon: ImageVector,
    val testTag: String
) {
    SCANNER("Scanner", Icons.Default.CameraAlt, "tab_scanner"),
    TRAIN("Train", Icons.Default.ModelTraining, "tab_train"),
    ACTIONS("Actions", Icons.Default.Tune, "tab_actions"),
    HISTORY("History", Icons.Default.History, "tab_history"),
    SETTINGS("Settings", Icons.Default.Settings, "tab_settings")
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getDatabase(applicationContext)
        val repository = GestureRepository(
            db.gestureMappingDao(),
            db.gestureLogDao(),
            db.gestureTrainingDao()
        )
        val factory = GestureViewModelFactory(repository)

        setContent {
            GestureFlowTheme {
                val viewModel: GestureViewModel = viewModel(factory = factory)
                GestureFlowApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun GestureFlowApp(viewModel: GestureViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(AppTab.SCANNER) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsToRequest = buildList {
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasCameraPermission = results[Manifest.permission.CAMERA] == true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("main_bottom_nav"),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                AppTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title
                            )
                        },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = CyanPrimary,
                            indicatorColor = CyanPrimary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tab_transition",
            modifier = Modifier.padding(innerPadding)
        ) { tab ->
            when (tab) {
                AppTab.SCANNER -> ScannerScreen(
                    viewModel = viewModel,
                    hasCameraPermission = hasCameraPermission,
                    onRequestCameraPermission = {
                        permissionLauncher.launch(permissionsToRequest.toTypedArray())
                    }
                )
                AppTab.TRAIN -> TrainingScreen(
                    viewModel = viewModel
                )
                AppTab.ACTIONS -> MappingsScreen(
                    viewModel = viewModel
                )
                AppTab.HISTORY -> HistoryScreen(
                    viewModel = viewModel
                )
                AppTab.SETTINGS -> SettingsScreen(
                    viewModel = viewModel
                )
            }
        }
    }
}
