package com.aura.app.miniapps

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MiniAppBundleEntity::class,
        MiniAppRecordEntity::class,
        MiniAppEventEntity::class,
        MiniAppSettingEntity::class,
        MiniAppVersionEntity::class
    ],
    version = 2,
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
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `mini_app_versions` (
                        `miniAppId` TEXT NOT NULL,
                        `version` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `migrationPlanJson` TEXT NOT NULL,
                        `bundleJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`miniAppId`, `version`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mini_app_versions_miniAppId_createdAt` ON `mini_app_versions` (`miniAppId`, `createdAt`)")
            }
        }
    }
}
