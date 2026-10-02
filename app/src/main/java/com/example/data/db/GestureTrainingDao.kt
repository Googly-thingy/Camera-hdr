package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.GestureTrainingSample
import kotlinx.coroutines.flow.Flow

@Dao
interface GestureTrainingDao {

    @Query("SELECT * FROM gesture_training_samples ORDER BY timestamp ASC")
    fun getAllSamples(): Flow<List<GestureTrainingSample>>

    @Query("SELECT * FROM gesture_training_samples WHERE gestureId = :gestureId ORDER BY timestamp ASC")
    fun getSamplesForGesture(gestureId: String): Flow<List<GestureTrainingSample>>

    @Query("SELECT DISTINCT gestureId FROM gesture_training_samples")
    fun getTrainedGestureIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: GestureTrainingSample)

    @Query("DELETE FROM gesture_training_samples WHERE gestureId = :gestureId")
    suspend fun deleteSamplesForGesture(gestureId: String)

    @Query("DELETE FROM gesture_training_samples")
    suspend fun clearAll()
}
