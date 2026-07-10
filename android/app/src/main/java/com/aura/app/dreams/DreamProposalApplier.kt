package com.aura.app.dreams

import com.aura.app.assistant.AssistantRepository
import com.aura.app.automations.AutomationRepository
import com.aura.app.automations.AutomationRuntime
import com.aura.app.miniapps.MiniAppRepository
import com.aura.app.miniapps.MiniAppRevisionPreview
import com.aura.app.miniapps.MiniAppValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class DreamProposalApplier(
    private val dreamRepository: DreamRepository,
    private val automationRepository: AutomationRepository,
    private val automationRuntime: AutomationRuntime,
    private val miniAppRepository: MiniAppRepository,
    private val assistantRepository: AssistantRepository
) {
    suspend fun apply(proposalId: String): DreamApplyResult {
        val claimed = dreamRepository.claimForApplication(proposalId)
            ?: return DreamApplyResult.Rejected("This proposal is unavailable, already decided, or review-only.")
        return try {
            val message = when (claimed.type) {
                DreamProposalType.AutomationRepair -> applyAutomationRepair(claimed)
                DreamProposalType.RoutineAutomation -> applyRoutineDraft(claimed)
                DreamProposalType.MiniAppEvolution -> applyMiniAppEvolution(claimed)
                DreamProposalType.TodoRescue -> error("Task rescue proposals are review-only")
            }
            val applied = dreamRepository.markApplied(claimed.id, message)
                ?: return DreamApplyResult.Failed("The change completed, but its Dream receipt could not be loaded.")
            DreamApplyResult.Applied(applied)
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                dreamRepository.markReconciliationRequired(
                    claimed.id,
                    "Application was interrupted. Aura will not replay it automatically."
                )
            }
            throw error
        } catch (error: DreamProposalConflictException) {
            dreamRepository.markApplicationFailed(claimed.id, error.message ?: "The target changed")
            DreamApplyResult.Rejected(error.message ?: "The target changed after this proposal was created.")
        } catch (error: Exception) {
            val message = DreamPrivacyPolicy.sanitizeDiagnostic(error.message ?: "Proposal application failed")
            dreamRepository.markApplicationFailed(claimed.id, message)
            DreamApplyResult.Failed(message)
        }
    }

    private suspend fun applyAutomationRepair(proposal: DreamProposal): String {
        val proposed = proposal.payload.automationSpec
            ?: throw IllegalArgumentException("Repair payload is missing an automation spec")
        val current = automationRepository.get(proposal.subjectId)
            ?: throw DreamProposalConflictException("The automation no longer exists.")
        requireBaseRevision(proposal, DreamAutomationRevision.compute(current))
        require(proposed.id == current.id) { "Repair payload targets a different automation" }
        automationRuntime.upsertAndRestore(proposed)
        return "Applied the validated repair and restored its triggers."
    }

    private suspend fun applyRoutineDraft(proposal: DreamProposal): String {
        val proposed = proposal.payload.automationSpec
            ?: throw IllegalArgumentException("Routine payload is missing an automation spec")
        val source = automationRepository.get(proposal.subjectId)
            ?: throw DreamProposalConflictException("The source automation no longer exists.")
        requireBaseRevision(proposal, DreamAutomationRevision.compute(source))
        require(proposed.id.isBlank()) { "A routine draft must not overwrite an existing automation" }
        require(!proposed.enabled) { "A Dream-created routine must start disabled" }
        automationRuntime.upsertAndRestore(proposed)
        return "Created a disabled automation draft. Review it before enabling."
    }

    private suspend fun applyMiniAppEvolution(proposal: DreamProposal): String {
        val miniAppId = proposal.payload.miniAppId
            ?: throw IllegalArgumentException("Mini-app proposal is missing its target")
        val expectedVersion = proposal.payload.miniAppVersion
            ?: throw IllegalArgumentException("Mini-app proposal is missing its base version")
        val instruction = proposal.payload.revisionInstruction
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Mini-app proposal is missing its revision instruction")
        val current = miniAppRepository.bundle(miniAppId)
            ?: throw DreamProposalConflictException("The mini app no longer exists.")
        if (current.version != expectedVersion) {
            throw DreamProposalConflictException("The mini app changed after this proposal was created.")
        }
        val sample = miniAppRepository.records(miniAppId).take(MaxRecordSample).map { record ->
            record.values.mapValues { (_, value) -> value as Any }
        }
        val generated = assistantRepository.reviseMiniApp(instruction, current, sample)
        val validated = MiniAppValidator.validate(generated.bundle)
        require(validated.id == miniAppId) { "Generated revision changed the mini-app id" }
        require(validated.version > current.version) { "Generated revision must increase the mini-app version" }
        val latest = miniAppRepository.bundle(miniAppId)
            ?: throw DreamProposalConflictException("The mini app disappeared during revision.")
        if (latest.version != expectedVersion) {
            throw DreamProposalConflictException("The mini app changed while the revision was being generated.")
        }
        miniAppRepository.applyRevision(
            MiniAppRevisionPreview(
                bundle = validated,
                summary = generated.summary,
                migrationPlan = generated.migrationPlan
            )
        )
        return "Applied mini-app version ${validated.version}; the previous version remains available for rollback."
    }

    private fun requireBaseRevision(proposal: DreamProposal, actualRevision: String) {
        val expected = proposal.baseRevision
            ?: throw IllegalArgumentException("Proposal has no base revision")
        if (expected != actualRevision) {
            throw DreamProposalConflictException("The target changed after this proposal was created.")
        }
    }

    private companion object {
        const val MaxRecordSample = 50
    }
}

private class DreamProposalConflictException(message: String) : IllegalStateException(message)
