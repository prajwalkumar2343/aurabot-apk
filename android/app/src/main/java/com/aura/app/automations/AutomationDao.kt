package com.aura.app.automations

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automations ORDER BY updatedAt DESC")
    suspend fun listAutomations(): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE enabled = 1 ORDER BY updatedAt DESC")
    suspend fun listEnabledAutomations(): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE id = :id LIMIT 1")
    suspend fun automation(id: String): AutomationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAutomation(entity: AutomationEntity)

    @Query("UPDATE automations SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long)

    @Query("UPDATE automations SET lastTriggeredAt = :triggeredAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markTriggered(id: String, triggeredAt: Long, updatedAt: Long)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun deleteAutomation(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRunLog(entity: AutomationRunLogEntity)

    @Query("SELECT * FROM automation_run_logs WHERE automationId = :automationId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun runLogs(automationId: String, limit: Int = 50): List<AutomationRunLogEntity>
}
