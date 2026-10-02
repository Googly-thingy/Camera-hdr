package com.example.data.repository

import com.example.data.db.GestureLogDao
import com.example.data.db.GestureMappingDao
import com.example.data.db.GestureTrainingDao
import com.example.data.model.ActionType
import com.example.data.model.GestureLog
import com.example.data.model.GestureMapping
import com.example.data.model.GestureSignature
import com.example.data.model.GestureTrainingSample
import com.example.data.model.GestureType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class GestureRepository(
    private val mappingDao: GestureMappingDao,
    private val logDao: GestureLogDao,
    private val trainingDao: GestureTrainingDao
) {
    val allMappings: Flow<List<GestureMapping>> = mappingDao.getAllMappings()
    val enabledMappings: Flow<List<GestureMapping>> = mappingDao.getEnabledMappings()
    val recentLogs: Flow<List<GestureLog>> = logDao.getRecentLogs()
    val totalCount: Flow<Int> = logDao.getTotalCount()
    val averageLatency: Flow<Double?> = logDao.getAverageLatency()

    val allTrainingSamples: Flow<List<GestureTrainingSample>> = trainingDao.getAllSamples()
    val trainedGestureIds: Flow<List<String>> = trainingDao.getTrainedGestureIds()

    suspend fun ensureDefaultMappings() = withContext(Dispatchers.IO) {
        val existing = mappingDao.getAllMappings().firstOrNull()
        if (existing.isNullOrEmpty()) {
            val defaults = GestureType.entries.map { gesture ->
                val (defaultAction, holdTime, cooldown) = when (gesture) {
                    GestureType.OPEN_PALM -> Triple(ActionType.MEDIA_PLAY_PAUSE, 300L, 1200L)
                    GestureType.FIST -> Triple(ActionType.MUTE_TOGGLE, 350L, 1200L)
                    GestureType.THUMBS_UP -> Triple(ActionType.TOGGLE_FLASHLIGHT, 350L, 1200L)
                    GestureType.THUMBS_DOWN -> Triple(ActionType.VOLUME_DOWN, 300L, 800L)
                    GestureType.VICTORY -> Triple(ActionType.MEDIA_NEXT, 300L, 1000L)
                    GestureType.POINTING_UP -> Triple(ActionType.VOLUME_UP, 300L, 800L)
                    GestureType.POINTING_RIGHT -> Triple(ActionType.MEDIA_NEXT, 300L, 1000L)
                    GestureType.POINTING_LEFT -> Triple(ActionType.MEDIA_PREV, 300L, 1000L)
                    GestureType.OK_SIGN -> Triple(ActionType.TRIGGER_BEEP, 350L, 1000L)
                    GestureType.SWIPE_RIGHT -> Triple(ActionType.MEDIA_NEXT, 150L, 1000L)
                    GestureType.SWIPE_LEFT -> Triple(ActionType.MEDIA_PREV, 150L, 1000L)
                    GestureType.ROCK_ON -> Triple(ActionType.HAPTIC_PULSE, 350L, 1000L)
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

    suspend fun saveTrainingSample(
        gestureId: String,
        sampleIndex: Int,
        signature: GestureSignature
    ) = withContext(Dispatchers.IO) {
        val radialJson = signature.radialDistances.joinToString(",") { String.format(java.util.Locale.US, "%.3f", it) }
        val peakJson = signature.peakAngles.joinToString(",") { String.format(java.util.Locale.US, "%.3f", it) }

        trainingDao.insertSample(
            GestureTrainingSample(
                gestureId = gestureId,
                sampleIndex = sampleIndex,
                radialDistancesJson = radialJson,
                fingerCount = signature.fingerCount,
                aspectRatio = signature.aspectRatio,
                solidity = signature.solidity,
                palmRadiusRatio = signature.palmRadiusRatio,
                peakAnglesJson = peakJson
            )
        )
    }

    suspend fun deleteTrainingForGesture(gestureId: String) = withContext(Dispatchers.IO) {
        trainingDao.deleteSamplesForGesture(gestureId)
    }

    suspend fun clearAllTraining() = withContext(Dispatchers.IO) {
        trainingDao.clearAll()
    }

    suspend fun loadTrainedProfiles(): Map<String, List<GestureSignature>> = withContext(Dispatchers.IO) {
        val samples = trainingDao.getAllSamples().firstOrNull() ?: emptyList()
        val result = mutableMapOf<String, MutableList<GestureSignature>>()

        for (sample in samples) {
            try {
                val rads = sample.radialDistancesJson.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
                val peaks = sample.peakAnglesJson.split(",").mapNotNull { it.toDoubleOrNull() }
                if (rads.isNotEmpty()) {
                    val sig = GestureSignature(
                        radialDistances = rads,
                        fingerCount = sample.fingerCount,
                        aspectRatio = sample.aspectRatio,
                        solidity = sample.solidity,
                        palmRadiusRatio = sample.palmRadiusRatio,
                        peakAngles = peaks
                    )
                    result.getOrPut(sample.gestureId) { mutableListOf() }.add(sig)
                }
            } catch (_: Exception) {
            }
        }
        result
    }
}
