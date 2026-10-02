package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ActionType
import com.example.data.model.GestureLog
import com.example.data.model.GestureMapping
import com.example.data.model.GestureTrainingSample
import com.example.data.model.GestureType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [GestureMapping::class, GestureLog::class, GestureTrainingSample::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun gestureMappingDao(): GestureMappingDao
    abstract fun gestureLogDao(): GestureLogDao
    abstract fun gestureTrainingDao(): GestureTrainingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gesture_flow_db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialMappings(database.gestureMappingDao())
                    }
                }
            }

            private suspend fun populateInitialMappings(dao: GestureMappingDao) {
                val defaults = listOf(
                    GestureMapping(
                        gestureId = GestureType.OPEN_PALM.name,
                        isEnabled = true,
                        actionId = ActionType.MEDIA_PLAY_PAUSE.name,
                        holdTimeMs = 300L,
                        cooldownMs = 1200L
                    ),
                    GestureMapping(
                        gestureId = GestureType.FIST.name,
                        isEnabled = true,
                        actionId = ActionType.MUTE_TOGGLE.name,
                        holdTimeMs = 350L,
                        cooldownMs = 1200L
                    ),
                    GestureMapping(
                        gestureId = GestureType.THUMBS_UP.name,
                        isEnabled = true,
                        actionId = ActionType.TOGGLE_FLASHLIGHT.name,
                        holdTimeMs = 350L,
                        cooldownMs = 1200L
                    ),
                    GestureMapping(
                        gestureId = GestureType.THUMBS_DOWN.name,
                        isEnabled = true,
                        actionId = ActionType.VOLUME_DOWN.name,
                        holdTimeMs = 300L,
                        cooldownMs = 800L
                    ),
                    GestureMapping(
                        gestureId = GestureType.VICTORY.name,
                        isEnabled = true,
                        actionId = ActionType.MEDIA_NEXT.name,
                        holdTimeMs = 300L,
                        cooldownMs = 1000L
                    ),
                    GestureMapping(
                        gestureId = GestureType.POINTING_UP.name,
                        isEnabled = true,
                        actionId = ActionType.VOLUME_UP.name,
                        holdTimeMs = 300L,
                        cooldownMs = 800L
                    ),
                    GestureMapping(
                        gestureId = GestureType.POINTING_RIGHT.name,
                        isEnabled = true,
                        actionId = ActionType.MEDIA_NEXT.name,
                        holdTimeMs = 300L,
                        cooldownMs = 1000L
                    ),
                    GestureMapping(
                        gestureId = GestureType.POINTING_LEFT.name,
                        isEnabled = true,
                        actionId = ActionType.MEDIA_PREV.name,
                        holdTimeMs = 300L,
                        cooldownMs = 1000L
                    ),
                    GestureMapping(
                        gestureId = GestureType.OK_SIGN.name,
                        isEnabled = true,
                        actionId = ActionType.TRIGGER_BEEP.name,
                        holdTimeMs = 350L,
                        cooldownMs = 1000L
                    ),
                    GestureMapping(
                        gestureId = GestureType.SWIPE_RIGHT.name,
                        isEnabled = true,
                        actionId = ActionType.MEDIA_NEXT.name,
                        holdTimeMs = 150L,
                        cooldownMs = 1000L
                    ),
                    GestureMapping(
                        gestureId = GestureType.SWIPE_LEFT.name,
                        isEnabled = true,
                        actionId = ActionType.MEDIA_PREV.name,
                        holdTimeMs = 150L,
                        cooldownMs = 1000L
                    ),
                    GestureMapping(
                        gestureId = GestureType.ROCK_ON.name,
                        isEnabled = true,
                        actionId = ActionType.HAPTIC_PULSE.name,
                        holdTimeMs = 350L,
                        cooldownMs = 1000L
                    )
                )
                dao.insertAll(defaults)
            }
        }
    }
}
