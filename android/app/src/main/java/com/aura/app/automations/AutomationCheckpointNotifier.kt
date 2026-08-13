package com.aura.app.automations

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aura.app.R

data class AutomationCheckpointRequest(
    val runId: String,
    val automationName: String,
    val message: String,
    val expiresAt: Long
)

interface AutomationCheckpointNotifier {
    fun present(request: AutomationCheckpointRequest)
    fun cancel(runId: String)
}

object NoOpAutomationCheckpointNotifier : AutomationCheckpointNotifier {
    override fun present(request: AutomationCheckpointRequest) = Unit
    override fun cancel(runId: String) = Unit
}

class AndroidAutomationCheckpointNotifier(private val context: Context) : AutomationCheckpointNotifier {
    @SuppressLint("MissingPermission")
    override fun present(request: AutomationCheckpointRequest) {
        check(canPostNotifications()) { "Notification permission is required for automation approval" }
        createChannel()
        val approve = decisionIntent(request, AutomationApprovalDecisions.Approve)
        val deny = decisionIntent(request, AutomationApprovalDecisions.Deny)
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle("Approve ${request.automationName}")
            .setContentText(request.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(request.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(R.drawable.notification_icon, "Deny", deny)
            .addAction(R.drawable.notification_icon, "Approve", approve)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId(request.runId), notification)
        } catch (error: SecurityException) {
            throw IllegalStateException("Notification permission was revoked before automation approval could be shown", error)
        }
    }

    override fun cancel(runId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(runId))
    }

    private fun decisionIntent(request: AutomationCheckpointRequest, decision: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (request.runId + decision).hashCode(),
            Intent(context, AutomationCheckpointDecisionReceiver::class.java).apply {
                action = "${context.packageName}.automation.checkpoint.${request.runId}.$decision"
                putExtra(AutomationCheckpointDecisionReceiver.ExtraRunId, request.runId)
                putExtra(AutomationCheckpointDecisionReceiver.ExtraDecision, decision)
                putExtra(AutomationCheckpointDecisionReceiver.ExtraExpiresAt, request.expiresAt)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun createChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(ChannelId, "Automation approvals", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Approval requests before Aura performs irreversible automation actions."
            }
        )
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun notificationId(runId: String): Int = runId.hashCode()

    private companion object {
        const val ChannelId = "aura_automation_approvals"
    }
}

class AutomationCheckpointDecisionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val runId = intent.getStringExtra(ExtraRunId)?.takeIf { it.isNotBlank() } ?: return
        val decision = intent.getStringExtra(ExtraDecision)
            ?.takeIf { it in AutomationApprovalDecisions.All } ?: return
        if (intent.action != "${context.packageName}.automation.checkpoint.$runId.$decision") return
        val expiresAt = intent.getLongExtra(ExtraExpiresAt, 0L).takeIf { it > 0L } ?: return
        AutomationWorkScheduler(context.applicationContext).enqueueApproval(runId, decision, expiresAt)
    }

    companion object {
        const val ExtraRunId = "automation_run_id"
        const val ExtraDecision = "automation_approval_decision"
        const val ExtraExpiresAt = "automation_approval_expires_at"
    }
}

object AutomationApprovalDecisions {
    const val Approve = "approve"
    const val Deny = "deny"
    val All = setOf(Approve, Deny)
}

internal object AutomationCheckpointPolicy {
    fun expiresAt(startedAt: Long, metadata: Map<String, String>): Long {
        val requestedTtl = metadata["expiresInMillis"]?.toLongOrNull() ?: DefaultTtlMillis
        val ttl = requestedTtl.coerceIn(MinTtlMillis, MaxTtlMillis)
        return try {
            Math.addExact(startedAt, ttl)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
    }

    fun approvalMessage(spec: AutomationSpec, checkpointIndex: Int): String {
        val steps = spec.flow?.steps.orEmpty()
        val action = steps.drop(checkpointIndex + 1)
            .mapNotNull { it.action }
            .firstOrNull { it.hasAtMostOnceSideEffect() }
        if (action == null) {
            val checkpoint = steps.getOrNull(checkpointIndex)
            val label = checkpoint?.name?.takeIf { it.isNotBlank() } ?: checkpoint?.id ?: "checkpoint"
            return "Continue '${spec.name}' after $label"
        }
        if (action.sendsDirectSms()) {
            val recipient = action.recipientAddress?.trim().orEmpty().ifBlank { "the configured recipient" }
            val body = action.messageTemplate?.trim().orEmpty().take(160)
            return if (body.isBlank()) "Send an SMS to $recipient" else "Send an SMS to $recipient: $body"
        }
        return "Continue '${spec.name}' after the checkpoint"
    }

    private const val MinTtlMillis = 60_000L
    private const val DefaultTtlMillis = 86_400_000L
    private const val MaxTtlMillis = 604_800_000L
}
