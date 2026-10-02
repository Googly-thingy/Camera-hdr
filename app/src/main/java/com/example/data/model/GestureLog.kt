package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gesture_logs")
data class GestureLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gestureId: String,
    val actionId: String,
    val actionLabel: String,
    val latencyMs: Long,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis()
) {
    val gesture: GestureType
        get() = try {
            GestureType.valueOf(gestureId)
        } catch (_: Exception) {
            GestureType.OPEN_PALM
        }
}
