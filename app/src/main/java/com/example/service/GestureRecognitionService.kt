package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.MainActivity
import com.example.action.ActionExecutor
import com.example.data.db.AppDatabase
import com.example.data.model.GestureMapping
import com.example.data.model.GestureType
import com.example.data.repository.GestureRepository
import com.example.vision.DetectionResult
import com.example.vision.GestureDetectorEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GestureRecognitionService : LifecycleService() {

    private val detector = GestureDetectorEngine()
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null

    private lateinit var repository: GestureRepository
    private var activeMappings = mapOf<String, GestureMapping>()

    // Gesture Debounce & Cooldown state
    private var candidateGesture: GestureType? = null
    private var candidateStartTime = 0L
    private var lastActionTime = 0L
    private var isPaused = false

    companion object {
        const val CHANNEL_ID = "gesture_flow_service_channel"
        const val NOTIFICATION_ID = 4040

        const val ACTION_START = "com.example.service.START"
        const val ACTION_STOP = "com.example.service.STOP"
        const val ACTION_TOGGLE_PAUSE = "com.example.service.TOGGLE_PAUSE"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _isServicePaused = MutableStateFlow(false)
        val isServicePaused: StateFlow<Boolean> = _isServicePaused.asStateFlow()

        private val _liveDetection = MutableStateFlow<DetectionResult?>(null)
        val liveDetection: StateFlow<DetectionResult?> = _liveDetection.asStateFlow()

        private val _lastTriggeredAction = MutableStateFlow<String?>(null)
        val lastTriggeredAction: StateFlow<String?> = _lastTriggeredAction.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, GestureRecognitionService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, GestureRecognitionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun togglePause(context: Context) {
            val intent = Intent(context, GestureRecognitionService::class.java).apply {
                action = ACTION_TOGGLE_PAUSE
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        cameraExecutor = Executors.newSingleThreadExecutor()

        val db = AppDatabase.getDatabase(applicationContext)
        repository = GestureRepository(db.gestureMappingDao(), db.gestureLogDao())

        createNotificationChannel()

        // Keep active mappings synchronized from Room
        lifecycleScope.launch(Dispatchers.IO) {
            repository.enabledMappings.collect { list ->
                activeMappings = list.associateBy { it.gestureId }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PAUSE -> {
                isPaused = !isPaused
                _isServicePaused.value = isPaused
                updateNotification()
                return START_STICKY
            }
            ACTION_START, null -> {
                startForegroundWithNotification()
                startCameraAnalysis()
            }
        }

        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification(isPaused)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        _isRunning.value = true
    }

    private fun buildNotification(paused: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseIntent = Intent(this, GestureRecognitionService::class.java).apply {
            action = ACTION_TOGGLE_PAUSE
        }
        val pendingPause = PendingIntent.getService(
            this,
            1,
            pauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, GestureRecognitionService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val statusText = if (paused) "Paused - Touch to resume" else "Actively scanning front camera gestures"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GestureFlow Running")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_pause,
                if (paused) "Resume" else "Pause",
                pendingPause
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                pendingStop
            )
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(isPaused))
    }

    private fun startCameraAnalysis() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            if (isPaused) {
                imageProxy.close()
                return@setAnalyzer
            }

            try {
                val result = detector.analyze(imageProxy)
                _liveDetection.value = result

                if (result.handDetected && result.gesture != null) {
                    processGestureTrigger(result)
                } else {
                    candidateGesture = null
                }
            } catch (_: Exception) {
            } finally {
                imageProxy.close()
            }
        }

        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, cameraSelector, imageAnalysis)
        } catch (_: Exception) {
        }
    }

    private fun processGestureTrigger(result: DetectionResult) {
        val gesture = result.gesture ?: return
        val mapping = activeMappings[gesture.name] ?: return
        if (!mapping.isEnabled) return

        val now = System.currentTimeMillis()

        // Check cooldown
        if (now - lastActionTime < mapping.cooldownMs) {
            return
        }

        // Check hold time / debounce
        if (candidateGesture == gesture) {
            val elapsedHold = now - candidateStartTime
            if (elapsedHold >= mapping.holdTimeMs) {
                // Confirmed! Trigger action
                lastActionTime = now
                candidateGesture = null

                lifecycleScope.launch(Dispatchers.Main) {
                    val message = ActionExecutor.execute(applicationContext, mapping)
                    _lastTriggeredAction.value = "${gesture.title} → $message"

                    repository.logExecution(
                        gesture = gesture,
                        action = mapping.action,
                        actionLabel = message,
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GestureFlow Active Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of background gesture recognition"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundService() {
        _isRunning.value = false
        _liveDetection.value = null
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        _isRunning.value = false
        _liveDetection.value = null
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        }
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
