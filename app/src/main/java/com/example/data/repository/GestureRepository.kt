package com.example.data.repository

import com.example.data.db.GestureLogDao
import com.example.data.db.GestureMappingDao
import com.example.data.model.ActionType
import com.example.data.model.GestureLog
import com.example.data.model.GestureMapping
import com.example.data.model.GestureType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class GestureRepository(
    private val mappingDao: GestureMappingDao,
    private val logDao: GestureLogDao
) {
    val allMappings: Flow<List<GestureMapping>> = mappingDao.getAllMappings()
    val enabledMappings: Flow<List<GestureMapping>> = mappingDao.getEnabledMappings()
    val recentLogs: Flow<List<GestureLog>> = logDao.getRecentLogs()
    val totalCount: Flow<Int> = logDao.getTotalCount()
    val averageLatency: Flow<Double?> = logDao.getAverageLatency()

    suspend fun ensureDefaultMappings() = withContext(Dispatchers.IO) {
        val existing = mappingDao.getAllMappings().firstOrNull()
        if (existing.isNullOrEmpty()) {
            val defaults = GestureType.entries.map { gesture ->
                val (defaultAction, holdTime, cooldown) = when (gesture) {
                    GestureType.OPEN_PALM -> Triple(ActionType.MEDIA_PLAY_PAUSE, 250L, 1200L)
                    GestureType.FIST -> Triple(ActionType.MUTE_TOGGLE, 300L, 1200L)
                    GestureType.THUMBS_UP -> Triple(ActionType.TOGGLE_FLASHLIGHT, 300L, 1200L)
                    GestureType.THUMBS_DOWN -> Triple(ActionType.VOLUME_DOWN, 250L, 800L)
                    GestureType.VICTORY -> Triple(ActionType.MEDIA_NEXT, 250L, 1000L)
                    GestureType.POINTING_UP -> Triple(ActionType.VOLUME_UP, 250L, 800L)
                    GestureType.POINTING_RIGHT -> Triple(ActionType.MEDIA_NEXT, 250L, 1000L)
                    GestureType.POINTING_LEFT -> Triple(ActionType.MEDIA_PREV, 250L, 1000L)
                    GestureType.OK_SIGN -> Triple(ActionType.TRIGGER_BEEP, 300L, 1000L)
                    GestureType.SWIPE_RIGHT -> Triple(ActionType.MEDIA_NEXT, 150L, 1000L)
                    GestureType.SWIPE_LEFT -> Triple(ActionType.MEDIA_PREV, 150L, 1000L)
                    GestureType.ROCK_ON -> Triple(ActionType.HAPTIC_PULSE, 300L, 1000L)
                }
                GestureMapping(
                    gestureId = gesture.name,
                    isEnabled = true,
                    actionId = defaultAction.name,
                    holdTimeMs = holdTime,
                    cooldownMs = cooldown
                )
            }
            mappingDao.insertAll(defaults)
        }
    }

    suspend fun updateMapping(mapping: GestureMapping) = withContext(Dispatchers.IO) {
        mappingDao.insertOrUpdate(mapping)
    }

    suspend fun toggleMapping(gestureId: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        val current = mappingDao.getMappingById(gestureId)
        if (current != null) {
            mappingDao.update(current.copy(isEnabled = isEnabled))
        }
    }

    suspend fun logExecution(
        gesture: GestureType,
        action: ActionType,
        actionLabel: String,
        latencyMs: Long,
        confidence: Float
    ) = withContext(Dispatchers.IO) {
        logDao.insertLog(
            GestureLog(
                gestureId = gesture.name,
                actionId = action.name,
                actionLabel = actionLabel,
                latencyMs = latencyMs,
                confidence = confidence
            )
        )
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        logDao.clearAll()
    }
}
