package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.GestureLog
import com.example.data.model.GestureMapping
import kotlinx.coroutines.flow.Flow

@Dao
interface GestureMappingDao {

    @Query("SELECT * FROM gesture_mappings")
    fun getAllMappings(): Flow<List<GestureMapping>>

    @Query("SELECT * FROM gesture_mappings WHERE isEnabled = 1")
    fun getEnabledMappings(): Flow<List<GestureMapping>>

    @Query("SELECT * FROM gesture_mappings WHERE gestureId = :gestureId LIMIT 1")
    suspend fun getMappingById(gestureId: String): GestureMapping?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(mapping: GestureMapping)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mappings: List<GestureMapping>)

    @Update
    suspend fun update(mapping: GestureMapping)
}

@Dao
interface GestureLogDao {

    @Query("SELECT * FROM gesture_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogs(): Flow<List<GestureLog>>

    @Query("SELECT COUNT(*) FROM gesture_logs")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT AVG(latencyMs) FROM gesture_logs")
    fun getAverageLatency(): Flow<Double?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: GestureLog)

    @Query("DELETE FROM gesture_logs")
    suspend fun clearAll()
}
