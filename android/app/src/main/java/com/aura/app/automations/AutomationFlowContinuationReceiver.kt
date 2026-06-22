package com.aura.app.automations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aura.app.AuraApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AutomationFlowContinuationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val runId = intent.getStringExtra(AlarmAutomationFlowContinuationScheduler.EXTRA_RUN_ID) ?: return
        val retryAttempt = intent.getIntExtra(AlarmAutomationFlowContinuationScheduler.EXTRA_RETRY_ATTEMPT, 0)
            .coerceAtLeast(0)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AutomationBroadcastWork.run(
                    operation = {
                        val container = (context.applicationContext as AuraApplication).container
                        AutomationFlowContinuationCoordinator.handle(
                            retryAttempt = retryAttempt,
                            resume = { container.automationEngine.resumeRun(runId) },
                            scheduleRetry = { nextAttempt ->
                                container.automationFlowContinuationScheduler.scheduleRetry(
                                    runId = runId,
                                    delayMillis = AutomationFlowContinuationCoordinator.RetryDelayMillis,
                                    retryAttempt = nextAttempt
                                )
                            },
                            abandon = { failure ->
                                withContext(NonCancellable) {
                                    container.automationEngine.failWaitingRun(
                                        runId,
                                        "Flow continuation delivery failed: " +
                                            (failure.message ?: failure::class.simpleName ?: "Unknown error")
                                    )
                                }
                            }
                        )
                    },
                    reportFailure = { error ->
                        Log.e(TAG, "Automation flow continuation failed for $runId", error)
                    }
                )
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "AutomationFlow"
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
