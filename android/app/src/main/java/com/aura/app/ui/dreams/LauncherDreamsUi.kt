package com.aura.app.ui.dreams

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.app.dreams.DreamProposal
import com.aura.app.dreams.DreamProposalStatus
import com.aura.app.dreams.DreamProposalType
import com.aura.app.dreams.DreamRisk
import com.aura.app.dreams.DreamRunStatus
import com.aura.app.ui.Header
import com.aura.app.ui.ScreenShell
import com.aura.app.ui.bounceClick
import java.text.DateFormat
import java.util.Date

@Composable
fun DreamsScreen(
    viewModel: DreamsViewModel,
    wallpaperUri: String?,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmApply by remember { mutableStateOf<DreamProposal?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenShell(wallpaperUri = wallpaperUri) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .bounceClick(showRipple = true, onClick = onBack)
                    .padding(8.dp)
            ) {
                Text("← BACK", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Header("AURA DREAMS", "Private, evidence-backed improvements prepared while your phone is idle.")

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DreamsControlCard(
                enabled = state.settings.enabled,
                requiresCharging = state.settings.requiresCharging,
                requiresDeviceIdle = state.settings.requiresDeviceIdle,
                onEnabled = viewModel::setEnabled,
                onRequiresCharging = viewModel::setRequiresCharging,
                onRequiresDeviceIdle = viewModel::setRequiresDeviceIdle,
                onRunNow = viewModel::runNow
            )

            state.error?.let { MessageCard(it, isError = true, onDismiss = viewModel::clearMessage) }
            state.notice?.let { MessageCard(it, isError = false, onDismiss = viewModel::clearMessage) }
            DreamRunCard(state.latestRun, state.proposals.size)

            if (state.proposals.isEmpty()) {
                EmptyDreamsCard(state.latestRun?.status)
            } else {
                state.proposals.forEach { proposal ->
                    DreamProposalCard(
                        proposal = proposal,
                        applying = state.applyingProposalId == proposal.id,
                        selected = state.selectedProposalId == proposal.id,
                        evidence = if (state.selectedProposalId == proposal.id) state.selectedEvidence.map { it.summary } else emptyList(),
                        onInspect = { viewModel.inspect(proposal.id) },
                        onApply = { confirmApply = proposal },
                        onDismiss = { viewModel.dismiss(proposal.id) },
                        onSuppress = { viewModel.suppress(proposal.id) }
                    )
                }
            }

            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Text("  DELETE DREAM HISTORY")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    confirmApply?.let { proposal ->
        AlertDialog(
            onDismissRequest = { confirmApply = null },
            title = { Text("Apply this proposal?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(proposal.title, fontWeight = FontWeight.Bold)
                    Text(proposal.validationMessage)
                    Text("Aura will revalidate the current target before changing anything.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    confirmApply = null
                    viewModel.apply(proposal.id)
                }) { Text("APPLY") }
            },
            dismissButton = { TextButton(onClick = { confirmApply = null }) { Text("CANCEL") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Dream history?") },
            text = { Text("This removes Dream runs, evidence, proposals, and suppressions from this device. It does not change existing automations, tasks, or mini apps.") },
            confirmButton = {
                Button(onClick = {
                    confirmDelete = false
                    viewModel.deleteHistory()
                }) { Text("DELETE") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("CANCEL") } }
        )
    }
}

@Composable
private fun DreamsControlCard(
    enabled: Boolean,
    requiresCharging: Boolean,
    requiresDeviceIdle: Boolean,
    onEnabled: (Boolean) -> Unit,
    onRequiresCharging: (Boolean) -> Unit,
    onRequiresDeviceIdle: (Boolean) -> Unit,
    onRunNow: () -> Unit
) {
    DreamCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.padding(6.dp))
            Column(Modifier.weight(1f)) {
                Text("NIGHTLY DREAMS", fontWeight = FontWeight.Bold)
                Text("Nothing changes without approval.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = enabled, onCheckedChange = onEnabled)
        }
        SettingToggle("Only while charging", requiresCharging, onRequiresCharging)
        SettingToggle("Only while device is idle", requiresDeviceIdle, onRequiresDeviceIdle)
        FilledTonalButton(
            onClick = onRunNow,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Text(" DREAM NOW")
        }
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DreamRunCard(run: com.aura.app.dreams.DreamRun?, pendingCount: Int) {
    DreamCard {
        Text("LATEST REPORT", fontWeight = FontWeight.Bold)
        if (run == null) {
            Text("No Dream has run yet.")
        } else {
            Text(
                when (run.status) {
                    DreamRunStatus.Running -> "Dreaming · ${run.stage.name}"
                    DreamRunStatus.Completed -> "$pendingCount proposal${if (pendingCount == 1) "" else "s"} ready · nothing changed automatically"
                    DreamRunStatus.Failed -> "The last Dream could not finish"
                    DreamRunStatus.Cancelled -> "The last Dream was cancelled"
                }
            )
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(run.updatedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (run.warningCount > 0) {
                Text("${run.warningCount} source warning${if (run.warningCount == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall)
            }
            run.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun EmptyDreamsCard(status: DreamRunStatus?) {
    DreamCard {
        Text(
            if (status == DreamRunStatus.Completed) "Aura found no strong patterns this time." else "Aura is still learning your routines.",
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Dreams waits for repeated evidence instead of turning coincidences into advice.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DreamProposalCard(
    proposal: DreamProposal,
    applying: Boolean,
    selected: Boolean,
    evidence: List<String>,
    onInspect: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onSuppress: () -> Unit
) {
    DreamCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    proposalLabel(proposal.type),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(proposal.title, fontWeight = FontWeight.Bold)
            }
            Text("${(proposal.confidence * 100).toInt()}%", fontWeight = FontWeight.Bold)
        }
        Text(proposal.summary)
        if (proposal.status != DreamProposalStatus.PendingReview) {
            Text("STATUS: ${proposal.status.name.uppercase()}", style = MaterialTheme.typography.labelSmall)
        }
        Text(proposal.rationale, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "${proposal.risk.name.uppercase()} RISK · ${proposal.validationMessage}",
            style = MaterialTheme.typography.labelSmall,
            color = riskColor(proposal.risk)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onInspect, modifier = Modifier.weight(1f)) { Text(if (selected) "EVIDENCE" else "WHY?") }
            if (proposal.applicable && proposal.status == DreamProposalStatus.PendingReview) {
                Button(onClick = onApply, enabled = !applying, modifier = Modifier.weight(1f)) {
                    Text(if (applying) "APPLYING" else "APPLY")
                }
            }
        }
        if (selected) {
            if (evidence.isEmpty()) {
                Text("No retained evidence is available.", style = MaterialTheme.typography.bodySmall)
            } else {
                evidence.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("DISMISS") }
            TextButton(onClick = onSuppress, modifier = Modifier.weight(1f)) { Text("NEVER AGAIN") }
        }
    }
}

@Composable
private fun MessageCard(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

@Composable
private fun DreamCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

private fun proposalLabel(type: DreamProposalType): String = when (type) {
    DreamProposalType.AutomationRepair -> "AUTOMATION REPAIR"
    DreamProposalType.TodoRescue -> "TASK RESCUE"
    DreamProposalType.MiniAppEvolution -> "MINI-APP EVOLUTION"
    DreamProposalType.RoutineAutomation -> "ROUTINE"
}

private fun riskColor(risk: DreamRisk): Color = when (risk) {
    DreamRisk.Low -> Color(0xFF16A34A)
    DreamRisk.Medium -> Color(0xFFD97706)
    DreamRisk.High -> Color(0xFFDC2626)
}
