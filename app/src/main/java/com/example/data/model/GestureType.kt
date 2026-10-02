package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.ui.graphics.vector.ImageVector

enum class GestureType(
    val id: String,
    val title: String,
    val description: String,
    val iconName: String
) {
    OPEN_PALM(
        id = "OPEN_PALM",
        title = "Open Palm",
        description = "Show all 5 fingers open facing camera",
        iconName = "FrontHand"
    ),
    FIST(
        id = "FIST",
        title = "Closed Fist",
        description = "Clench all fingers tightly into a ball",
        iconName = "SportsKabaddi"
    ),
    THUMBS_UP(
        id = "THUMBS_UP",
        title = "Thumbs Up",
        description = "Thumb pointed upward, other fingers folded",
        iconName = "ThumbUp"
    ),
    THUMBS_DOWN(
        id = "THUMBS_DOWN",
        title = "Thumbs Down",
        description = "Thumb pointed downward, other fingers folded",
        iconName = "ThumbDown"
    ),
    VICTORY(
        id = "VICTORY",
        title = "Peace / Victory",
        description = "Index and middle fingers extended in a V",
        iconName = "PanTool"
    ),
    POINTING_UP(
        id = "POINTING_UP",
        title = "Pointing Up",
        description = "Index finger extended up, others closed",
        iconName = "Navigation"
    ),
    POINTING_RIGHT(
        id = "POINTING_RIGHT",
        title = "Point Right",
        description = "Index finger pointing towards right",
        iconName = "ArrowForward"
    ),
    POINTING_LEFT(
        id = "POINTING_LEFT",
        title = "Point Left",
        description = "Index finger pointing towards left",
        iconName = "ArrowBack"
    ),
    OK_SIGN(
        id = "OK_SIGN",
        title = "OK Sign",
        description = "Index and thumb touching, 3 fingers extended",
        iconName = "CheckCircle"
    ),
    SWIPE_RIGHT(
        id = "SWIPE_RIGHT",
        title = "Swipe Right",
        description = "Wave hand across screen from left to right",
        iconName = "ArrowForward"
    ),
    SWIPE_LEFT(
        id = "SWIPE_LEFT",
        title = "Swipe Left",
        description = "Wave hand across screen from right to left",
        iconName = "ArrowBack"
    ),
    ROCK_ON(
        id = "ROCK_ON",
        title = "Rock On / Horns",
        description = "Index and pinky fingers extended",
        iconName = "WavingHand"
    );

    fun getIcon(): ImageVector {
        return when (this) {
            OPEN_PALM -> Icons.Default.FrontHand
            FIST -> Icons.Default.SportsKabaddi
            THUMBS_UP -> Icons.Default.ThumbUp
            THUMBS_DOWN -> Icons.Default.ThumbDown
            VICTORY -> Icons.Default.PanTool
            POINTING_UP -> Icons.Default.Navigation
            POINTING_RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
            POINTING_LEFT -> Icons.AutoMirrored.Filled.ArrowBack
            OK_SIGN -> Icons.Default.CheckCircle
            SWIPE_RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
            SWIPE_LEFT -> Icons.AutoMirrored.Filled.ArrowBack
            ROCK_ON -> Icons.Default.WavingHand
        }
    }
}
