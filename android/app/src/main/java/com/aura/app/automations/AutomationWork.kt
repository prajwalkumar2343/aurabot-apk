package com.aura.app.automations

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aura.app.AuraApplication
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class AutomationWorkScheduler(private val context: Context) {
    private val workManager = WorkManager.getInstance(context)
    private val gson = Gson()

    fun enqueueEvent(deliveryId: String, event: AutomationEvent, reschedule: Boolean = false) {
        require(deliveryId.isNotBlank() && deliveryId.length <= MaxDeliveryIdLength) {
            "Automation delivery id is invalid"
        }
        require(event.type.isNotBlank() && event.type.length <= MaxEventFieldLength) {
            "Automation event type is invalid"
        }
        require(event.automationId == null || event.automationId.length <= MaxEventFieldLength) {
            "Automation event id is invalid"
        }
        val admittedEvent = event.copy(values = event.values + ("deliveryId" to deliveryId))
        val valuesJson = gson.toJson(admittedEvent.values)
        require(admittedEvent.values.size <= MaxEventValues && valuesJson.length <= MaxEventValuesJsonLength) {
            "Automation event context is too large"
        }
        val request = OneTimeWorkRequestBuilder<AutomationEventWorker>()
            .setInputData(
                Data.Builder()
                    .putString(WorkKeys.DeliveryId, deliveryId)
                    .putString(WorkKeys.EventType, admittedEvent.type)
                    .putString(WorkKeys.AutomationId, admittedEvent.automationId)
                    .putLong(WorkKeys.OccurredAt, admittedEvent.occurredAt)
                    .putString(WorkKeys.ValuesJson, valuesJson)
                    .putBoolean(WorkKeys.Reschedule, reschedule)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(WorkNames.AutomationTag)
            .build()
        workManager.enqueueUniqueWork(WorkNames.event(deliveryId), ExistingWorkPolicy.KEEP, request)
    }

    fun enqueueContinuation(runId: String) {
        val request = OneTimeWorkRequestBuilder<AutomationContinuationWorker>()
            .setInputData(Data.Builder().putString(WorkKeys.RunId, runId).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(WorkNames.AutomationTag)
            .build()
        workManager.enqueueUniqueWork(WorkNames.continuation(runId), ExistingWorkPolicy.KEEP, request)
    }

    fun enqueueApproval(runId: String, decision: String, expiresAt: Long) {
        val request = OneTimeWorkRequestBuilder<AutomationApprovalWorker>()
            .setInputData(
                Data.Builder()
                    .putString(WorkKeys.RunId, runId)
                    .putString(WorkKeys.Decision, decision)
                    .putLong(WorkKeys.ExpiresAt, expiresAt)
                    .build()
            )
            .addTag(WorkNames.AutomationTag)
            .build()
        workManager.enqueueUniqueWork(WorkNames.approval(runId), ExistingWorkPolicy.KEEP, request)
    }
}

class AutomationEventWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val event = inputData.automationEvent() ?: return Result.failure()
        val deliveryId = inputData.getString(WorkKeys.DeliveryId)?.takeIf { it.isNotBlank() }
            ?: return Result.failure()
        val container = (applicationContext as AuraApplication).container
        try {
            val admitted = container.automationRepository.admitEvent(deliveryId, event)
            if (admitted.status in setOf(AutomationEventStatus.Succeeded, AutomationEventStatus.Failed)) {
                return Result.success()
            }
            if (admitted.status == AutomationEventStatus.Running) {
                container.automationRepository.settleEvent(
                    deliveryId,
                    AutomationEventStatus.Failed,
                    "Automation event was interrupted after execution started; it was not replayed"
                )
                return Result.success()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return if (runAttemptCount < MaxWorkerRetries) Result.retry() else Result.failure()
        }
        if (inputData.getBoolean(WorkKeys.Reschedule, false)) {
            try {
                event.automationId?.let { id ->
                    container.automationRepository.get(id)?.takeIf { it.enabled }?.let {
                        container.scheduleAutomationScheduler.schedule(it)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                return if (runAttemptCount < MaxWorkerRetries) Result.retry() else Result.failure()
            }
        }
        val claimed = try {
            container.automationRepository.claimEvent(deliveryId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return if (runAttemptCount < MaxWorkerRetries) Result.retry() else Result.failure()
        }
        if (!claimed) return Result.success()
        return try {
            val results = container.automationEngine.handle(event)
            val failedRuns = results.count {
                it.status == AutomationRunStatus.Failed ||
                    it.status == AutomationRunStatus.OutcomeUnknown
            }
            val message = if (failedRuns == 0) {
                "Automation event completed for ${results.size} matching rule(s)"
            } else {
                "Automation event completed with $failedRuns failed rule(s)"
            }
            container.automationRepository.settleEvent(deliveryId, AutomationEventStatus.Succeeded, message)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            runCatching {
                container.automationRepository.settleEvent(
                    deliveryId,
                    AutomationEventStatus.Failed,
                    "Automation event failed after execution started: ${error.message ?: error::class.simpleName}"
                )
            }
            Result.failure()
        }
    }
}

class AutomationContinuationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val runId = inputData.getString(WorkKeys.RunId)?.takeIf { it.isNotBlank() } ?: return Result.failure()
        val engine = (applicationContext as AuraApplication).container.automationEngine
        return try {
            engine.resumeRun(runId)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (runAttemptCount < MaxWorkerRetries) {
                Result.retry()
            } else {
                engine.failWaitingRun(runId, "Flow continuation delivery failed after retries")
                Result.failure()
            }
        }
    }
}

class AutomationApprovalWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val runId = inputData.getString(WorkKeys.RunId)?.takeIf { it.isNotBlank() } ?: return Result.failure()
        val decision = inputData.getString(WorkKeys.Decision)
            ?.takeIf { it in AutomationApprovalDecisions.All } ?: return Result.failure()
        val expiresAt = inputData.getLong(WorkKeys.ExpiresAt, 0L).takeIf { it > 0L } ?: return Result.failure()
        val engine = (applicationContext as AuraApplication).container.automationEngine
        return try {
            if (System.currentTimeMillis() > expiresAt) {
                engine.failWaitingRun(runId, "Automation approval expired")
            } else if (decision == AutomationApprovalDecisions.Approve) {
                engine.resumeRun(runId, mapOf("approval" to "approved"))
            } else {
                engine.failWaitingRun(runId, "Automation was denied by the user")
            }
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure()
        }
    }
}

