package com.aura.app.miniapps

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MiniAppBundleEntity::class,
        MiniAppRecordEntity::class,
        MiniAppEventEntity::class,
        MiniAppSettingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MiniAppDatabase : RoomDatabase() {
    abstract fun miniAppDao(): MiniAppDao

    companion object {
        @Volatile private var instance: MiniAppDatabase? = null

        fun get(context: Context): MiniAppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MiniAppDatabase::class.java,
                    "aura_mini_apps.db"
                ).build().also { instance = it }
            }
    }
}
