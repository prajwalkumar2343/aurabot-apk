package com.aura.app.automations

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import java.util.UUID

class AutomationRepository(
    private val dao: AutomationDao,
    private val gson: Gson = Gson(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val runHistoryLimit: Int = DefaultRunHistoryLimit,
    private val logHistoryLimit: Int = DefaultLogHistoryLimit,
    private val maintenanceFailureReporter: (String, Exception) -> Unit = { message, error ->
        Log.e(TAG, message, error)
    }
) {
    private val stringMapType = object : TypeToken<Map<String, String>>() {}.type

    suspend fun list(): List<AutomationSpec> =
        dao.listAutomations().mapNotNull { it.spec() }

    suspend fun listEnabled(): List<AutomationSpec> =
        dao.listEnabledAutomations().mapNotNull { it.spec() }

    suspend fun get(id: String): AutomationSpec? =
        dao.automation(id)?.spec()

    suspend fun upsert(spec: AutomationSpec): AutomationSpec {
        val now = clock()
        val valid = AutomationValidator.validate(spec)
        val existing = valid.id.takeIf { it.isNotBlank() }?.let { dao.automation(it) }
        val normalized = valid.copy(
            id = spec.id.ifBlank { UUID.randomUUID().toString() },
            createdAt = existing?.createdAt ?: valid.createdAt.takeIf { it > 0L } ?: now,
            updatedAt = now
        )
        dao.upsertAutomation(normalized.entity(existing?.lastTriggeredAt))
        return normalized
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        dao.setEnabled(id, enabled, clock())
    }

    suspend fun delete(id: String) {
        dao.deleteAutomationData(id)
    }

    suspend fun markTriggered(id: String) {
        val now = clock()
        dao.markTriggered(id, now, now)
    }

    suspend fun lastTriggeredAt(id: String): Long? =
        dao.automation(id)?.lastTriggeredAt

    suspend fun log(
        automationId: String,
        eventType: String,
        status: String,
        message: String
    ) {
        dao.insertRunLog(
            AutomationRunLogEntity(
                id = UUID.randomUUID().toString(),
                automationId = automationId,
                eventType = eventType,
                status = status,
                message = message,
                createdAt = clock()
            )
        )
        pruneHistoryBestEffort(automationId)
    }

    suspend fun logs(automationId: String, limit: Int = 50): List<AutomationRunLog> =
        dao.runLogs(automationId, limit).map {
            AutomationRunLog(
                id = it.id,
                automationId = it.automationId,
                eventType = it.eventType,
                status = it.status,
                message = it.message,
                createdAt = it.createdAt
            )
        }

    suspend fun createRun(
        automationId: String,
        eventType: String,
        values: Map<String, String>,
        status: String = AutomationRunStatus.Running,
        message: String = "Automation flow started"
    ): AutomationRunRecord {
        val now = clock()
        val automationRevision = dao.automation(automationId)
            ?.spec()
            ?.let(::revision)
            .orEmpty()
        val entity = AutomationRunEntity(
            id = UUID.randomUUID().toString(),
            automationId = automationId,
            eventType = eventType,
            status = status,
            message = message,
            valuesJson = gson.toJson(values),
            automationRevision = automationRevision,
            startedAt = now,
            updatedAt = now,
            completedAt = null
        )
        dao.upsertRun(entity)
        return entity.record()
    }

    suspend fun updateRun(
        runId: String,
        status: String,
        message: String,
        values: Map<String, String>? = null,
        completed: Boolean = status in terminalStatuses
    ) {
        val existing = dao.run(runId) ?: return
        val now = clock()
        dao.upsertRun(
            existing.copy(
                status = status,
                message = message,
                valuesJson = values?.let { gson.toJson(it) } ?: existing.valuesJson,
                updatedAt = now,
                completedAt = if (completed) now else existing.completedAt
            )
        )
        if (completed) pruneHistoryBestEffort(existing.automationId)
    }

    suspend fun getRun(id: String): AutomationRunRecord? =
        dao.run(id)?.record()

    suspend fun activeRun(automationId: String): AutomationRunRecord? =
        dao.activeRun(automationId)?.record()

    suspend fun activeRuns(): List<AutomationRunRecord> =
        dao.activeRuns().map { it.record() }

    suspend fun activeRuns(automationId: String): List<AutomationRunRecord> =
        dao.activeRuns(automationId).map { it.record() }

    suspend fun runs(automationId: String, limit: Int = 20): List<AutomationRunRecord> =
        dao.runs(automationId, limit).map { it.record() }

    suspend fun recordStep(
        runId: String,
        automationId: String,
        step: AutomationFlowStep,
        stepIndex: Int,
        status: String,
        attempt: Int,
        message: String
    ): AutomationStepRunRecord {
        val now = clock()
        val entity = AutomationStepRunEntity(
            id = UUID.randomUUID().toString(),
            runId = runId,
            automationId = automationId,
            stepId = step.id,
            stepIndex = stepIndex,
            stepType = step.type,
            actionType = step.action?.type,
            status = status,
            attempt = attempt,
            message = message,
            startedAt = now,
            completedAt = now
        )
        dao.insertStepRun(entity)
        return entity.record()
    }

    suspend fun stepRuns(runId: String): List<AutomationStepRunRecord> =
        dao.stepRuns(runId).map { it.record() }

    private suspend fun pruneHistory(automationId: String) {
        dao.pruneHistory(
            automationId = automationId,
            runRetainCount = runHistoryLimit.coerceAtLeast(0),
            logRetainCount = logHistoryLimit.coerceAtLeast(0)
        )
    }

    private suspend fun pruneHistoryBestEffort(automationId: String) {
        try {
            pruneHistory(automationId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            try {
                maintenanceFailureReporter("Failed to prune history for automation '$automationId'", error)
            } catch (_: Exception) {
                // Retention diagnostics must not change an already-persisted run outcome.
            }
        }
    }

    internal fun revision(spec: AutomationSpec): String {
        val revisionSource = gson.toJson(spec.copy(createdAt = 0L, updatedAt = 0L))
        return MessageDigest.getInstance("SHA-256")
            .digest(revisionSource.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private fun AutomationSpec.entity(lastTriggeredAt: Long?) = AutomationEntity(
        id = id,
        name = name,
        description = description,
        enabled = enabled,
        specJson = gson.toJson(this),
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastTriggeredAt = lastTriggeredAt
    )

    private fun AutomationEntity.spec(): AutomationSpec? =
        runCatching { gson.fromJson(specJson, AutomationSpec::class.java) }
            .getOrNull()
            ?.copy(
                id = id,
                name = name,
                description = description,
                enabled = enabled,
                createdAt = createdAt,
                updatedAt = updatedAt
            )

    private fun AutomationRunEntity.record() = AutomationRunRecord(
        id = id,
        automationId = automationId,
        eventType = eventType,
        status = status,
        message = message,
        values = runCatching {
            gson.fromJson<Map<String, String>>(valuesJson, stringMapType)
        }.getOrNull().orEmpty(),
        automationRevision = automationRevision,
        startedAt = startedAt,
        updatedAt = updatedAt,
        completedAt = completedAt
    )

    private fun AutomationStepRunEntity.record() = AutomationStepRunRecord(
        id = id,
        runId = runId,
        automationId = automationId,
        stepId = stepId,
        stepIndex = stepIndex,
        stepType = stepType,
        actionType = actionType,
        status = status,
        attempt = attempt,
        message = message,
        startedAt = startedAt,
        completedAt = completedAt
    )

    private companion object {
        const val TAG = "AutomationRepository"
        const val DefaultRunHistoryLimit = 100
        const val DefaultLogHistoryLimit = 200

        val terminalStatuses = setOf(
            AutomationRunStatus.Success,
            AutomationRunStatus.Skipped,
            AutomationRunStatus.Failed
        )
    }
}
