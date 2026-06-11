package com.aura.app.automations

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AutomationEntity::class,
        AutomationRunLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AutomationDatabase : RoomDatabase() {
    abstract fun automationDao(): AutomationDao

    companion object {
        @Volatile private var instance: AutomationDatabase? = null

        fun get(context: Context): AutomationDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AutomationDatabase::class.java,
                    "aura_automations.db"
                ).build().also { instance = it }
            }
    }
}
