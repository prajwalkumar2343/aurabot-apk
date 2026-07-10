package com.aura.app.dreams

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class DreamScheduler(private val context: Context) {
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    fun reconcile(settings: DreamSettings) {
        if (!settings.enabled) {
            workManager.cancelUniqueWork(NightlyWorkName)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiresCharging(settings.requiresCharging)
            .setRequiresDeviceIdle(settings.requiresDeviceIdle)
            .setRequiresBatteryNotLow(settings.requireBatteryNotLow)
            .build()
        val request = PeriodicWorkRequestBuilder<DreamWorker>(
            24, TimeUnit.HOURS,
            6, TimeUnit.HOURS
        )
            .setInitialDelay(initialDelayUntilTwoAm())
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(WorkTag)
            .build()
        workManager.enqueueUniquePeriodicWork(
            NightlyWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun runNow() {
        val request = OneTimeWorkRequestBuilder<DreamWorker>()
            .addTag(WorkTag)
            .build()
        workManager.enqueueUniqueWork(ManualWorkName, ExistingWorkPolicy.KEEP, request)
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(NightlyWorkName)
        workManager.cancelUniqueWork(ManualWorkName)
    }

    private fun initialDelayUntilTwoAm(now: ZonedDateTime = ZonedDateTime.now()): Duration {
        var next = now.toLocalDate().atTime(2, 0).atZone(now.zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    companion object {
        const val NightlyWorkName = "aura-dreams-nightly"
        const val ManualWorkName = "aura-dreams-manual"
        const val WorkTag = "aura-dreams"
    }
}
