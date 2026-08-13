package com.aura.app.automations

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CancellationException
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

interface AutomationScheduleScheduler {
    fun restore(automations: List<AutomationSpec>)
    fun cancel(automationId: String)
}

class ScheduleAutomationScheduler(private val context: Context) : AutomationScheduleScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun restore(automations: List<AutomationSpec>) {
        ScheduleRegistrationCoordinator.restore(
            automations = automations,
            cancel = ::cancel,
            schedule = ::schedule
        )
    }

    fun schedule(spec: AutomationSpec) {
        if (!spec.enabled || spec.trigger.type != AutomationTriggerTypes.Schedule) {
            cancel(spec.id)
            return
        }
        val trigger = spec.trigger.schedule ?: return
        val nextAt = nextTriggerAt(trigger) ?: return
        cancelLegacyPendingIntent(spec.id)
        val pendingIntent = createPendingIntent(spec.id, nextAt)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt, pendingIntent)
    }

    override fun cancel(automationId: String) {
        cancelPendingIntent(existingPendingIntent(automationId))
        cancelLegacyPendingIntent(automationId)
    }

    private fun nextTriggerAt(trigger: ScheduleTrigger): Long? {
        return nextTriggerAt(trigger, ZonedDateTime.now(ZoneId.systemDefault()))
    }

    private fun createPendingIntent(automationId: String, triggerAt: Long): PendingIntent {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(
            context,
            automationId.hashCode(),
            intent(automationId, includeIdentity = true, triggerAt = triggerAt),
            flags
        )
    }

    private fun existingPendingIntent(automationId: String): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            automationId.hashCode(),
            intent(automationId, includeIdentity = true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )

    private fun cancelLegacyPendingIntent(automationId: String) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            automationId.hashCode(),
            intent(automationId, includeIdentity = false),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        cancelPendingIntent(pendingIntent)
    }

    private fun cancelPendingIntent(pendingIntent: PendingIntent?) {
        pendingIntent ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun intent(automationId: String, includeIdentity: Boolean, triggerAt: Long? = null): Intent =
        Intent(context, ScheduleAutomationReceiver::class.java).apply {
            if (includeIdentity) action = alarmAction(automationId)
            putExtra(EXTRA_AUTOMATION_ID, automationId)
            triggerAt?.let { putExtra(EXTRA_TRIGGER_AT, it) }
        }

    companion object {
        const val EXTRA_AUTOMATION_ID = "automation_id"
        const val EXTRA_TRIGGER_AT = "automation_trigger_at"
        private const val ACTION_PREFIX = "com.aura.app.automation.schedule."

        internal fun alarmAction(automationId: String): String = ACTION_PREFIX + automationId

        internal fun nextTriggerAt(trigger: ScheduleTrigger, now: ZonedDateTime): Long? {
            if (trigger.mode == "interval") {
                val minutes = trigger.intervalMinutes?.takeIf { it > 0 } ?: return null
                return now.toInstant().toEpochMilli() + minutes * 60_000L
            }
            val localTime = runCatching { LocalTime.parse(trigger.localTime ?: "09:00") }.getOrNull() ?: return null
            var next = now.withHour(localTime.hour).withMinute(localTime.minute).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val allowedDays = trigger.daysOfWeek.filter { it in 1..7 }.toSet()
            if (allowedDays.isNotEmpty()) {
                repeat(7) {
                    if (next.dayOfWeek.value in allowedDays) {
                        return next.toInstant().toEpochMilli()
                    }
                    next = next.plusDays(1)
                }
                return null
            }
            return next.toInstant().toEpochMilli()
        }
    }
}

internal object ScheduleRegistrationCoordinator {
    fun restore(
        automations: List<AutomationSpec>,
        cancel: (String) -> Unit,
        schedule: (AutomationSpec) -> Unit
    ) {
        val failures = mutableListOf<Exception>()
        automations
            .map { it.id }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { automationId ->
                captureFailure("cancel", automationId) { cancel(automationId) }?.let(failures::add)
            }
        automations
            .filter {
                it.id.isNotBlank() &&
                    it.enabled &&
                    it.trigger.type == AutomationTriggerTypes.Schedule
            }
            .distinctBy { it.id }
            .forEach { spec ->
                captureFailure("schedule", spec.id) { schedule(spec) }?.let(failures::add)
            }
        if (failures.isNotEmpty()) throw ScheduleRegistrationException(failures)
    }

    private fun captureFailure(operation: String, automationId: String, block: () -> Unit): Exception? =
        try {
            block()
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            IllegalStateException("Failed to $operation automation alarm '$automationId'", error)
        }
}

internal class ScheduleRegistrationException(failures: List<Exception>) : Exception(
    failures.joinToString(
        prefix = "Schedule restoration failed: ",
        separator = "; "
    ) { it.message ?: it::class.simpleName ?: "Unknown error" },
    failures.firstOrNull()
) {
    init {
        failures.drop(1).forEach(::addSuppressed)
    }
}
