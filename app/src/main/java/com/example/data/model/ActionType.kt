package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector

enum class ActionCategory {
    MEDIA,
    DEVICE,
    SYSTEM,
    LAUNCH
}

enum class ActionType(
    val id: String,
    val title: String,
    val description: String,
    val category: ActionCategory
) {
    TOGGLE_FLASHLIGHT(
        id = "TOGGLE_FLASHLIGHT",
        title = "Toggle Flashlight",
        description = "Turn device torch on or off",
        category = ActionCategory.DEVICE
    ),
    MEDIA_PLAY_PAUSE(
        id = "MEDIA_PLAY_PAUSE",
        title = "Media Play / Pause",
        description = "Play or pause current music or video",
        category = ActionCategory.MEDIA
    ),
    MEDIA_NEXT(
        id = "MEDIA_NEXT",
        title = "Next Track",
        description = "Skip to the next song/track",
        category = ActionCategory.MEDIA
    ),
    MEDIA_PREV(
        id = "MEDIA_PREV",
        title = "Previous Track",
        description = "Go back to previous song/track",
        category = ActionCategory.MEDIA
    ),
    VOLUME_UP(
        id = "VOLUME_UP",
        title = "Volume Up",
        description = "Increase media volume step",
        category = ActionCategory.DEVICE
    ),
    VOLUME_DOWN(
        id = "VOLUME_DOWN",
        title = "Volume Down",
        description = "Decrease media volume step",
        category = ActionCategory.DEVICE
    ),
    MUTE_TOGGLE(
        id = "MUTE_TOGGLE",
        title = "Mute / Unmute",
        description = "Toggle mute on media audio stream",
        category = ActionCategory.DEVICE
    ),
    GO_HOME(
        id = "GO_HOME",
        title = "Go to Home Screen",
        description = "Simulate home button press",
        category = ActionCategory.SYSTEM
    ),
    OPEN_NOTIFICATIONS(
        id = "OPEN_NOTIFICATIONS",
        title = "Open Notifications",
        description = "Pull down system notification shade",
        category = ActionCategory.SYSTEM
    ),
    TRIGGER_BEEP(
        id = "TRIGGER_BEEP",
        title = "Audio Beep Chime",
        description = "Play a high-pitch recognition tone",
        category = ActionCategory.DEVICE
    ),
    HAPTIC_PULSE(
        id = "HAPTIC_PULSE",
        title = "Haptic Vibration",
        description = "Trigger distinct double vibration pulse",
        category = ActionCategory.DEVICE
    ),
    LAUNCH_APP(
        id = "LAUNCH_APP",
        title = "Launch Application",
        description = "Open a customized user-chosen app",
        category = ActionCategory.LAUNCH
    ),
    OPEN_URL(
        id = "OPEN_URL",
        title = "Open Web URL",
        description = "Open a specific website in browser",
        category = ActionCategory.LAUNCH
    );

    fun getIcon(): ImageVector {
        return when (this) {
            TOGGLE_FLASHLIGHT -> Icons.Default.FlashlightOn
            MEDIA_PLAY_PAUSE -> Icons.Default.PlayArrow
            MEDIA_NEXT -> Icons.Default.SkipNext
            MEDIA_PREV -> Icons.Default.SkipPrevious
            VOLUME_UP -> Icons.AutoMirrored.Filled.VolumeUp
            VOLUME_DOWN -> Icons.AutoMirrored.Filled.VolumeDown
            MUTE_TOGGLE -> Icons.AutoMirrored.Filled.VolumeMute
            GO_HOME -> Icons.Default.Home
            OPEN_NOTIFICATIONS -> Icons.Default.Notifications
            TRIGGER_BEEP -> Icons.Default.VolumeUp
            HAPTIC_PULSE -> Icons.Default.Vibration
            LAUNCH_APP -> Icons.Default.Apps
            OPEN_URL -> Icons.Default.Language
        }
    }
}
