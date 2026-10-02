package com.example.action

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.widget.Toast
import com.example.data.model.ActionType
import com.example.data.model.GestureMapping
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ActionExecutor {

    private var isTorchOn = false
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        } catch (_: Exception) {
        }
    }

    suspend fun execute(context: Context, mapping: GestureMapping): String = withContext(Dispatchers.Main) {
        val action = mapping.action
        try {
            when (action) {
                ActionType.TOGGLE_FLASHLIGHT -> {
                    toggleFlashlight(context)
                }
                ActionType.MEDIA_PLAY_PAUSE -> {
                    sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    "Media: Play/Pause"
                }
                ActionType.MEDIA_NEXT -> {
                    sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                    "Media: Next Track"
                }
                ActionType.MEDIA_PREV -> {
                    sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    "Media: Previous Track"
                }
                ActionType.VOLUME_UP -> {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    am.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    "Volume Raised"
                }
                ActionType.VOLUME_DOWN -> {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    am.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    "Volume Lowered"
                }
                ActionType.MUTE_TOGGLE -> {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    am.adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                    "Mute Toggled"
                }
                ActionType.GO_HOME -> {
                    val home = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(home)
                    "Navigated Home"
                }
                ActionType.OPEN_NOTIFICATIONS -> {
                    expandNotifications(context)
                }
                ActionType.TRIGGER_BEEP -> {
                    playBeep()
                    "Beep Chime"
                }
                ActionType.HAPTIC_PULSE -> {
                    triggerHaptic(context)
                    "Haptic Vibrate"
                }
                ActionType.LAUNCH_APP -> {
                    val pkg = mapping.targetPackageName
                    if (!pkg.isNullOrBlank()) {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                            "Launched ${mapping.targetAppName ?: pkg}"
                        } else {
                            "App not found: $pkg"
                        }
                    } else {
                        "No app configured for this gesture"
                    }
                }
                ActionType.OPEN_URL -> {
                    val urlStr = mapping.targetUrl ?: "https://www.google.com"
                    val formatted = if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
                        "https://$urlStr"
                    } else urlStr
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(formatted)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(browserIntent)
                    "Opened $formatted"
                }
            }
        } catch (e: Exception) {
            "Action error: ${e.localizedMessage ?: "Unknown"}"
        }
    }

    private fun toggleFlashlight(context: Context): String {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (cameraManager == null) return "Camera Manager unavailable"

            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }

            if (cameraId != null) {
                isTorchOn = !isTorchOn
                cameraManager.setTorchMode(cameraId, isTorchOn)
                if (isTorchOn) "Flashlight ON" else "Flashlight OFF"
            } else {
                "No flashlight found"
            }
        } catch (e: Exception) {
            "Flashlight error: ${e.message}"
        }
    }

    private fun sendMediaKeyEvent(context: Context, keyCode: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(down)
        audioManager.dispatchMediaKeyEvent(up)
    }

    @SuppressLint("WrongConstant")
    private fun expandNotifications(context: Context): String {
        return try {
            val statusBarService = context.getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val expand = statusBarManager.getMethod("expandNotificationsPanel")
            expand.invoke(statusBarService)
            "Opened Notifications"
        } catch (_: Exception) {
            "Notification shade opened"
        }
    }

    fun playBeep() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 120)
        } catch (_: Exception) {
        }
    }

    fun triggerHaptic(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                val effect = VibrationEffect.createWaveform(longArrayOf(0, 45, 60, 45), -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(longArrayOf(0, 45, 60, 45), -1)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(100)
                }
            }
        } catch (_: Exception) {
        }
    }
}
