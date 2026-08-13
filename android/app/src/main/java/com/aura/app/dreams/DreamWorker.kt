package com.aura.app.dreams

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aura.app.AuraApplication
import kotlinx.coroutines.flow.first

class DreamWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AuraApplication).container
        val settings = container.dreamSettingsStore.state.first()
        if (!settings.enabled) return Result.success()
        val end = System.currentTimeMillis()
        val report = container.dreamOrchestrator.run(
            DreamWindow(end - EvidenceWindowMillis, end)
        )
        return when (report.run.status) {
            DreamRunStatus.Completed -> {
                container.dreamNotificationPublisher.publish(report)
                Result.success()
            }
            DreamRunStatus.Cancelled -> Result.failure()
            DreamRunStatus.Failed -> Result.failure()
            DreamRunStatus.Running -> Result.success()
        }
    }

    private companion object {
        const val EvidenceWindowMillis = 7L * 24L * 60L * 60L * 1_000L
    }
}