private fun Data.automationEvent(): AutomationEvent? {
    val type = getString(WorkKeys.EventType)?.takeIf { it.isNotBlank() } ?: return null
    val occurredAt = getLong(WorkKeys.OccurredAt, 0L).takeIf { it > 0L } ?: return null
    val valuesType = object : TypeToken<Map<String, String>>() {}.type
    val values = runCatching {
        Gson().fromJson<Map<String, String>>(getString(WorkKeys.ValuesJson).orEmpty(), valuesType)
    }.getOrNull() ?: return null
    return AutomationEvent(
        type = type,
        automationId = getString(WorkKeys.AutomationId),
        occurredAt = occurredAt,
        values = values
    )
}

private object WorkKeys {
    const val DeliveryId = "delivery_id"
    const val EventType = "event_type"
    const val AutomationId = "automation_id"
    const val OccurredAt = "occurred_at"
    const val ValuesJson = "values_json"
    const val Reschedule = "reschedule"
    const val RunId = "run_id"
    const val Decision = "decision"
    const val ExpiresAt = "expires_at"
}

private object WorkNames {
    const val AutomationTag = "aura-automation"
    fun event(deliveryId: String) = "automation-event:$deliveryId"
    fun continuation(runId: String) = "automation-continuation:$runId"
    fun approval(runId: String) = "automation-approval:$runId"
}

private const val MaxWorkerRetries = 2
private const val MaxDeliveryIdLength = 512
private const val MaxEventFieldLength = 128
private const val MaxEventValues = 64
private const val MaxEventValuesJsonLength = 7_000
