package com.aura.app.automations

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

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

    @Query("DELETE FROM automation_run_logs WHERE automationId = :automationId")
    suspend fun deleteRunLogs(automationId: String)

    @Query("DELETE FROM automation_step_runs WHERE automationId = :automationId")
    suspend fun deleteStepRuns(automationId: String)

    @Query("DELETE FROM automation_runs WHERE automationId = :automationId")
    suspend fun deleteRuns(automationId: String)

    @Query("DELETE FROM automation_events WHERE automationId = :automationId")
    suspend fun deleteEvents(automationId: String)

    @Transaction
    suspend fun deleteAutomationData(automationId: String) {
        deleteStepRuns(automationId)
        deleteRuns(automationId)
        deleteRunLogs(automationId)
        deleteEvents(automationId)
        deleteAutomation(automationId)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(entity: AutomationEventEntity): Long

    @Query("SELECT * FROM automation_events WHERE deliveryId = :deliveryId LIMIT 1")
    suspend fun event(deliveryId: String): AutomationEventEntity?

    @Query(
        "UPDATE automation_events SET status = 'running', message = :message, updatedAt = :updatedAt " +
            "WHERE deliveryId = :deliveryId AND status = 'queued'"
    )
    suspend fun claimEvent(deliveryId: String, message: String, updatedAt: Long): Int

    @Query(
        "UPDATE automation_events SET status = :status, message = :message, updatedAt = :updatedAt " +
            "WHERE deliveryId = :deliveryId"
    )
    suspend fun settleEvent(deliveryId: String, status: String, message: String, updatedAt: Long)

    @Query("SELECT deliveryId FROM automation_events WHERE status = 'running'")
    suspend fun runningEventIds(): List<String>

    @Query(
        """
        DELETE FROM automation_events
        WHERE automationId = :automationId AND status IN ('succeeded', 'failed') AND deliveryId IN (
            SELECT deliveryId FROM automation_events
            WHERE automationId = :automationId AND status IN ('succeeded', 'failed')
            ORDER BY updatedAt DESC, deliveryId DESC
            LIMIT -1 OFFSET :retainCount
        )
        """
    )
    suspend fun pruneEvents(automationId: String, retainCount: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRunLog(entity: AutomationRunLogEntity)

    @Query("SELECT * FROM automation_run_logs WHERE automationId = :automationId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun runLogs(automationId: String, limit: Int = 50): List<AutomationRunLogEntity>

    @Query(
        """
        DELETE FROM automation_run_logs
        WHERE automationId = :automationId AND id IN (
            SELECT id FROM automation_run_logs
            WHERE automationId = :automationId
            ORDER BY createdAt DESC, id DESC
            LIMIT -1 OFFSET :retainCount
        )
        """
    )
    suspend fun pruneRunLogs(automationId: String, retainCount: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRun(entity: AutomationRunEntity)

    @Query("SELECT * FROM automation_runs WHERE id = :id LIMIT 1")
    suspend fun run(id: String): AutomationRunEntity?

    @Query("SELECT * FROM automation_runs WHERE automationId = :automationId AND status IN ('running', 'waiting') ORDER BY updatedAt DESC LIMIT 1")
    suspend fun activeRun(automationId: String): AutomationRunEntity?

    @Query("SELECT * FROM automation_runs WHERE status IN ('running', 'waiting') ORDER BY updatedAt DESC")
    suspend fun activeRuns(): List<AutomationRunEntity>

    @Query("SELECT * FROM automation_runs WHERE automationId = :automationId AND status IN ('running', 'waiting') ORDER BY updatedAt DESC")
    suspend fun activeRuns(automationId: String): List<AutomationRunEntity>

    @Query("SELECT * FROM automation_runs WHERE automationId = :automationId ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun runs(automationId: String, limit: Int = 20): List<AutomationRunEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStepRun(entity: AutomationStepRunEntity)

    @Query("SELECT * FROM automation_step_runs WHERE runId = :runId ORDER BY stepIndex ASC, attempt ASC")
    suspend fun stepRuns(runId: String): List<AutomationStepRunEntity>

    @Query(
        """
        DELETE FROM automation_step_runs
        WHERE runId IN (
            SELECT id FROM automation_runs
            WHERE automationId = :automationId AND status NOT IN ('running', 'waiting')
            ORDER BY updatedAt DESC, id DESC
            LIMIT -1 OFFSET :retainCount
        )
        """
    )
    suspend fun pruneStepRuns(automationId: String, retainCount: Int)

    @Query(
        """
        DELETE FROM automation_runs
        WHERE automationId = :automationId AND status NOT IN ('running', 'waiting') AND id IN (
            SELECT id FROM automation_runs
            WHERE automationId = :automationId AND status NOT IN ('running', 'waiting')
            ORDER BY updatedAt DESC, id DESC
            LIMIT -1 OFFSET :retainCount
        )
        """
    )
    suspend fun pruneRuns(automationId: String, retainCount: Int)

    @Transaction
    suspend fun pruneHistory(automationId: String, runRetainCount: Int, logRetainCount: Int) {
        pruneStepRuns(automationId, runRetainCount)
        pruneRuns(automationId, runRetainCount)
        pruneRunLogs(automationId, logRetainCount)
    }
}
