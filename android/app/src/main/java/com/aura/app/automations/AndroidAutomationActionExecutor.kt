package com.aura.app.automations

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aura.app.LauncherActivity
import com.aura.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class AndroidAutomationActionExecutor(
    private val context: Context,
    private val renderer: AutomationTemplateRenderer = AutomationTemplateRenderer(),
    private val crossAppController: CrossAppAutomationController = CrossAppAutomationController(context),
    private val smsDispatcher: AutomationSmsDispatcher = AndroidAutomationSmsDispatcher(context)
) : AutomationActionExecutor {
    override suspend fun execute(action: AutomationAction, event: AutomationEvent): AutomationActionResult =
        withContext(Dispatchers.IO) {
            when (action.type) {
                AutomationActionTypes.Notify -> notify(action, event)
                AutomationActionTypes.DraftMessage,
                AutomationActionTypes.EtaMessage -> draftMessage(action, event)
                AutomationActionTypes.DirectSms -> {
                    if (action.sendsDirectSms()) sendDirectSms(action, event) else draftMessage(action, event)
                }
                in AutomationActionTypeSets.CrossApp -> crossAppController.execute(action, event)
                else -> AutomationActionResult(action.type, AutomationRunStatus.Skipped, "Unsupported action type")
            }
        }

    private fun notify(action: AutomationAction, event: AutomationEvent): AutomationActionResult {
        createNotificationChannel()
        if (!canPostNotifications()) {
            return AutomationActionResult(action.type, AutomationRunStatus.Failed, "Notification permission is missing")
        }
        val title = action.title ?: "Aura automation"
        val body = renderer.render(action.messageTemplate ?: action.metadata["body"].orEmpty(), event.values)
            .ifBlank { "Automation '${event.automationId.orEmpty()}' ran." }
        val openIntent = PendingIntent.getActivity(
            context,
            nextRequestCode(),
            Intent(context, LauncherActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        return if (postNotification(notification)) {
            AutomationActionResult(action.type, AutomationRunStatus.Success, "Notification posted")
        } else {
            AutomationActionResult(action.type, AutomationRunStatus.Failed, "Notification permission is missing")
        }
    }

    private fun draftMessage(action: AutomationAction, event: AutomationEvent): AutomationActionResult {
        createNotificationChannel()
        val body = renderer.render(action.messageTemplate ?: defaultEtaTemplate(), event.values)
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${Uri.encode(action.recipientAddress.orEmpty())}")
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            nextRequestCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (!canPostNotifications()) {
            return AutomationActionResult(action.type, AutomationRunStatus.Failed, "Notification permission is missing")
        }
        val title = action.title ?: "Message ${action.recipientName ?: "recipient"}"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.notification_icon, "Review", pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        return if (postNotification(notification)) {
            AutomationActionResult(action.type, AutomationRunStatus.Success, "Draft message notification posted")
        } else {
            AutomationActionResult(action.type, AutomationRunStatus.Failed, "Notification permission is missing")
        }
    }

    private fun sendDirectSms(action: AutomationAction, event: AutomationEvent): AutomationActionResult {
        val recipient = action.recipientAddress?.trim().orEmpty()
        if (recipient.isBlank()) {
            return AutomationActionResult(action.type, AutomationRunStatus.Failed, "Direct SMS recipient is missing")
        }
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return AutomationActionResult(action.type, AutomationRunStatus.Failed, "SMS permission is missing")
        }
        val body = renderer.render(action.messageTemplate ?: defaultEtaTemplate(), event.values)
        if (body.isBlank()) {
            return AutomationActionResult(action.type, AutomationRunStatus.Failed, "Direct SMS body is empty")
        }
        smsDispatcher.send(recipient, body)
        return AutomationActionResult(action.type, AutomationRunStatus.Success, "SMS queued for delivery")
    }

    private fun defaultEtaTemplate(): String =
        "I just left {{placeName}}. My ETA is {{etaMinutes}} minutes."

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun postNotification(notification: android.app.Notification): Boolean {
        if (!canPostNotifications()) return false
        return runCatching {
            NotificationManagerCompat.from(context).notify(nextNotificationId(), notification)
        }.isSuccess
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Aura automations",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications and confirmations for Aura automations."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun nextRequestCode(): Int = requestCodes.incrementAndGet()
    private fun nextNotificationId(): Int = NOTIFICATION_ID_BASE + requestCodes.incrementAndGet()

    companion object {
        private const val CHANNEL_ID = "aura_automations"
        private const val NOTIFICATION_ID_BASE = 5200
        private val requestCodes = AtomicInteger(0)
    }
}
