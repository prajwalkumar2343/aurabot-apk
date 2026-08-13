package com.aura.app.widgets

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aura.app.assistant.AssistantRunSurfaceEntity
import com.aura.app.assistant.AssistantRunDao

@Database(
    entities = [
        AuraWidgetEntity::class,
        AuraWidgetEventEntity::class,
        HostedAndroidWidgetEntity::class,
        AssistantRunSurfaceEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AuraWidgetDatabase : RoomDatabase() {
    abstract fun auraWidgetDao(): AuraWidgetDao
    abstract fun assistantRunDao(): AssistantRunDao

    companion object {
        @Volatile private var instance: AuraWidgetDatabase? = null

        fun get(context: Context): AuraWidgetDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AuraWidgetDatabase::class.java,
                    "aura_widgets.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE aura_widgets ADD COLUMN presentation TEXT NOT NULL DEFAULT 'compact'")
                db.execSQL("ALTER TABLE aura_widgets ADD COLUMN contentFormat TEXT NOT NULL DEFAULT 'plain_text'")
                db.execSQL("ALTER TABLE aura_widgets ADD COLUMN content TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE aura_widgets ADD COLUMN assistantRunId TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS assistant_run_surfaces (
                        runId TEXT NOT NULL,
                        mode TEXT NOT NULL,
                        state TEXT NOT NULL,
                        phase TEXT NOT NULL,
                        activeSubagents INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        lastError TEXT,
                        PRIMARY KEY(runId)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_assistant_run_surfaces_state_updatedAt " +
                        "ON assistant_run_surfaces(state, updatedAt)"
                )
            }
        }
    }
}
