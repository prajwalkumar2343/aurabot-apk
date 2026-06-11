package com.aura.app.automations

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
        automations
            .map { it.id }
            .filter { it.isNotBlank() }
            .forEach { cancel(it) }
        automations
            .filter { it.enabled && it.trigger.type == AutomationTriggerTypes.Schedule }
            .forEach { schedule(it) }
    }

    fun schedule(spec: AutomationSpec) {
        if (!spec.enabled || spec.trigger.type != AutomationTriggerTypes.Schedule) {
            cancel(spec.id)
            return
        }
        val trigger = spec.trigger.schedule ?: return
        val nextAt = nextTriggerAt(trigger) ?: return
        val pendingIntent = pendingIntent(spec.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, nextAt, pendingIntent)
        }
    }

    override fun cancel(automationId: String) {
        alarmManager.cancel(pendingIntent(automationId))
    }

    private fun nextTriggerAt(trigger: ScheduleTrigger): Long? {
        return nextTriggerAt(trigger, ZonedDateTime.now(ZoneId.systemDefault()))
    }

    private fun pendingIntent(automationId: String): PendingIntent {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(
            context,
            automationId.hashCode(),
            Intent(context, ScheduleAutomationReceiver::class.java).putExtra(EXTRA_AUTOMATION_ID, automationId),
            flags
        )
    }

    companion object {
        const val EXTRA_AUTOMATION_ID = "automation_id"

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
