package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.camera.CameraManager
import com.example.model.CameraLens
import com.example.model.HdrMode
import com.example.ui.components.BacklightAlertBanner
import com.example.ui.components.ExposureControlDial
import com.example.ui.components.FocusRing
import com.example.ui.components.HdrComparisonViewer
import com.example.ui.components.HdrSettingsSheet
import com.example.ui.components.HistogramView
import com.example.ui.theme.HdrAmber
import com.example.ui.theme.HdrAmberGlow
import com.example.ui.theme.OptimalExposureGreen
import com.example.ui.theme.TitaniumDark
import com.example.ui.theme.TitaniumSurface
import com.example.ui.theme.TitaniumSurfaceElevated
import com.example.viewmodel.CameraViewModel

@Composable
fun BacklightCameraScreen(
  viewModel: CameraViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val uiState by viewModel.uiState.collectAsState()

  var showSettingsSheet by remember { mutableStateOf(false) }
  var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
  val cameraManager = remember { CameraManager(context) }

  // Check and request Camera permission
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    viewModel.setCameraPermissionGranted(isGranted)
  }

  LaunchedEffect(Unit) {
    viewModel.setCameraManager(cameraManager)
    val hasPermission = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
    viewModel.setCameraPermissionGranted(hasPermission)
    if (!hasPermission) {
      permissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  DisposableEffect(lifecycleOwner) {
    onDispose {
      cameraManager.release()
    }
  }

  // Bind live camera when surface is ready
  LaunchedEffect(uiState.hasCameraPermission, previewViewRef, uiState.currentLens) {
    val pv = previewViewRef
    if (uiState.hasCameraPermission && pv != null) {
      cameraManager.initializeCamera(lifecycleOwner, pv.surfaceProvider, uiState.currentLens)
    }
  }

  // Toast notifications
  LaunchedEffect(uiState.statusToast) {
    uiState.statusToast?.let { msg ->
      Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
      viewModel.clearToast()
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black)
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("backlight_camera_screen")
  ) {
    if (!uiState.hasCameraPermission) {
      // Clean Permission Request State
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Box(
          modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(HdrAmber.copy(alpha = 0.15f))
            .border(1.dp, HdrAmber, CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            tint = HdrAmber,
            modifier = Modifier.size(36.dp)
          )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
          text = "Camera Permission Required",
          style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = "Backlight HDR Camera needs access to the camera to analyze high-contrast lighting and capture photos with balanced exposure.",
          style = MaterialTheme.typography.bodyMedium.copy(
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
          ),
          modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
          onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
          colors = ButtonDefaults.buttonColors(containerColor = HdrAmber, contentColor = Color.Black),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .height(48.dp)
            .testTag("grant_camera_permission_button")
        ) {
          Text("Enable Camera Access", fontWeight = FontWeight.Bold)
        }
      }
    } else {
      // Main Camera Interface: 4:3 Native Uncropped Ratio
      Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // 1. Top HUD Control Bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // HDR Mode Tag
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(16.dp))
              .background(TitaniumDark.copy(alpha = 0.85f))
              .border(1.dp, HdrAmber.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
              .clickable { showSettingsSheet = true }
              .padding(horizontal = 10.dp, vertical = 5.dp)
              .testTag("hdr_mode_chip")
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = HdrAmber,
                modifier = Modifier.size(14.dp)
              )
              Text(
                text = uiState.hdrSettings.mode.label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                  fontSize = 10.sp
                )
              )
            }
          }

          // 4:3 Native Aspect Ratio Pill
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(TitaniumSurfaceElevated)
              .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
              .padding(horizontal = 7.dp, vertical = 3.dp)
          ) {
            Text(
              text = "4:3 NATIVE",
              style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = OptimalExposureGreen,
                fontSize = 10.sp
              )
            )
          }

          // Action Icons: Histogram, Flash, Settings
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            // Live Histogram Toggle
            IconButton(
              onClick = { viewModel.toggleHistogramVisibility() },
              modifier = Modifier
                .clip(CircleShape)
                .background(if (uiState.isHistogramVisible) HdrAmber else TitaniumDark)
                .size(34.dp)
                .testTag("toggle_histogram_button")
            ) {
              Icon(
                imageVector = Icons.Default.BarChart,
                contentDescription = "Toggle Histogram",
                tint = if (uiState.isHistogramVisible) Color.Black else Color.White,
                modifier = Modifier.size(17.dp)
              )
            }

            // Flash Torch (rear camera only)
            if (uiState.currentLens == CameraLens.BACK) {
              IconButton(
                onClick = { viewModel.toggleFlash() },
                modifier = Modifier
                  .clip(CircleShape)
                  .background(if (uiState.isFlashOn) HdrAmber else TitaniumDark)
                  .size(34.dp)
                  .testTag("toggle_flash_button")
              ) {
                Icon(
                  imageVector = if (uiState.isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                  contentDescription = "Toggle Flash",
                  tint = if (uiState.isFlashOn) Color.Black else Color.White,
                  modifier = Modifier.size(17.dp)
                )
              }
            }

            // Settings Sheet
            IconButton(
              onClick = { showSettingsSheet = true },
              modifier = Modifier
                .clip(CircleShape)
                .background(TitaniumDark)
                .size(34.dp)
                .testTag("open_settings_button")
            ) {
              Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "HDR Settings",
                tint = Color.White,
                modifier = Modifier.size(17.dp)
              )
            }
          }
        }

        // Floating Backlight Alert Banner
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
          BacklightAlertBanner(
            backlightState = uiState.backlightState,
            currentHdrMode = uiState.hdrSettings.mode,
            currentEv = uiState.manualEvCompensation,
            onApplyRecommendedEv = { viewModel.applyRecommendedEv() },
            onEnableBacklightHdr = { viewModel.setHdrMode(HdrMode.BACKLIGHT_BOOST) }
          )
        }

        // Floating Real-Time Histogram Overlay
        AnimatedVisibility(
          visible = uiState.isHistogramVisible,
          enter = fadeIn() + slideInVertically(),
          exit = fadeOut() + slideOutVertically(),
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
          HistogramView(
            histogramData = uiState.histogramData,
            selectedChannel = uiState.histogramChannel,
            onChannelSelected = { viewModel.setHistogramChannel(it) },
            showClippingAlerts = uiState.hdrSettings.showZebraAlerts
          )
        }

        Spacer(modifier = Modifier.weight(0.1f))

        // 2. Native 4:3 Viewfinder (3 width : 4 height, 100% uncropped sensor frame)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
              detectTapGestures { offset ->
                val normX = (offset.x / size.width).coerceIn(0f, 1f)
                val normY = (offset.y / size.height).coerceIn(0f, 1f)
                viewModel.onFocusTap(normX, normY, size.width.toFloat(), size.height.toFloat())
              }
            }
            .testTag("camera_viewfinder"),
          contentAlignment = Alignment.Center
        ) {
          AndroidView(
            factory = { ctx ->
              PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FIT_CENTER
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                previewViewRef = this
              }
            },
            modifier = Modifier.fillMaxSize()
          )

          // Tap-to-meter reticle
          uiState.tapFocusPoint?.let { point ->
            FocusRing(tapPoint = point)
          }

          // Lens Indicator Badge
          Box(
            modifier = Modifier
              .align(Alignment.BottomStart)
              .padding(8.dp)
              .clip(RoundedCornerShape(4.dp))
              .background(Color.Black.copy(alpha = 0.65f))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = if (uiState.currentLens == CameraLens.FRONT) "SELFIE CAM" else "REAR CAM",
              style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.85f)
              )
            )
          }
        }

        Spacer(modifier = Modifier.weight(0.1f))

        // 3. Manual Exposure Compensation Scrub Dial
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
        ) {
          ExposureControlDial(
            currentEv = uiState.manualEvCompensation,
            minEv = uiState.minEv,
            maxEv = uiState.maxEv,
            recommendedEv = uiState.backlightState.recommendedEvCompensation,
            onEvChanged = { viewModel.setEvCompensation(it) },
            onResetEv = { viewModel.resetEvCompensation() },
            onApplyRecommended = { viewModel.applyRecommendedEv() }
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Bottom Controls Bar: Gallery, Shutter Button, Selfie Flip Button
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Left: Gallery / Recent Capture Thumbnail
          Box(
            modifier = Modifier
              .size(52.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(TitaniumSurfaceElevated)
              .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
              .clickable {
                uiState.recentPhotos.firstOrNull()?.let { photo ->
                  viewModel.capturePhoto(context)
                }
              }
              .testTag("recent_photos_thumbnail"),
            contentAlignment = Alignment.Center
          ) {
            val recent = uiState.recentPhotos.firstOrNull()
            if (recent != null) {
              Image(
                bitmap = recent.hdrBitmap.asImageBitmap(),
                contentDescription = "Recent Capture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            } else {
              Icon(
                imageVector = Icons.Default.Photo,
                contentDescription = "Gallery",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
              )
            }
          }

          // Center: Large Backlight HDR Shutter Button
          Box(
            modifier = Modifier
              .size(76.dp)
              .clip(CircleShape)
              .border(3.dp, HdrAmber, CircleShape)
              .padding(4.dp)
              .clip(CircleShape)
              .background(if (uiState.isCapturing) HdrAmberGlow else Color.White)
              .clickable(enabled = !uiState.isCapturing) {
                viewModel.capturePhoto(context)
              }
              .testTag("shutter_button"),
            contentAlignment = Alignment.Center
          ) {
            if (uiState.isCapturing) {
              CircularProgressIndicator(
                color = Color.Black,
                strokeWidth = 3.dp,
                modifier = Modifier.size(34.dp)
              )
            } else {
              Box(
                modifier = Modifier
                  .size(58.dp)
                  .clip(CircleShape)
                  .background(HdrAmber)
                  .border(2.dp, Color.Black.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.CameraAlt,
                  contentDescription = "Capture Backlight HDR Photo",
                  tint = Color.Black,
                  modifier = Modifier.size(26.dp)
                )
              }
            }
          }

          // Right: Selfie Camera Switcher Button
          Box(
            modifier = Modifier
              .size(52.dp)
              .clip(CircleShape)
              .background(TitaniumSurfaceElevated)
              .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
              .clickable {
                previewViewRef?.let { pv ->
                  viewModel.switchCameraLens(lifecycleOwner, pv.surfaceProvider)
                }
              }
              .testTag("switch_camera_lens_button"),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.FlipCameraAndroid,
              contentDescription = "Switch Camera (Selfie / Rear)",
              tint = HdrAmber,
              modifier = Modifier.size(24.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))
      }
    }

    // HDR Settings Bottom Sheet
    if (showSettingsSheet) {
      HdrSettingsSheet(
        settings = uiState.hdrSettings,
        onSettingsChanged = {
          viewModel.updateHdrSettings(
            shadowLift = it.shadowLift,
            highlightRecovery = it.highlightRecovery,
            fillLight = it.fillLightEmulation,
            showZebra = it.showZebraAlerts
          )
          viewModel.setHdrMode(it.mode)
        },
        onDismiss = { showSettingsSheet = false }
      )
    }

    // Captured Photo Interactive Before / After Inspector
    uiState.capturedPhoto?.let { photo ->
      HdrComparisonViewer(
        photo = photo,
        onSaveToGallery = { viewModel.savePhotoToGallery(context, it) },
        onDismiss = { viewModel.dismissCapturedPhoto() }
      )
    }
  }
}
