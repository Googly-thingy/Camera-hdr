package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gesture_mappings")
data class GestureMapping(
    @PrimaryKey
    val gestureId: String,
    val isEnabled: Boolean = true,
    val actionId: String,
    val targetPackageName: String? = null,
    val targetAppName: String? = null,
    val targetUrl: String? = null,
    val holdTimeMs: Long = 250L,
    val cooldownMs: Long = 1000L
) {
    val gesture: GestureType
        get() = try {
            GestureType.valueOf(gestureId)
        } catch (_: Exception) {
            GestureType.OPEN_PALM
        }

    val action: ActionType
        get() = try {
            ActionType.valueOf(actionId)
        } catch (_: Exception) {
            ActionType.MEDIA_PLAY_PAUSE
        }
}
