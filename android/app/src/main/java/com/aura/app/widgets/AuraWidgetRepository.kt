package com.aura.app.widgets

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AuraWidgetRepository(
    private val dao: AuraWidgetDao,
    private val gson: Gson = Gson(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val mutationMutex = Mutex()

    val visibleWidgets: Flow<List<AuraWidget>> =
        dao.observeVisibleWidgets().map { entities -> entities.mapNotNull(::decodeWidget) }

    val hostedWidgets: Flow<List<HostedAndroidWidget>> =
        dao.observeHostedWidgets().map { entities -> entities.map { it.model() } }

    suspend fun reconcileStartup() = mutationMutex.withLock {
        val now = clock()
        dao.activeWidgets().filter { decodeWidget(it) == null }.forEach { entity ->
            persistTransition(
                entity = entity,
                status = AuraWidgetStatus.Expired,
                eventType = "invalid_stored_widget",
                now = now,
                pendingActionId = null,
                error = "Stored widget data was invalid."
            )
        }
        dao.widgetsWithStatus(AuraWidgetStatus.Executing.wireValue).forEach { entity ->
            persistTransition(
                entity = entity,
                status = AuraWidgetStatus.Failed,
                eventType = "execution_interrupted",
                now = now,
                pendingActionId = null,
                error = "The previous action was interrupted. Verify the result before trying again."
            )
        }
        dao.widgetsWithStatus(AuraWidgetStatus.Succeeded.wireValue).forEach { entity ->
            persistTransition(
                entity = entity,
                status = AuraWidgetStatus.Dismissed,
                eventType = "completion_reconciled",
                now = now,
                pendingActionId = null
            )
        }
        expireEligibleWidgets(now)
        pruneTerminalHistory(now)
    }

    suspend fun expireWidgets() = mutationMutex.withLock {
        val now = clock()
        dao.executionsStartedBefore(now - EXECUTION_TIMEOUT_MILLIS).forEach { entity ->
            persistTransition(
                entity = entity,
                status = AuraWidgetStatus.Failed,
                eventType = "execution_timed_out",
                now = now,
                pendingActionId = null,
                error = "The action timed out. Verify the result before trying again."
            )
        }
        expireEligibleWidgets(now)
        dao.widgetsWithStatus(AuraWidgetStatus.Succeeded.wireValue)
            .filter { it.updatedAt <= now - SUCCESS_VISIBILITY_MILLIS }
            .forEach { entity ->
                persistTransition(
                    entity = entity,
                    status = AuraWidgetStatus.Dismissed,
                    eventType = "completion_auto_dismissed",
                    now = now,
                    pendingActionId = null
                )
            }
        pruneTerminalHistory(now)
    }

    suspend fun admit(proposal: AuraWidgetProposal): AuraWidget = mutationMutex.withLock {
        val valid = AuraWidgetPolicy.validate(proposal)
        val now = clock()
        expireEligibleWidgets(now)
        if (valid.dedupeKey.isNotBlank()) {
            dao.activeWidgetByDedupeKey(valid.dedupeKey)?.let { existing ->
                decodeWidget(existing)?.let { return@withLock it }
                persistTransition(
                    entity = existing,
                    status = AuraWidgetStatus.Expired,
                    eventType = "invalid_stored_widget",
                    now = now,
                    pendingActionId = null,
                    error = "Stored widget data was invalid."
                )
            }
        }
        if (dao.activeWidgets().size >= MAX_ACTIVE_WIDGETS) {
            throw AuraWidgetValidationException(
                "Aura already has $MAX_ACTIVE_WIDGETS active widgets. Dismiss one before adding another."
            )
        }
        val id = UUID.randomUUID().toString()
        val entity = AuraWidgetEntity(
            id = id,
            kind = valid.kind.wireValue,
            title = valid.title,
            message = valid.message,
            detailsJson = gson.toJson(valid.details),
            actionsJson = gson.toJson(valid.actions.map { it.entityPayload() }),
            presentation = valid.presentation.wireValue,
            contentFormat = valid.contentFormat.wireValue,
            content = valid.content,
            status = AuraWidgetStatus.Visible.wireValue,
            risk = valid.risk.wireValue,
            priority = valid.priority,
            source = valid.source,
            dedupeKey = valid.dedupeKey,
            pendingActionId = null,
            createdAt = now,
            updatedAt = now,
            expiresAt = now + TimeUnit.MINUTES.toMillis(valid.expiresInMinutes.toLong()),
            lastError = null,
            assistantRunId = valid.assistantRunId
        )
        dao.persistWidget(entity, event(id, "admitted", mapOf("source" to valid.source), now))
        decodeWidget(entity) ?: throw AuraWidgetValidationException("Could not encode widget")
    }

    /**
     * Idempotently updates the primary or an extra Home surface for an assistant run.
     * Terminal surfaces are never resurrected after the user dismisses or expires them.
     */
    suspend fun upsertAssistantRunWidget(
        assistantRunId: String,
        proposal: AuraWidgetProposal,
        surfaceKey: String = "primary"
    ): AuraWidget = mutationMutex.withLock {
        val runId = assistantRunId.trim()
        if (runId.isEmpty() || runId.length > 200) {
            throw AuraWidgetValidationException("Assistant run id is invalid")
        }
        val normalizedSurfaceKey = surfaceKey.trim().ifBlank { "primary" }
        val dedupeKey = "assistant-run:$runId:$normalizedSurfaceKey"
        val valid = AuraWidgetPolicy.validate(
            proposal.copy(
                dedupeKey = dedupeKey,
                source = "assistant_run",
                assistantRunId = runId
            )
        )
        val now = clock()
        expireEligibleWidgets(now)
        val existing = dao.assistantRunWidget(runId, dedupeKey)
        if (existing != null) {
            val decoded = decodeWidget(existing)
                ?: throw AuraWidgetValidationException("Stored assistant run surface is invalid")
            if (existing.status in TERMINAL_STATUSES) return@withLock decoded
            val updated = existing.copy(
                kind = valid.kind.wireValue,
                title = valid.title,
                message = valid.message,
                detailsJson = gson.toJson(valid.details),
                actionsJson = gson.toJson(valid.actions.map { it.entityPayload() }),
                presentation = valid.presentation.wireValue,
                contentFormat = valid.contentFormat.wireValue,
                content = valid.content,
                status = AuraWidgetStatus.Visible.wireValue,
                risk = valid.risk.wireValue,
                priority = valid.priority,
                source = valid.source,
                dedupeKey = valid.dedupeKey,
                pendingActionId = null,
                updatedAt = now,
                expiresAt = now + TimeUnit.MINUTES.toMillis(valid.expiresInMinutes.toLong()),
                lastError = null,
                assistantRunId = runId
            )
            dao.persistWidget(
                updated,
                event(updated.id, "assistant_run_surface_updated", mapOf("runId" to runId), now)
            )
            return@withLock decodeWidget(updated)
                ?: throw AuraWidgetValidationException("Could not encode assistant run surface")
        }
        if (dao.activeWidgets().size >= MAX_ACTIVE_WIDGETS) {
            throw AuraWidgetValidationException(
                "Aura already has $MAX_ACTIVE_WIDGETS active widgets. Dismiss one before adding another."
            )
        }
        val id = UUID.randomUUID().toString()
        val entity = AuraWidgetEntity(
            id = id,
            kind = valid.kind.wireValue,
            title = valid.title,
            message = valid.message,
            detailsJson = gson.toJson(valid.details),
            actionsJson = gson.toJson(valid.actions.map { it.entityPayload() }),
            presentation = valid.presentation.wireValue,
            contentFormat = valid.contentFormat.wireValue,
            content = valid.content,
            status = AuraWidgetStatus.Visible.wireValue,
            risk = valid.risk.wireValue,
            priority = valid.priority,
            source = valid.source,
            dedupeKey = valid.dedupeKey,
            pendingActionId = null,
            createdAt = now,
            updatedAt = now,
            expiresAt = now + TimeUnit.MINUTES.toMillis(valid.expiresInMinutes.toLong()),
            lastError = null,
            assistantRunId = runId
        )
        dao.persistWidget(entity, event(id, "assistant_run_surface_admitted", mapOf("runId" to runId), now))
        decodeWidget(entity) ?: throw AuraWidgetValidationException("Could not encode assistant run surface")
    }

    suspend fun dismissAssistantRunWidgets(assistantRunId: String) = mutationMutex.withLock {
        val now = clock()
        dao.widgetsForAssistantRun(assistantRunId).forEach { entity ->
            if (entity.status !in TERMINAL_STATUSES) {
                persistTransition(
                    entity = entity,
                    status = AuraWidgetStatus.Dismissed,
                    eventType = "assistant_run_surface_hidden",
                    now = now,
                    pendingActionId = null
                )
            }
        }
    }

    suspend fun requestAction(widgetId: String, actionId: String): AuraWidgetActionDecision =
        mutationMutex.withLock {
            val entity = dao.widget(widgetId) ?: return@withLock AuraWidgetActionDecision.Ignored
            val widget = decodeWidget(entity) ?: return@withLock AuraWidgetActionDecision.Ignored
            val action = widget.actions.firstOrNull { it.id == actionId }
                ?: return@withLock AuraWidgetActionDecision.Ignored
            if (widget.status !in setOf(AuraWidgetStatus.Visible, AuraWidgetStatus.Failed)) {
                return@withLock AuraWidgetActionDecision.Ignored
            }
            val now = clock()
            if (widget.expiresAt <= now) {
                persistTransition(
                    entity = entity,
                    status = AuraWidgetStatus.Expired,
                    eventType = "expired",
                    now = now,
                    pendingActionId = null
                )
                return@withLock AuraWidgetActionDecision.Ignored
            }
            if (action.requiresConfirmation) {
                val pending = entity.copy(
                    status = AuraWidgetStatus.AwaitingConfirmation.wireValue,
                    pendingActionId = action.id,
                    updatedAt = now,
                    lastError = null
                )
                dao.persistWidget(pending, event(widgetId, "confirmation_requested", mapOf("actionId" to action.id), now))
                return@withLock AuraWidgetActionDecision.NeedsConfirmation(
                    decodeWidget(pending) ?: widget,
                    action
                )
            }
            beginExecution(entity, widget, action, now)
        }

    suspend fun confirmAction(widgetId: String, actionId: String): AuraWidgetActionDecision =
        mutationMutex.withLock {
            val entity = dao.widget(widgetId) ?: return@withLock AuraWidgetActionDecision.Ignored
            val widget = decodeWidget(entity) ?: return@withLock AuraWidgetActionDecision.Ignored
            val action = widget.actions.firstOrNull { it.id == actionId }
                ?: return@withLock AuraWidgetActionDecision.Ignored
            if (
                widget.status != AuraWidgetStatus.AwaitingConfirmation ||
                widget.pendingActionId != action.id ||
                !action.requiresConfirmation
            ) {
                return@withLock AuraWidgetActionDecision.Ignored
            }
            val now = clock()
            if (widget.expiresAt <= now) {
                persistTransition(
                    entity = entity,
                    status = AuraWidgetStatus.Expired,
                    eventType = "expired",
                    now = now,
                    pendingActionId = null
                )
                return@withLock AuraWidgetActionDecision.Ignored
            }
            beginExecution(entity, widget, action, now)
        }

    suspend fun cancelConfirmation(widgetId: String) = mutationMutex.withLock {
        val entity = dao.widget(widgetId) ?: return@withLock false
        if (entity.status != AuraWidgetStatus.AwaitingConfirmation.wireValue) return@withLock false
        persistTransition(
            entity = entity,
            status = AuraWidgetStatus.Visible,
            eventType = "confirmation_cancelled",
            now = clock(),
            pendingActionId = null
        )
        true
    }

    suspend fun completeAction(widgetId: String, actionId: String, error: String? = null) =
        mutationMutex.withLock {
            val entity = dao.widget(widgetId) ?: return@withLock false
            if (
                entity.status != AuraWidgetStatus.Executing.wireValue ||
                entity.pendingActionId != actionId
            ) {
                return@withLock false
            }
            persistTransition(
                entity = entity,
                status = if (error == null) AuraWidgetStatus.Succeeded else AuraWidgetStatus.Failed,
                eventType = if (error == null) "execution_succeeded" else "execution_failed",
                now = clock(),
                pendingActionId = null,
                error = error,
                payload = mapOf("actionId" to actionId)
            )
            true
        }

    suspend fun dismiss(widgetId: String) = mutationMutex.withLock {
        val entity = dao.widget(widgetId) ?: return@withLock false
        if (entity.status == AuraWidgetStatus.Executing.wireValue) return@withLock false
        if (entity.status in TERMINAL_STATUSES) return@withLock true
        persistTransition(
            entity = entity,
            status = AuraWidgetStatus.Dismissed,
            eventType = "dismissed",
            now = clock(),
            pendingActionId = null
        )
        true
    }

    suspend fun addHostedWidget(
        appWidgetId: Int,
        providerPackage: String,
        providerClass: String,
        spanX: Int,
        spanY: Int
    ): HostedAndroidWidget = mutationMutex.withLock {
        dao.hostedWidget(appWidgetId)?.let { return@withLock it.model() }
        val now = clock()
        val entity = HostedAndroidWidgetEntity(
            appWidgetId = appWidgetId,
            providerPackage = providerPackage,
            providerClass = providerClass,
            page = 0,
            cellX = 0,
            cellY = dao.maxHostedCellY(0) + 1,
            spanX = spanX.coerceIn(1, 4),
            spanY = spanY.coerceIn(1, 6),
            createdAt = now,
            updatedAt = now
        )
        dao.upsertHostedWidget(entity)
        entity.model()
    }

    suspend fun resizeHostedWidget(appWidgetId: Int, spanX: Int, spanY: Int): HostedAndroidWidget? =
        mutationMutex.withLock {
            dao.resizeHostedWidget(appWidgetId, spanX.coerceIn(1, 4), spanY.coerceIn(1, 6), clock())
            dao.hostedWidget(appWidgetId)?.model()
        }

    suspend fun removeHostedWidget(appWidgetId: Int): Boolean =
        mutationMutex.withLock { dao.deleteHostedWidget(appWidgetId) > 0 }

    suspend fun hostedWidgetIds(): Set<Int> =
        mutationMutex.withLock { dao.hostedWidgetIds().toSet() }

    suspend fun hostedWidget(appWidgetId: Int): HostedAndroidWidget? =
        mutationMutex.withLock { dao.hostedWidget(appWidgetId)?.model() }

    private suspend fun persistTransition(
        entity: AuraWidgetEntity,
        status: AuraWidgetStatus,
        eventType: String,
        now: Long,
        pendingActionId: String?,
        error: String? = null,
        payload: Map<String, String> = emptyMap()
    ) {
        val updated = entity.copy(
            status = status.wireValue,
            pendingActionId = pendingActionId,
            updatedAt = now,
            lastError = error?.take(240)
        )
        dao.persistWidget(updated, event(entity.id, eventType, payload, now))
    }

    private suspend fun beginExecution(
        entity: AuraWidgetEntity,
        widget: AuraWidget,
        action: AuraWidgetAction,
        now: Long
    ): AuraWidgetActionDecision.Execute {
        val executing = entity.copy(
            status = AuraWidgetStatus.Executing.wireValue,
            pendingActionId = action.id,
            updatedAt = now,
            lastError = null
        )
        dao.persistWidget(
            executing,
            event(entity.id, "execution_started", mapOf("actionId" to action.id), now)
        )
        return AuraWidgetActionDecision.Execute(decodeWidget(executing) ?: widget, action)
    }

    private suspend fun expireEligibleWidgets(now: Long) {
        dao.expirableWidgets(now).forEach { entity ->
            persistTransition(
                entity = entity,
                status = AuraWidgetStatus.Expired,
                eventType = "expired",
                now = now,
                pendingActionId = null
            )
        }
    }

    private suspend fun pruneTerminalHistory(now: Long) {
        dao.deleteWidgetsAndEvents(
            dao.terminalWidgetIdsBefore(now - TERMINAL_RETENTION_MILLIS)
        )
    }

    private fun decodeWidget(entity: AuraWidgetEntity): AuraWidget? {
        val kind = AuraWidgetKind.fromWireValue(entity.kind) ?: return null
        val status = AuraWidgetStatus.fromWireValue(entity.status) ?: return null
        val risk = AuraWidgetRisk.fromWireValue(entity.risk) ?: return null
        val presentation = AuraWidgetPresentation.fromWireValue(entity.presentation) ?: return null
        val contentFormat = AuraWidgetContentFormat.fromWireValue(entity.contentFormat) ?: return null
        val detailType = object : TypeToken<List<String>>() {}.type
        val actionType = object : TypeToken<List<StoredAction>>() {}.type
        return try {
            val storedActions = gson.fromJson<List<StoredAction>>(entity.actionsJson, actionType).orEmpty()
            val normalized = AuraWidgetPolicy.validate(
                AuraWidgetProposal(
                    kind = kind,
                    title = entity.title,
                    message = entity.message,
                    details = gson.fromJson<List<String>>(entity.detailsJson, detailType).orEmpty(),
                    actions = storedActions.map {
                        it.model() ?: throw AuraWidgetValidationException("Stored widget action is invalid")
                    },
                    presentation = presentation,
                    contentFormat = contentFormat,
                    content = entity.content,
                    risk = risk,
                    priority = entity.priority,
                    expiresInMinutes = 1,
                    dedupeKey = entity.dedupeKey,
                    source = entity.source,
                    assistantRunId = entity.assistantRunId
                )
            )
            AuraWidget(
                id = entity.id,
                kind = kind,
                title = normalized.title,
                message = normalized.message,
                details = normalized.details,
                actions = normalized.actions,
                presentation = normalized.presentation,
                contentFormat = normalized.contentFormat,
                content = normalized.content,
                status = status,
                risk = risk,
                priority = normalized.priority,
                source = normalized.source,
                dedupeKey = normalized.dedupeKey,
                pendingActionId = entity.pendingActionId,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                expiresAt = entity.expiresAt,
                lastError = entity.lastError,
                assistantRunId = entity.assistantRunId
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun AuraWidgetAction.entityPayload() = StoredAction(
        id = id,
        label = label,
        type = type.wireValue,
        payload = payload,
        requiresConfirmation = requiresConfirmation
    )

    private fun StoredAction.model(): AuraWidgetAction? =
        AuraWidgetActionType.fromWireValue(type)?.let {
            AuraWidgetAction(id, label, it, payload, requiresConfirmation)
        }

    private fun event(widgetId: String, type: String, payload: Map<String, String>, now: Long) =
        AuraWidgetEventEntity(UUID.randomUUID().toString(), widgetId, type, gson.toJson(payload), now)

    private fun HostedAndroidWidgetEntity.model() = HostedAndroidWidget(
        appWidgetId,
        providerPackage,
        providerClass,
        page,
        cellX,
        cellY,
        spanX.coerceIn(1, 4),
        spanY.coerceIn(1, 6),
        createdAt,
        updatedAt
    )

    private data class StoredAction(
        val id: String = "",
        val label: String = "",
        val type: String = "",
        val payload: Map<String, String> = emptyMap(),
        val requiresConfirmation: Boolean = false
    )

    companion object {
        const val MAX_ACTIVE_WIDGETS = 12
        private const val SUCCESS_VISIBILITY_MILLIS = 1_500L
        private val EXECUTION_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5)
        private val TERMINAL_RETENTION_MILLIS = TimeUnit.DAYS.toMillis(30)
        private val TERMINAL_STATUSES = setOf(
            AuraWidgetStatus.Dismissed.wireValue,
            AuraWidgetStatus.Expired.wireValue
        )
    }
}
