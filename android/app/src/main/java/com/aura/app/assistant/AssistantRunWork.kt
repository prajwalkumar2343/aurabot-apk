package com.aura.app.assistant

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

interface AssistantRunScheduler {
    fun enqueue(runId: String)
    fun cancel(runId: String)
}

class WorkManagerAssistantRunScheduler(context: Context) : AssistantRunScheduler {
    private val appContext = context.applicationContext

    override fun enqueue(runId: String) {
        val normalizedRunId = runId.trim()
        if (normalizedRunId.isEmpty()) return
        val request = OneTimeWorkRequestBuilder<AssistantRunSyncWorker>()
            .setInputData(workDataOf(KEY_RUN_ID to normalizedRunId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInitialDelay(10, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            uniqueWorkName(normalizedRunId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    override fun cancel(runId: String) {
        val normalizedRunId = runId.trim()
        if (normalizedRunId.isNotEmpty()) {
            WorkManager.getInstance(appContext).cancelUniqueWork(uniqueWorkName(normalizedRunId))
        }
    }

    private companion object {
        const val KEY_RUN_ID = "assistant_run_id"
        const val WORK_TAG = "assistant_run_sync"

        fun uniqueWorkName(runId: String) = "assistant-run-sync:$runId"
    }
}

class AssistantRunSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val runId = inputData.getString(KEY_RUN_ID)?.trim()
            ?: return Result.failure()
        val container = (applicationContext as com.aura.app.AuraApplication).container
        return try {
            if (!container.assistantRunSurfaceRepository.shouldSyncManagedRun(runId)) {
                return Result.success()
            }
            when (container.assistantRunSurfaceRepository.persistManagedSnapshot(
                container.assistantRepository.managedRun(runId)
            )) {
                AssistantRunSyncResult.Retry -> Result.retry()
                AssistantRunSyncResult.Finished -> Result.success()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            Result.retry()
        } catch (error: HttpException) {
            if (error.code() == 408 || error.code() == 429 || error.code() >= 500) {
                Result.retry()
            } else {
                if (container.sessionStore.serviceMode() == AssistantRunMode.Managed.wireValue) {
                    container.assistantRunSurfaceRepository.fail(
                        runId,
                        error.userFacingMessage("Could not restore the assistant run.")
                    )
                }
                Result.failure()
            }
        } catch (error: Exception) {
            Result.retry()
        }
    }

    private companion object {
        const val KEY_RUN_ID = "assistant_run_id"
    }
}
