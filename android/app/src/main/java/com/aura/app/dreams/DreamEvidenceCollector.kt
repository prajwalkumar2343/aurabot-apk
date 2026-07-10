package com.aura.app.dreams

import com.aura.app.assistant.AssistantRepository
import com.aura.app.automations.AutomationEvents
import com.aura.app.automations.AutomationRepository
import com.aura.app.automations.AutomationRunRecord
import com.aura.app.automations.AutomationRunStatus
import com.aura.app.automations.AutomationSpec
import com.aura.app.automations.AutomationStepRunRecord
import com.aura.app.automations.AutomationTriggerTypes
import com.aura.app.miniapps.MiniAppEvolutionEngine
import com.aura.app.miniapps.MiniAppRepository
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeParseException

class DreamEvidenceCollector(
    private val automationRepository: AutomationRepository,
    private val assistantRepository: AssistantRepository,
    private val miniAppRepository: MiniAppRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    suspend fun collect(runId: String, window: DreamWindow, settings: DreamSettings): DreamEvidenceBatch {
        val signals = mutableListOf<DreamSignal>()
        val warnings = mutableListOf<String>()
        collectSource("automations", warnings) { signals += automationSignals(runId, window, settings) }
        collectSource("tasks", warnings) { signals += todoSignals(runId, window, settings) }
        collectSource("mini apps", warnings) { signals += miniAppSignals(runId, settings) }
        return DreamEvidenceBatch(
            signals = signals.distinctBy { it.fingerprint }.take(MaxSignals),
            warnings = warnings
        )
    }

    private suspend fun automationSignals(
        runId: String,
        window: DreamWindow,
        settings: DreamSettings
    ): List<DreamSignal> {
        val observations = mutableListOf<AutomationObservation>()
        automationRepository.list().take(MaxAutomations).forEach { spec ->
            automationRepository.runs(spec.id, RunsPerAutomation)
                .filter { it.updatedAt in window.startMillis until window.endMillis }
                .forEach { run ->
                    val failedStep = if (run.status == AutomationRunStatus.Failed) {
                        automationRepository.stepRuns(run.id)
                            .lastOrNull { it.status == AutomationRunStatus.Failed }
                    } else {
                        null
                    }
                    observations += AutomationObservation(spec, run, failedStep)
                }
        }
        return failureSignals(runId, observations, settings) + routineSignals(runId, observations, settings)
    }

    private fun failureSignals(
        runId: String,
        observations: List<AutomationObservation>,
        settings: DreamSettings
    ): List<DreamSignal> {
        val failed = observations.filter { it.run.status == AutomationRunStatus.Failed }
        return failed.groupBy { observation ->
            val failureKind = DreamPrivacyPolicy.classifyAutomationFailure(
                observation.failedStep?.message ?: observation.run.message
            )
            listOf(
                observation.spec.id,
                observation.run.automationRevision,
                observation.failedStep?.stepId.orEmpty(),
                failureKind
            ).joinToString("|")
        }.values.mapNotNull { group ->
            if (group.size < MinFailureOccurrences) return@mapNotNull null
            val latest = group.maxBy { it.run.updatedAt }
            val laterSuccess = observations.any {
                it.spec.id == latest.spec.id &&
                    it.run.automationRevision == latest.run.automationRevision &&
                    it.run.status == AutomationRunStatus.Success &&
                    it.run.updatedAt > latest.run.updatedAt
            }
            if (laterSuccess) return@mapNotNull null
            val failedStep = latest.failedStep
            val failureKind = DreamPrivacyPolicy.classifyAutomationFailure(failedStep?.message ?: latest.run.message)
            val timeout = latest.spec.flow?.steps
                ?.firstOrNull { it.id == failedStep?.stepId }
                ?.action
                ?.metadata
                ?.get("timeoutMillis")
                ?.toLongOrNull()
                ?: DefaultAutomationTimeoutMillis
            val fingerprint = DreamPrivacyPolicy.fingerprint(
                DreamSignalKind.AutomationFailure.name,
                latest.spec.id,
                latest.run.automationRevision,
                failedStep?.stepId.orEmpty(),
                failureKind
            )
            signal(
                runId = runId,
                kind = DreamSignalKind.AutomationFailure,
                subjectId = latest.spec.id,
                fingerprint = fingerprint,
                summary = "${latest.spec.name} failed at the same step ${group.size} times.",
                attributes = mapOf(
                    "automationName" to latest.spec.name,
                    "revision" to latest.run.automationRevision,
                    "stepId" to failedStep?.stepId.orEmpty(),
                    "actionType" to failedStep?.actionType.orEmpty(),
                    "failureKind" to failureKind,
                    "count" to group.size.toString(),
                    "timeoutMillis" to timeout.toString()
                ),
                occurredAt = latest.run.updatedAt,
                confidence = (0.68f + (group.size.coerceAtMost(5) - 2) * 0.07f).coerceAtMost(0.9f),
                settings = settings
            )
        }
    }

    private fun routineSignals(
        runId: String,
        observations: List<AutomationObservation>,
        settings: DreamSettings
    ): List<DreamSignal> = observations
        .filter {
            it.spec.trigger.type == AutomationTriggerTypes.Manual &&
                it.run.eventType == AutomationEvents.Manual &&
                it.run.status == AutomationRunStatus.Success
        }
        .groupBy { it.spec.id }
        .values
        .mapNotNull { group ->
            val distinctDays = group.map { Instant.ofEpochMilli(it.run.startedAt).atZone(zoneId).toLocalDate() }.distinct()
            if (group.size < MinRoutineOccurrences || distinctDays.size < MinRoutineDays) return@mapNotNull null
            val minuteValues = group.map {
                val local = Instant.ofEpochMilli(it.run.startedAt).atZone(zoneId).toLocalTime()
                local.hour * 60 + local.minute
            }.sorted()
            val spread = minuteValues.last() - minuteValues.first()
            if (spread > MaxRoutineSpreadMinutes) return@mapNotNull null
            val medianMinutes = minuteValues[minuteValues.size / 2]
            val localTime = "%02d:%02d".format(medianMinutes / 60, medianMinutes % 60)
            val latest = group.maxBy { it.run.updatedAt }
            val fingerprint = DreamPrivacyPolicy.fingerprint(
                DreamSignalKind.RepeatedRoutine.name,
                latest.spec.id,
                localTime
            )
            signal(
                runId = runId,
                kind = DreamSignalKind.RepeatedRoutine,
                subjectId = latest.spec.id,
                fingerprint = fingerprint,
                summary = "${latest.spec.name} usually runs manually around $localTime.",
                attributes = mapOf(
                    "automationName" to latest.spec.name,
                    "occurrenceCount" to group.size.toString(),
                    "localTime" to localTime,
                    "sourceAutomationId" to latest.spec.id,
                    "revision" to DreamAutomationRevision.compute(latest.spec)
                ),
                occurredAt = latest.run.updatedAt,
                confidence = (0.65f + distinctDays.size.coerceAtMost(5) * 0.05f).coerceAtMost(0.9f),
                settings = settings
            )
        }

    private suspend fun todoSignals(
        runId: String,
        window: DreamWindow,
        settings: DreamSettings
    ): List<DreamSignal> {
        val now = clock()
        return assistantRepository.todos().take(MaxTodos).mapNotNull { todo ->
            if (todo.done) return@mapNotNull null
            val createdAt = try {
                Instant.parse(todo.created_at).toEpochMilli()
            } catch (_: DateTimeParseException) {
                return@mapNotNull null
            }
            val ageDays = ((now - createdAt).coerceAtLeast(0L) / DayMillis).toInt()
            if (ageDays < StaleTodoDays) return@mapNotNull null
            val fingerprint = DreamPrivacyPolicy.fingerprint(DreamSignalKind.StaleTodo.name, todo.id)
            signal(
                runId = runId,
                kind = DreamSignalKind.StaleTodo,
                subjectId = todo.id,
                fingerprint = fingerprint,
                summary = "A task has stayed open for $ageDays days.",
                attributes = mapOf("ageDays" to ageDays.toString()),
                occurredAt = maxOf(createdAt, window.startMillis),
                confidence = (0.62f + (ageDays - StaleTodoDays).coerceAtMost(14) * 0.01f),
                settings = settings
            )
        }
    }

    private suspend fun miniAppSignals(runId: String, settings: DreamSettings): List<DreamSignal> =
        miniAppRepository.listInstalled().take(MaxMiniApps).mapNotNull { install ->
            val bundle = miniAppRepository.bundle(install.id) ?: return@mapNotNull null
            val records = miniAppRepository.records(install.id).take(MaxRecordsPerMiniApp)
            val suggestion = MiniAppEvolutionEngine.suggest(bundle, records) ?: return@mapNotNull null
            val fingerprint = DreamPrivacyPolicy.fingerprint(
                DreamSignalKind.MiniAppEvolution.name,
                install.id,
                bundle.version.toString(),
                suggestion.id
            )
            signal(
                runId = runId,
                kind = DreamSignalKind.MiniAppEvolution,
                subjectId = install.id,
                fingerprint = fingerprint,
                summary = suggestion.reason,
                attributes = mapOf(
                    "miniAppName" to install.name,
                    "miniAppVersion" to bundle.version.toString(),
                    "suggestionTitle" to suggestion.title,
                    "revisionInstruction" to suggestion.revisionInstruction
                ),
                occurredAt = clock(),
                confidence = suggestion.confidence.coerceIn(0.55f, 0.95f),
                settings = settings
            )
        }

    private fun signal(
        runId: String,
        kind: DreamSignalKind,
        subjectId: String,
        fingerprint: String,
        summary: String,
        attributes: Map<String, String>,
        occurredAt: Long,
        confidence: Float,
        settings: DreamSettings
    ) = DreamSignal(
        id = DreamPrivacyPolicy.fingerprint(runId, fingerprint),
        runId = runId,
        kind = kind,
        subjectId = subjectId,
        fingerprint = fingerprint,
        summary = DreamPrivacyPolicy.sanitizeDiagnostic(summary),
        attributes = DreamPrivacyPolicy.allowlistedAttributes(kind, attributes),
        occurredAt = occurredAt,
        confidence = confidence.coerceIn(0f, 1f),
        expiresAt = clock() + settings.signalRetentionDays.toLong() * DayMillis
    )

    private suspend fun collectSource(
        label: String,
        warnings: MutableList<String>,
        block: suspend () -> Unit
    ) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            warnings += "$label: ${DreamPrivacyPolicy.sanitizeDiagnostic(error.message ?: "unavailable")}"
        }
    }

    private data class AutomationObservation(
        val spec: AutomationSpec,
        val run: AutomationRunRecord,
        val failedStep: AutomationStepRunRecord?
    )

    private companion object {
        const val MaxSignals = 500
        const val MaxAutomations = 100
        const val RunsPerAutomation = 20
        const val MaxTodos = 100
        const val MaxMiniApps = 50
        const val MaxRecordsPerMiniApp = 200
        const val MinFailureOccurrences = 2
        const val MinRoutineOccurrences = 3
        const val MinRoutineDays = 2
        const val MaxRoutineSpreadMinutes = 90
        const val StaleTodoDays = 7
        const val DefaultAutomationTimeoutMillis = 5_000L
        const val DayMillis = 86_400_000L
    }
}

internal object DreamAutomationRevision {
    private val gson = Gson()

    fun compute(spec: AutomationSpec): String {
        val source = gson.toJson(spec.copy(createdAt = 0L, updatedAt = 0L))
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}
