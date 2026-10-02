package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gesture_training_samples")
data class GestureTrainingSample(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gestureId: String,
    val sampleIndex: Int,
    val radialDistancesJson: String, // Comma-separated normalized radial distances
    val fingerCount: Int,
    val aspectRatio: Float,
    val solidity: Float,
    val palmRadiusRatio: Float,
    val peakAnglesJson: String, // Comma-separated peak angles
    val timestamp: Long = System.currentTimeMillis()
)
