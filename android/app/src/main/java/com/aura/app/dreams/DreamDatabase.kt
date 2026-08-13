package com.aura.app.dreams

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DreamRunEntity::class,
        DreamSignalEntity::class,
        DreamProposalEntity::class,
        DreamSuppressionEntity::class,
        DreamTraceEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DreamDatabase : RoomDatabase() {
    abstract fun dreamDao(): DreamDao

    companion object {
        @Volatile private var instance: DreamDatabase? = null

        fun get(context: Context): DreamDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DreamDatabase::class.java,
                    "aura_dreams.db"
                ).build().also { instance = it }
            }
    }
}
