package com.aura.app.dreams

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

class DreamOrchestrator(
    private val repository: DreamRepository,
    private val settingsStore: DreamSettingsStore,
    private val evidenceCollector: DreamEvidenceCollector,
    private val proposalEngine: DreamProposalEngine
) {
    private val mutex = Mutex()

    suspend fun run(window: DreamWindow): DreamReport = mutex.withLock {
        val admission = repository.admit(window)
        if (!admission.admitted) {
            return@withLock repository.report(admission.run.id) ?: DreamReport(admission.run, emptyList())
        }
        val runId = admission.run.id
        var signalCount = 0
        var proposalCount = 0
        var warningCount = 0
        try {
            val settings = settingsStore.stateValue()
            repository.trace(runId, DreamRunStage.Collecting, "stage_started", "ok", "Collecting bounded local evidence")
            repository.advance(runId, DreamRunStage.Collecting, signalCount, proposalCount, warningCount)
            val batch = evidenceCollector.collect(runId, window, settings)
            warningCount = batch.warnings.size
            val signals = repository.insertSignals(batch.signals)
            signalCount = signals.size

            repository.trace(runId, DreamRunStage.Detecting, "stage_started", "ok", "Building deterministic proposals")
            repository.advance(runId, DreamRunStage.Detecting, signalCount, proposalCount, warningCount)
            val drafts = proposalEngine.build(signals, settings.maxProposals)

            repository.trace(runId, DreamRunStage.Validating, "stage_started", "ok", "Persisting validated proposal drafts")
            repository.advance(runId, DreamRunStage.Validating, signalCount, proposalCount, warningCount)
            val proposals = repository.insertProposals(runId, drafts)
            proposalCount = proposals.size

            repository.advance(runId, DreamRunStage.Publishing, signalCount, proposalCount, warningCount)
            repository.prune()
            val completed = repository.finish(
                runId = runId,
                status = DreamRunStatus.Completed,
                signalCount = signalCount,
                proposalCount = proposalCount,
                warningCount = warningCount
            )
            repository.trace(runId, DreamRunStage.Completed, "run_finished", "ok", "$proposalCount proposals ready")
            DreamReport(completed, proposals)
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                repository.finish(
                    runId = runId,
                    status = DreamRunStatus.Cancelled,
                    signalCount = signalCount,
                    proposalCount = proposalCount,
                    warningCount = warningCount,
                    errorMessage = "Dream run was cancelled"
                )
            }
            throw error
        } catch (error: Exception) {
            val failed = repository.finish(
                runId = runId,
                status = DreamRunStatus.Failed,
                signalCount = signalCount,
                proposalCount = proposalCount,
                warningCount = warningCount,
                errorMessage = error.message ?: "Dream run failed"
            )
            repository.trace(runId, DreamRunStage.Completed, "run_finished", "failed", error.message ?: "failure")
            DreamReport(failed, repository.report(runId)?.proposals.orEmpty())
        }
    }
}

private suspend fun DreamSettingsStore.stateValue(): DreamSettings = state.first()
