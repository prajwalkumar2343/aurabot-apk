package com.aura.app.automations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CancellationException

class AutomationFlowContinuationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val runId = intent.getStringExtra(AlarmAutomationFlowContinuationScheduler.EXTRA_RUN_ID) ?: return
        if (intent.action != AlarmAutomationFlowContinuationScheduler.alarmAction(runId)) return
        AutomationWorkScheduler(context.applicationContext).enqueueContinuation(runId)
    }
}

internal object AutomationFlowContinuationCoordinator {
    const val RetryDelayMillis = 15_000L
    private const val MaxRetryAttempts = 1

    suspend fun handle(
        retryAttempt: Int,
        resume: suspend () -> Unit,
        scheduleRetry: (Int) -> Unit,
        abandon: suspend (Exception) -> Unit
    ) {
        try {
            resume()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val retryScheduled = if (retryAttempt < MaxRetryAttempts) {
                try {
                    scheduleRetry(retryAttempt + 1)
                    true
                } catch (retryCancellation: CancellationException) {
                    retryCancellation.addSuppressed(error)
                    throw retryCancellation
                } catch (retryError: Exception) {
                    error.addSuppressed(retryError)
                    false
                }
            } else {
                false
            }
            if (!retryScheduled) {
                try {
                    abandon(error)
                } catch (abandonCancellation: CancellationException) {
                    abandonCancellation.addSuppressed(error)
                    throw abandonCancellation
                } catch (abandonError: Exception) {
                    error.addSuppressed(abandonError)
                }
            }
            throw error
        }
    }
}
