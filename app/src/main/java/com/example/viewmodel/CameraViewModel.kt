package com.example.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.Preview
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.camera.CameraManager
import com.example.data.CameraPreferences
import com.example.model.BacklightSeverity
import com.example.model.BacklightState
import com.example.model.CameraLens
import com.example.model.CapturedPhoto
import com.example.model.HdrMode
import com.example.model.HdrSettings
import com.example.model.HistogramChannel
import com.example.model.HistogramData
import com.example.processing.BacklightHdrProcessor
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CameraUiState(
  val currentLens: CameraLens = CameraLens.BACK,
  val hasCameraPermission: Boolean = false,
  val manualEvCompensation: Float = 0.0f,
  val minEv: Float = -3.0f,
  val maxEv: Float = 3.0f,
  val evStep: Float = 0.166667f,
  val histogramData: HistogramData = HistogramData(),
  val backlightState: BacklightState = BacklightState(),
  val hdrSettings: HdrSettings = HdrSettings(),
  val histogramChannel: HistogramChannel = HistogramChannel.RGB,
  val isHistogramVisible: Boolean = true,
  val isFlashOn: Boolean = false,
  val isCapturing: Boolean = false,
  val capturedPhoto: CapturedPhoto? = null,
  val recentPhotos: List<CapturedPhoto> = emptyList(),
  val tapFocusPoint: Offset? = null,
  val statusToast: String? = null
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {

  private val preferences = CameraPreferences(application)

  private val _uiState = MutableStateFlow(
    CameraUiState(
      currentLens = preferences.getLens(),
      hdrSettings = preferences.getHdrSettings(),
      isHistogramVisible = preferences.isHistogramVisible(),
      histogramChannel = preferences.getHistogramChannel(),
      manualEvCompensation = preferences.getEvCompensation(),
      isFlashOn = preferences.isFlashOn()
    )
  )
  val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

  private var cameraManager: CameraManager? = null

  fun setCameraManager(manager: CameraManager) {
    this.cameraManager = manager
    manager.onHistogramUpdate = { histData, backlightState ->
      _uiState.update { it.copy(histogramData = histData, backlightState = backlightState) }
    }
    manager.onExposureRangeChanged = { minEv, maxEv, step ->
      _uiState.update { it.copy(minEv = minEv, maxEv = maxEv, evStep = step) }
    }
  }

  fun setCameraPermissionGranted(granted: Boolean) {
    _uiState.update { it.copy(hasCameraPermission = granted) }
  }

  fun switchCameraLens(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
    viewModelScope.launch {
      val targetLens = if (_uiState.value.currentLens == CameraLens.BACK) CameraLens.FRONT else CameraLens.BACK
      cameraManager?.initializeCamera(lifecycleOwner, surfaceProvider, targetLens)
      preferences.setLens(targetLens)
      _uiState.update { it.copy(currentLens = targetLens, isFlashOn = false) }
      preferences.setFlashOn(false)
      showToast(if (targetLens == CameraLens.FRONT) "Switched to Selfie Camera" else "Switched to Rear Camera")
    }
  }

  fun setEvCompensation(ev: Float) {
    val clamped = ev.coerceIn(_uiState.value.minEv, _uiState.value.maxEv)
    val rounded = Math.round(clamped * 10f) / 10f
    _uiState.update { it.copy(manualEvCompensation = rounded) }
    preferences.setEvCompensation(rounded)
    cameraManager?.setExposureCompensation(rounded)
  }

  fun applyRecommendedEv() {
    val rec = _uiState.value.backlightState.recommendedEvCompensation
    setEvCompensation(rec)
    showToast("Applied Backlight EV: +${rec} EV")
  }

  fun resetEvCompensation() {
    setEvCompensation(0.0f)
  }

  fun setHdrMode(mode: HdrMode) {
    val updated = _uiState.value.hdrSettings.copy(mode = mode)
    _uiState.update { it.copy(hdrSettings = updated) }
    preferences.setHdrSettings(updated)
  }

  fun updateHdrSettings(
    shadowLift: Float? = null,
    highlightRecovery: Float? = null,
    fillLight: Boolean? = null,
    showZebra: Boolean? = null
  ) {
    _uiState.update { current ->
      val updated = current.hdrSettings.copy(
        shadowLift = shadowLift ?: current.hdrSettings.shadowLift,
        highlightRecovery = highlightRecovery ?: current.hdrSettings.highlightRecovery,
        fillLightEmulation = fillLight ?: current.hdrSettings.fillLightEmulation,
        showZebraAlerts = showZebra ?: current.hdrSettings.showZebraAlerts
      )
      preferences.setHdrSettings(updated)
      current.copy(hdrSettings = updated)
    }
  }

  fun setHistogramChannel(channel: HistogramChannel) {
    _uiState.update { it.copy(histogramChannel = channel) }
    preferences.setHistogramChannel(channel)
  }

  fun toggleHistogramVisibility() {
    val next = !_uiState.value.isHistogramVisible
    _uiState.update { it.copy(isHistogramVisible = next) }
    preferences.setHistogramVisible(next)
  }

  fun toggleFlash() {
    // Torch is only relevant for rear camera
    if (_uiState.value.currentLens == CameraLens.FRONT) {
      showToast("Flash is only available on rear camera")
      return
    }
    val next = !_uiState.value.isFlashOn
    _uiState.update { it.copy(isFlashOn = next) }
    preferences.setFlashOn(next)
    cameraManager?.setFlashMode(next)
  }

  fun onFocusTap(normX: Float, normY: Float, viewWidth: Float, viewHeight: Float) {
    _uiState.update { it.copy(tapFocusPoint = Offset(normX * viewWidth, normY * viewHeight)) }
    cameraManager?.triggerSpotMetering(normX, normY, viewWidth, viewHeight)
    viewModelScope.launch {
      kotlinx.coroutines.delay(2800)
      _uiState.update { if (it.tapFocusPoint == Offset(normX * viewWidth, normY * viewHeight)) it.copy(tapFocusPoint = null) else it }
    }
  }

  fun capturePhoto(context: Context) {
    val state = _uiState.value
    if (state.isCapturing) return

    _uiState.update { it.copy(isCapturing = true) }

    viewModelScope.launch(Dispatchers.Default) {
      try {
        val rawBitmap: Bitmap? = cameraManager?.capturePhoto()

        if (rawBitmap == null) {
          withContext(Dispatchers.Main) {
            _uiState.update { it.copy(isCapturing = false) }
            showToast("Camera frame not available. Please retry.")
          }
          return@launch
        }

        val isMultiFrame = state.hdrSettings.mode == HdrMode.MULTI_FRAME_HDR
        val shadowLift = if (state.hdrSettings.mode == HdrMode.OFF) 0f else state.hdrSettings.shadowLift
        val highlightRecovery = if (state.hdrSettings.mode == HdrMode.OFF) 0f else state.hdrSettings.highlightRecovery
        val fillLight = if (state.hdrSettings.mode == HdrMode.OFF) false else state.hdrSettings.fillLightEmulation

        val hdrBitmap = BacklightHdrProcessor.processBacklightHdr(
          source = rawBitmap,
          evBias = state.manualEvCompensation,
          shadowLift = shadowLift,
          highlightRecovery = highlightRecovery,
          fillLightEmulation = fillLight,
          isMultiFrameMode = isMultiFrame
        )

        val (histBefore, _) = BacklightHdrProcessor.analyzeBitmap(rawBitmap)
        val (histAfter, _) = BacklightHdrProcessor.analyzeBitmap(hdrBitmap)

        val captured = CapturedPhoto(
          originalBitmap = rawBitmap,
          hdrBitmap = hdrBitmap,
          evBias = state.manualEvCompensation,
          hdrModeUsed = state.hdrSettings.mode,
          contrastRatio = state.backlightState.contrastRatio,
          histogramBefore = histBefore,
          histogramAfter = histAfter,
          lensUsed = state.currentLens
        )

        withContext(Dispatchers.Main) {
          _uiState.update { current ->
            current.copy(
              isCapturing = false,
              capturedPhoto = captured,
              recentPhotos = listOf(captured) + current.recentPhotos.take(5)
            )
          }
        }
      } catch (e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
          _uiState.update { it.copy(isCapturing = false) }
          showToast("Capture error: ${e.message}")
        }
      }
    }
  }

  fun savePhotoToGallery(context: Context, photo: CapturedPhoto) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
          val filename = "BACKLIGHT_HDR_${System.currentTimeMillis()}.jpg"
          put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
          put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BacklightHDR")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
          }
        }

        val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
          resolver.openOutputStream(uri)?.use { stream: OutputStream ->
            photo.hdrBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
          }

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
          }

          withContext(Dispatchers.Main) {
            showToast("Saved Backlight HDR photo to Gallery!")
          }
        }
      } catch (e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
          showToast("Failed to save photo: ${e.message}")
        }
      }
    }
  }

  fun dismissCapturedPhoto() {
    _uiState.update { it.copy(capturedPhoto = null) }
  }

  fun showToast(msg: String) {
    _uiState.update { it.copy(statusToast = msg) }
  }

  fun clearToast() {
    _uiState.update { it.copy(statusToast = null) }
  }
}
