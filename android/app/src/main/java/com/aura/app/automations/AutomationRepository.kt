package com.aura.app.automations

import com.google.gson.Gson
import java.util.UUID

class AutomationRepository(
    private val dao: AutomationDao,
    private val gson: Gson = Gson(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
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
        dao.deleteAutomation(id)
    }

    suspend fun markTriggered(id: String, triggeredAt: Long) {
        dao.markTriggered(id, triggeredAt, clock())
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
}
