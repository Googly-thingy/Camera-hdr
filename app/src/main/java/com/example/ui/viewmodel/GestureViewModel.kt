package com.example.ui.viewmodel

import android.content.Context
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.action.ActionExecutor
import com.example.data.model.ActionType
import com.example.data.model.GestureLog
import com.example.data.model.GestureMapping
import com.example.data.model.GestureType
import com.example.data.repository.GestureRepository
import com.example.service.GestureRecognitionService
import com.example.util.InstalledAppInfo
import com.example.util.InstalledAppsHelper
import com.example.vision.DetectionResult
import com.example.vision.GestureDetectorEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GestureViewModel(
    private val repository: GestureRepository
) : ViewModel() {

    val allMappings: StateFlow<List<GestureMapping>> = repository.allMappings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLogs: StateFlow<List<GestureLog>> = repository.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalExecutions: StateFlow<Int> = repository.totalCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val averageLatency: StateFlow<Double?> = repository.averageLatency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isServiceRunning: StateFlow<Boolean> = GestureRecognitionService.isRunning
    val isServicePaused: StateFlow<Boolean> = GestureRecognitionService.isServicePaused
    val serviceLiveDetection: StateFlow<DetectionResult?> = GestureRecognitionService.liveDetection
    val serviceLastTriggered: StateFlow<String?> = GestureRecognitionService.lastTriggeredAction

    private val _inAppLiveDetection = MutableStateFlow<DetectionResult?>(null)
    val inAppLiveDetection: StateFlow<DetectionResult?> = _inAppLiveDetection.asStateFlow()

    private val _inAppLastTriggered = MutableStateFlow<String?>(null)
    val inAppLastTriggered: StateFlow<String?> = _inAppLastTriggered.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _sensitivity = MutableStateFlow(0.65f)
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()

    private val _soundFeedback = MutableStateFlow(true)
    val soundFeedback: StateFlow<Boolean> = _soundFeedback.asStateFlow()

    private val _hapticFeedback = MutableStateFlow(true)
    val hapticFeedback: StateFlow<Boolean> = _hapticFeedback.asStateFlow()

    private val inAppDetector = GestureDetectorEngine(_sensitivity.value)
    private var candidateGesture: GestureType? = null
    private var candidateStartTime = 0L
    private var lastActionTime = 0L

    // Unified live detection for the UI
    val currentDetection: StateFlow<DetectionResult?> = combine(
        isServiceRunning,
        serviceLiveDetection,
        inAppLiveDetection
    ) { running, serviceDet, inAppDet ->
        if (running) serviceDet else inAppDet
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val latestActionBanner: StateFlow<String?> = combine(
        isServiceRunning,
        serviceLastTriggered,
        inAppLastTriggered
    ) { running, sBanner, iBanner ->
        if (running) sBanner else iBanner
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            repository.ensureDefaultMappings()
        }
    }

    fun loadInstalledApps(context: Context) {
        viewModelScope.launch {
            val apps = InstalledAppsHelper.getInstalledLaunchableApps(context)
            _installedApps.value = apps
        }
    }

    fun startService(context: Context) {
        GestureRecognitionService.start(context)
    }

    fun stopService(context: Context) {
        GestureRecognitionService.stop(context)
    }

    fun togglePauseService(context: Context) {
        GestureRecognitionService.togglePause(context)
    }

    fun updateMapping(mapping: GestureMapping) {
        viewModelScope.launch {
            repository.updateMapping(mapping)
        }
    }

    fun toggleMapping(gestureId: String, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.toggleMapping(gestureId, isEnabled)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun setSensitivity(value: Float) {
        _sensitivity.value = value
        inAppDetector.sensitivityThreshold = value
    }

    fun setSoundFeedback(enabled: Boolean) {
        _soundFeedback.value = enabled
    }

    fun setHapticFeedback(enabled: Boolean) {
        _hapticFeedback.value = enabled
    }

    fun processInAppFrame(image: ImageProxy, context: Context) {
        try {
            val result = inAppDetector.analyze(image)
            _inAppLiveDetection.value = result

            if (result.handDetected && result.gesture != null) {
                val gesture = result.gesture
                val mapping = allMappings.value.find { it.gestureId == gesture.name }
                if (mapping != null && mapping.isEnabled) {
                    val now = System.currentTimeMillis()
                    if (now - lastActionTime >= mapping.cooldownMs) {
                        if (candidateGesture == gesture) {
                            val elapsed = now - candidateStartTime
                            if (elapsed >= mapping.holdTimeMs) {
                                lastActionTime = now
                                candidateGesture = null

                                viewModelScope.launch(Dispatchers.Main) {
                                    if (_soundFeedback.value) {
                                        ActionExecutor.playBeep()
                                    }
                                    if (_hapticFeedback.value) {
                                        ActionExecutor.triggerHaptic(context)
                                    }

                                    val msg = ActionExecutor.execute(context, mapping)
                                    _inAppLastTriggered.value = "${gesture.title} → $msg"

                                    repository.logExecution(
                                        gesture = gesture,
                                        action = mapping.action,
                                        actionLabel = msg,
                                        latencyMs = result.latencyMs,
                                        confidence = result.confidence
                                    )
                                }
                            }
                        } else {
                            candidateGesture = gesture
                            candidateStartTime = now
                        }
                    }
                }
            } else {
                candidateGesture = null
            }
        } catch (_: Exception) {
        } finally {
            image.close()
        }
    }

    fun simulateTrigger(context: Context, mapping: GestureMapping) {
        viewModelScope.launch(Dispatchers.Main) {
            if (_soundFeedback.value) ActionExecutor.playBeep()
            if (_hapticFeedback.value) ActionExecutor.triggerHaptic(context)
            val msg = ActionExecutor.execute(context, mapping)
            _inAppLastTriggered.value = "Test: ${mapping.gesture.title} → $msg"
            repository.logExecution(
                gesture = mapping.gesture,
                action = mapping.action,
                actionLabel = "Simulated: $msg",
                latencyMs = 8L,
                confidence = 1.0f
            )
        }
    }
}

class GestureViewModelFactory(
    private val repository: GestureRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GestureViewModel::class.java)) {
            return GestureViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
