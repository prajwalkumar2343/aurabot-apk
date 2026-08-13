package com.aura.app.assistant

import com.aura.app.widgets.AuraWidgetKind
import com.aura.app.widgets.AuraWidgetPresentation
import com.aura.app.widgets.AuraWidgetProposal
import com.aura.app.widgets.AuraWidgetRepository
import com.aura.app.widgets.AuraWidgetRisk
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AssistantRunSurfaceRepository(
    private val dao: AssistantRunDao,
    private val auraWidgetRepository: AuraWidgetRepository,
    private val workScheduler: AssistantRunScheduler,
    private val currentServiceMode: suspend () -> String = { "local" },
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun surface(runId: String): AssistantRunSurfaceEntity? = dao.surface(runId)

    suspend fun shouldSyncManagedRun(runId: String): Boolean {
        val surface = dao.surface(runId) ?: return false
        return currentServiceMode() == AssistantRunMode.Managed.wireValue &&
            surface.mode == AssistantRunMode.Managed.wireValue &&
            surface.state in ACTIVE_STATES
    }

    suspend fun recordProgress(progress: AssistantRunProgress) = mutex.withLock {
        val current = dao.surface(progress.runId)
        if (current?.state in TERMINAL_STATES) return@withLock
        val now = clock()
        dao.upsert(
            AssistantRunSurfaceEntity(
                runId = progress.runId,
                mode = progress.mode.wireValue,
                state = progress.state,
                phase = progress.phase,
                activeSubagents = progress.activeSubagents.coerceIn(0, 3),
                createdAt = current?.createdAt ?: now,
                updatedAt = now,
                lastError = null
            )
        )
        var surfaceError: String? = null
        if (progress.state in ACTIVE_STATES) {
            try {
                auraWidgetRepository.upsertAssistantRunWidget(
                    assistantRunId = progress.runId,
                    proposal = progress.toProgressProposal()
                )
            } catch (error: com.aura.app.widgets.AuraWidgetValidationException) {
                surfaceError = error.message?.take(MAX_ERROR_CHARS)
            }
            if (progress.mode == AssistantRunMode.Managed) {
                workScheduler.enqueue(progress.runId)
            }
        }
        if (surfaceError != null) {
            dao.upsert(
                AssistantRunSurfaceEntity(
                    runId = progress.runId,
                    mode = progress.mode.wireValue,
                    state = progress.state,
                    phase = progress.phase,
                    activeSubagents = progress.activeSubagents.coerceIn(0, 3),
                    createdAt = current?.createdAt ?: now,
                    updatedAt = now,
                    lastError = surfaceError
                )
            )
        }
    }

    suspend fun complete(runId: String, response: ChatResponse) = mutex.withLock {
        val current = dao.surface(runId)
        if (current?.state in TERMINAL_STATES) return@withLock
        val persistedWidgetCount = persistResponseWidgets(runId, response)
        var terminalError: String? = null
        if (persistedWidgetCount == 0) {
            try {
                auraWidgetRepository.upsertAssistantRunWidget(
                    assistantRunId = runId,
                    proposal = reportProposal(
                        title = "Assistant report",
                        message = response.reply.summaryMessage(),
                        content = response.reply.reportContent(),
                        details = listOf("Run completed")
                    )
                )
            } catch (error: com.aura.app.widgets.AuraWidgetValidationException) {
                terminalError = error.message?.take(MAX_ERROR_CHARS)
            }
        }
        persistTerminal(
            runId = runId,
            state = "completed",
            phase = "completed",
            activeSubagents = 0,
            error = terminalError,
            createdAt = current?.createdAt
        )
    }

    suspend fun fail(runId: String, error: String, mode: AssistantRunMode = AssistantRunMode.Managed) =
        mutex.withLock {
            val current = dao.surface(runId)
            if (current?.state in TERMINAL_STATES) return@withLock
            try {
                auraWidgetRepository.upsertAssistantRunWidget(
                    assistantRunId = runId,
                    proposal = reportProposal(
                        title = "Assistant run needs attention",
                        message = error.summaryMessage(),
                        content = error.reportContent(),
                        details = listOf("Run ${mode.wireValue}", "No action was executed")
                    )
                )
            } catch (_: com.aura.app.widgets.AuraWidgetValidationException) {
                // The run record remains terminal even when Home has no free surface slot.
            }
            persistTerminal(
                runId = runId,
                state = "failed",
                phase = "failed",
                activeSubagents = 0,
                error = error.take(MAX_ERROR_CHARS),
                createdAt = current?.createdAt
            )
        }

    suspend fun reconcileStartup() = mutex.withLock {
        val now = clock()
        val serviceMode = currentServiceMode()
        if (serviceMode != AssistantRunMode.Managed.wireValue) {
            dao.surfacesForMode(AssistantRunMode.Managed.wireValue).forEach { surface ->
                workScheduler.cancel(surface.runId)
                auraWidgetRepository.dismissAssistantRunWidgets(surface.runId)
                if (surface.state in ACTIVE_STATES) {
                    dao.upsert(
                        surface.copy(
                            state = "cancelled",
                            phase = "cancelled",
                            activeSubagents = 0,
                            updatedAt = now,
                            lastError = "Managed run hidden when Aura left managed mode."
                        )
                    )
                }
            }
        }
        dao.activeSurfaces().forEach { surface ->
            if (
                surface.mode == AssistantRunMode.Managed.wireValue &&
                serviceMode == AssistantRunMode.Managed.wireValue
            ) {
                workScheduler.enqueue(surface.runId)
            } else if (surface.mode == AssistantRunMode.Local.wireValue) {
                auraWidgetRepository.upsertAssistantRunWidget(
                    assistantRunId = surface.runId,
                    proposal = reportProposal(
                        title = "Local run interrupted",
                        message = "Aura restarted before this local run finished.",
                        content = "The local provider was not resumed, so no partial result was treated as complete.",
                        details = listOf("Local mode stays offline", "Try the request again")
                    )
                )
                dao.upsert(
                    surface.copy(
                        state = "interrupted",
                        phase = "interrupted",
                        activeSubagents = 0,
                        updatedAt = now,
                        lastError = "Local run interrupted by launcher restart."
                    )
                )
            }
        }
        dao.deleteTerminalBefore(now - RUN_RETENTION_MILLIS)
    }

    internal suspend fun persistManagedSnapshot(snapshot: AgentRunResponse): AssistantRunSyncResult {
        val progress = AssistantRunProgress(
            runId = snapshot.id,
            state = snapshot.state,
            phase = snapshot.phase,
            activeSubagents = snapshot.children.count { it.state == "queued" || it.state == "running" },
            mode = AssistantRunMode.Managed
        )
        return when (snapshot.state) {
            "queued", "running" -> {
                recordProgress(progress)
                AssistantRunSyncResult.Retry
            }
            "completed" -> {
                complete(snapshot.id, snapshot.toChatResponse())
                AssistantRunSyncResult.Finished
            }
            "failed", "interrupted", "cancelled" -> {
                fail(snapshot.id, snapshot.error ?: "Assistant run ${snapshot.state}.")
                AssistantRunSyncResult.Finished
            }
            else -> {
                fail(snapshot.id, "Assistant returned an unknown run state.")
                AssistantRunSyncResult.Finished
            }
        }
    }

    private suspend fun persistResponseWidgets(runId: String, response: ChatResponse): Int {
        var persisted = 0
        response.actions
            .asSequence()
            .filter { it.type == "present_widget" }
            .mapNotNull { it.widget }
            .take(MAX_WIDGETS_PER_RESPONSE)
            .forEach { widget ->
                try {
                    val surfaceKey = if (persisted == 0) "primary" else "extra-$persisted"
                    auraWidgetRepository.upsertAssistantRunWidget(
                        assistantRunId = runId,
                        proposal = widget.toAuraWidgetProposal(
                            assistantRunId = runId,
                            sourceOverride = "assistant_run"
                        ),
                        surfaceKey = surfaceKey
                    )
                    persisted += 1
                } catch (_: IllegalArgumentException) {
                    // Malformed local-model output fails closed; a report is emitted below
                    // when no valid Home surface survives validation.
                }
            }
        return persisted
    }

    private suspend fun persistTerminal(
        runId: String,
        state: String,
        phase: String,
        activeSubagents: Int,
        error: String?,
        createdAt: Long?
    ) {
        val now = clock()
        dao.upsert(
            AssistantRunSurfaceEntity(
                runId = runId,
                mode = dao.surface(runId)?.mode ?: AssistantRunMode.Managed.wireValue,
                state = state,
                phase = phase,
                activeSubagents = activeSubagents,
                createdAt = createdAt ?: now,
                updatedAt = now,
                lastError = error
            )
        )
    }

    private fun AssistantRunProgress.toProgressProposal() = AuraWidgetProposal(
        kind = AuraWidgetKind.Progress,
        title = "Assistant run",
        message = when (state) {
            "queued" -> "Queued — Aura will keep working on this."
            else -> "Working — ${phase.displayName()}"
        },
        details = buildList {
            add(if (mode == AssistantRunMode.Local) "Local and offline" else "Managed run")
            if (activeSubagents > 0) add("$activeSubagents subagents active")
        },
        presentation = AuraWidgetPresentation.Compact,
        risk = AuraWidgetRisk.Low,
        priority = 90,
        expiresInMinutes = 24 * 60,
        source = "assistant_run"
    )

    private fun reportProposal(
        title: String,
        message: String,
        content: String,
        details: List<String>
    ) = AuraWidgetProposal(
        kind = AuraWidgetKind.Report,
        title = title,
        message = message,
        details = details,
        presentation = AuraWidgetPresentation.Fullscreen,
        content = content,
        risk = AuraWidgetRisk.Low,
        priority = 80,
        expiresInMinutes = 7 * 24 * 60,
        source = "assistant_run"
    )

    private fun String.summaryMessage(): String =
        replace(Regex("^\\{[a-zA-Z0-9_-]+\\}\\s*"), "")
            .trim()
            .ifBlank { "Aura finished the assistant run." }
            .take(280)

    private fun String.reportContent(): String =
        replace(Regex("^\\{[a-zA-Z0-9_-]+\\}\\s*"), "")
            .trim()
            .ifBlank { "Aura finished the assistant run." }
            .take(60_000)

    private fun String.displayName(): String =
        replace('_', ' ').split(' ').joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

    private companion object {
        const val MAX_WIDGETS_PER_RESPONSE = 4
        const val MAX_ERROR_CHARS = 500
        val ACTIVE_STATES = setOf("queued", "running")
        val TERMINAL_STATES = setOf("completed", "failed", "interrupted", "cancelled")
        val RUN_RETENTION_MILLIS = TimeUnit.DAYS.toMillis(30)
    }

    private val mutex = Mutex()
}

enum class AssistantRunSyncResult {
    Retry,
    Finished
}

private fun AgentRunResponse.toChatResponse() = ChatResponse(
    reply = reply ?: "{neutral} Done.",
    session_id = session_id,
    emotion = emotion,
    created_emotion = created_emotion,
    actions = actions
)
