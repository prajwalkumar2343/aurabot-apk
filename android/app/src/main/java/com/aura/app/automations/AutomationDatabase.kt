package com.aura.app.automations

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AutomationEntity::class,
        AutomationRunLogEntity::class,
        AutomationRunEntity::class,
        AutomationStepRunEntity::class
    ],
    version = 2,
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
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS automation_runs (
                        id TEXT NOT NULL PRIMARY KEY,
                        automationId TEXT NOT NULL,
                        eventType TEXT NOT NULL,
                        status TEXT NOT NULL,
                        message TEXT NOT NULL,
                        valuesJson TEXT NOT NULL,
                        startedAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        completedAt INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_automation_runs_automationId_updatedAt ON automation_runs(automationId, updatedAt)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_automation_runs_automationId_status ON automation_runs(automationId, status)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS automation_step_runs (
                        id TEXT NOT NULL PRIMARY KEY,
                        runId TEXT NOT NULL,
                        automationId TEXT NOT NULL,
                        stepId TEXT NOT NULL,
                        stepIndex INTEGER NOT NULL,
                        stepType TEXT NOT NULL,
                        actionType TEXT,
                        status TEXT NOT NULL,
                        attempt INTEGER NOT NULL,
                        message TEXT NOT NULL,
                        startedAt INTEGER NOT NULL,
                        completedAt INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_automation_step_runs_runId_stepIndex ON automation_step_runs(runId, stepIndex)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_automation_step_runs_automationId_stepId ON automation_step_runs(automationId, stepId)"
                )
            }
        }
    }
}
