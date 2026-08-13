package com.aura.app.ui.dreams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aura.app.AppContainer
import com.aura.app.dreams.DreamApplyResult
import com.aura.app.dreams.DreamProposal
import com.aura.app.dreams.DreamRun
import com.aura.app.dreams.DreamSettings
import com.aura.app.dreams.DreamSignal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class DreamsUiState(
    val settings: DreamSettings = DreamSettings(),
    val latestRun: DreamRun? = null,
    val proposals: List<DreamProposal> = emptyList(),
    val selectedProposalId: String? = null,
    val selectedEvidence: List<DreamSignal> = emptyList(),
    val applyingProposalId: String? = null,
    val error: String? = null,
    val notice: String? = null
)

private data class DreamsLocalState(
    val selectedProposalId: String? = null,
    val selectedEvidence: List<DreamSignal> = emptyList(),
    val applyingProposalId: String? = null,
    val error: String? = null,
    val notice: String? = null
)

class DreamsViewModel(private val container: AppContainer) : ViewModel() {
    private val localState = MutableStateFlow(DreamsLocalState())

    val state: StateFlow<DreamsUiState> = combine(
        container.dreamSettingsStore.state,
        container.dreamRepository.latestRun,
        container.dreamRepository.reviewableProposals,
        localState
    ) { settings, latestRun, proposals, local ->
        DreamsUiState(
            settings = settings,
            latestRun = latestRun,
            proposals = proposals,
            selectedProposalId = local.selectedProposalId,
            selectedEvidence = local.selectedEvidence,
            applyingProposalId = local.applyingProposalId,
            error = local.error,
            notice = local.notice
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DreamsUiState()
    )

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.dreamSettingsStore.setEnabled(enabled)
            localState.value = localState.value.copy(
                notice = if (enabled) "Aura Dreams is enabled. Nothing changes without your approval." else "Aura Dreams is paused.",
                error = null
            )
        }
    }

    fun setRequiresCharging(required: Boolean) {
        viewModelScope.launch { container.dreamSettingsStore.setRequiresCharging(required) }
    }

    fun setRequiresDeviceIdle(required: Boolean) {
        viewModelScope.launch { container.dreamSettingsStore.setRequiresDeviceIdle(required) }
    }

    fun runNow() {
        if (!state.value.settings.enabled) {
            localState.value = localState.value.copy(error = "Enable Aura Dreams before starting a run.", notice = null)
            return
        }
        container.dreamScheduler.runNow()
        localState.value = localState.value.copy(notice = "Dream run queued. Android will start it shortly.", error = null)
    }

    fun inspect(proposalId: String) {
        viewModelScope.launch {
            val evidence = container.dreamRepository.evidence(proposalId)
            localState.value = localState.value.copy(
                selectedProposalId = proposalId,
                selectedEvidence = evidence,
                error = null
            )
        }
    }

    fun apply(proposalId: String) {
        if (localState.value.applyingProposalId != null) return
        viewModelScope.launch {
            localState.value = localState.value.copy(applyingProposalId = proposalId, error = null, notice = null)
            val result = container.dreamProposalApplier.apply(proposalId)
            localState.value = when (result) {
                is DreamApplyResult.Applied -> localState.value.copy(
                    applyingProposalId = null,
                    selectedProposalId = null,
                    selectedEvidence = emptyList(),
                    notice = result.proposal.validationMessage,
                    error = null
                )
                is DreamApplyResult.Rejected -> localState.value.copy(
                    applyingProposalId = null,
                    error = result.reason
                )
                is DreamApplyResult.Failed -> localState.value.copy(
                    applyingProposalId = null,
                    error = result.reason
                )
            }
        }
    }

    fun dismiss(proposalId: String) {
        viewModelScope.launch {
            container.dreamRepository.dismiss(proposalId)
            localState.value = localState.value.copy(
                selectedProposalId = null,
                selectedEvidence = emptyList(),
                notice = "Proposal dismissed.",
                error = null
            )
        }
    }

    fun suppress(proposalId: String) {
        viewModelScope.launch {
            container.dreamRepository.suppress(proposalId)
            localState.value = localState.value.copy(
                selectedProposalId = null,
                selectedEvidence = emptyList(),
                notice = "Aura will not suggest this exact pattern again.",
                error = null
            )
        }
    }

    fun deleteHistory() {
        viewModelScope.launch {
            container.dreamRepository.deleteAll()
            localState.value = DreamsLocalState(notice = "Dream history deleted from this device.")
        }
    }

    fun clearMessage() {
        localState.value = localState.value.copy(error = null, notice = null)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(DreamsViewModel::class.java))
                return DreamsViewModel(container) as T
            }
        }
    }
}
