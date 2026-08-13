package com.aura.app.dreams

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DreamDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRun(entity: DreamRunEntity): Long

    @Query("SELECT * FROM dream_runs WHERE windowStart = :start AND windowEnd = :end LIMIT 1")
    suspend fun runForWindow(start: Long, end: Long): DreamRunEntity?

    @Query("SELECT * FROM dream_runs WHERE id = :id LIMIT 1")
    suspend fun run(id: String): DreamRunEntity?

    @Query("SELECT * FROM dream_runs ORDER BY startedAt DESC LIMIT 1")
    fun observeLatestRun(): Flow<DreamRunEntity?>

    @Query("SELECT * FROM dream_runs ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestRun(): DreamRunEntity?

    @Query("SELECT * FROM dream_runs WHERE status = 'Running' ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeRun(): DreamRunEntity?

    @Query(
        """
        UPDATE dream_runs
        SET stage = :stage, updatedAt = :updatedAt, signalCount = :signalCount,
            proposalCount = :proposalCount, warningCount = :warningCount
        WHERE id = :id AND status = 'Running'
        """
    )
    suspend fun advanceRun(
        id: String,
        stage: String,
        updatedAt: Long,
        signalCount: Int,
        proposalCount: Int,
        warningCount: Int
    ): Int

    @Query(
        """
        UPDATE dream_runs
        SET status = :status, stage = :stage, updatedAt = :updatedAt,
            completedAt = :completedAt, signalCount = :signalCount,
            proposalCount = :proposalCount, warningCount = :warningCount,
            errorMessage = :errorMessage
        WHERE id = :id
        """
    )
    suspend fun finishRun(
        id: String,
        status: String,
        stage: String,
        updatedAt: Long,
        completedAt: Long,
        signalCount: Int,
        proposalCount: Int,
        warningCount: Int,
        errorMessage: String?
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSignals(entities: List<DreamSignalEntity>): List<Long>

    @Query("SELECT * FROM dream_signals WHERE runId = :runId ORDER BY occurredAt ASC LIMIT :limit")
    suspend fun signalsForRun(runId: String, limit: Int): List<DreamSignalEntity>

    @Query("SELECT * FROM dream_signals WHERE id IN (:ids)")
    suspend fun signals(ids: List<String>): List<DreamSignalEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProposals(entities: List<DreamProposalEntity>): List<Long>

    @Query("SELECT * FROM dream_proposals WHERE id = :id LIMIT 1")
    suspend fun proposal(id: String): DreamProposalEntity?

    @Query("SELECT * FROM dream_proposals WHERE runId = :runId ORDER BY confidence DESC, createdAt ASC")
    suspend fun proposalsForRun(runId: String): List<DreamProposalEntity>

    @Query(
        """
        SELECT * FROM dream_proposals
        WHERE status IN ('PendingReview', 'Snoozed', 'Failed', 'Stale', 'ReconciliationRequired')
        ORDER BY createdAt DESC
        """
    )
    fun observeReviewableProposals(): Flow<List<DreamProposalEntity>>

    @Query(
        """
        SELECT * FROM dream_proposals
        WHERE fingerprint = :fingerprint AND status IN ('PendingReview', 'Applying', 'Snoozed', 'Applied')
        ORDER BY createdAt DESC LIMIT 1
        """
    )
    suspend fun activeProposalForFingerprint(fingerprint: String): DreamProposalEntity?

    @Query(
        """
        UPDATE dream_proposals
        SET status = 'Applying', updatedAt = :updatedAt
        WHERE id = :id AND status = 'PendingReview' AND applicable = 1
        """
    )
    suspend fun claimProposal(id: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE dream_proposals
        SET status = :status, updatedAt = :updatedAt, decisionAt = :decisionAt,
            validationMessage = :message
        WHERE id = :id AND status IN (:allowedStatuses)
        """
    )
    suspend fun transitionProposal(
        id: String,
        status: String,
        updatedAt: Long,
        decisionAt: Long?,
        message: String,
        allowedStatuses: List<String>
    ): Int

    @Query(
        """
        UPDATE dream_proposals
        SET status = 'ReconciliationRequired', updatedAt = :updatedAt,
            validationMessage = 'Application was interrupted. Aura will not replay it automatically.'
        WHERE status = 'Applying'
        """
    )
    suspend fun recoverInterruptedApplications(updatedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSuppression(entity: DreamSuppressionEntity)

    @Query("SELECT * FROM dream_suppressions WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun suppression(fingerprint: String): DreamSuppressionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrace(entity: DreamTraceEntity)

    @Query("DELETE FROM dream_signals WHERE expiresAt < :now AND id NOT IN (SELECT id FROM dream_signals WHERE runId IN (SELECT runId FROM dream_proposals WHERE status IN ('PendingReview', 'Applying', 'Snoozed')))")
    suspend fun pruneSignals(now: Long)

    @Query("DELETE FROM dream_suppressions WHERE expiresAt IS NOT NULL AND expiresAt < :now")
    suspend fun pruneSuppressions(now: Long)

    @Query("DELETE FROM dream_trace_events")
    suspend fun deleteTraces()

    @Query("DELETE FROM dream_proposals")
    suspend fun deleteProposals()

    @Query("DELETE FROM dream_signals")
    suspend fun deleteSignals()

    @Query("DELETE FROM dream_runs")
    suspend fun deleteRuns()

    @Query("DELETE FROM dream_suppressions")
    suspend fun deleteSuppressions()

    @Transaction
    suspend fun deleteAllDreamData() {
        deleteTraces()
        deleteProposals()
        deleteSignals()
        deleteRuns()
        deleteSuppressions()
    }
}
