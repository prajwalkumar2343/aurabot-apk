package com.aura.app.dreams

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dream_runs",
    indices = [
        Index(value = ["startedAt"]),
        Index(value = ["status", "updatedAt"]),
        Index(value = ["windowStart", "windowEnd"], unique = true)
    ]
)
data class DreamRunEntity(
    @PrimaryKey val id: String,
    val status: String,
    val stage: String,
    val windowStart: Long,
    val windowEnd: Long,
    val startedAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val signalCount: Int,
    val proposalCount: Int,
    val warningCount: Int,
    val errorMessage: String?
)

@Entity(
    tableName = "dream_signals",
    indices = [
        Index(value = ["runId", "kind"]),
        Index(value = ["kind", "occurredAt"]),
        Index(value = ["runId", "fingerprint"], unique = true),
        Index(value = ["expiresAt"])
    ]
)
data class DreamSignalEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val kind: String,
    val subjectId: String,
    val fingerprint: String,
    val summary: String,
    val attributesJson: String,
    val occurredAt: Long,
    val confidence: Float,
    val expiresAt: Long
)

@Entity(
    tableName = "dream_proposals",
    indices = [
        Index(value = ["runId", "createdAt"]),
        Index(value = ["status", "createdAt"]),
        Index(value = ["fingerprint"])
    ]
)
data class DreamProposalEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val type: String,
    val status: String,
    val fingerprint: String,
    val subjectId: String,
    val title: String,
    val summary: String,
    val rationale: String,
    val confidence: Float,
    val risk: String,
    val evidenceIdsJson: String,
    val baseRevision: String?,
    val payloadJson: String,
    val applicable: Boolean,
    val validationMessage: String,
    val createdAt: Long,
    val updatedAt: Long,
    val decisionAt: Long?
)

@Entity(tableName = "dream_suppressions")
data class DreamSuppressionEntity(
    @PrimaryKey val fingerprint: String,
    val reason: String,
    val createdAt: Long,
    val expiresAt: Long?
)

@Entity(
    tableName = "dream_trace_events",
    indices = [Index(value = ["runId", "createdAt"])]
)
data class DreamTraceEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val stage: String,
    val eventType: String,
    val status: String,
    val details: String,
    val createdAt: Long
)
