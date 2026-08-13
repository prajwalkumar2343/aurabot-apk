package com.aura.app.assistant

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
    tableName = "assistant_run_surfaces",
    indices = [Index(value = ["state", "updatedAt"])]
)
data class AssistantRunSurfaceEntity(
    @PrimaryKey val runId: String,
    val mode: String,
    val state: String,
    val phase: String,
    val activeSubagents: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastError: String?
)

@Dao
interface AssistantRunDao {
    @Query("SELECT * FROM assistant_run_surfaces WHERE runId = :runId LIMIT 1")
    suspend fun surface(runId: String): AssistantRunSurfaceEntity?

    @Query(
        """
        SELECT * FROM assistant_run_surfaces
        WHERE state IN ('queued', 'running')
        ORDER BY updatedAt ASC
        """
    )
    suspend fun activeSurfaces(): List<AssistantRunSurfaceEntity>

    @Query("SELECT * FROM assistant_run_surfaces WHERE mode = :mode")
    suspend fun surfacesForMode(mode: String): List<AssistantRunSurfaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AssistantRunSurfaceEntity)

    @Query(
        """
        DELETE FROM assistant_run_surfaces
        WHERE state IN ('completed', 'failed', 'interrupted', 'cancelled')
          AND updatedAt < :cutoff
        """
    )
    suspend fun deleteTerminalBefore(cutoff: Long): Int
}
