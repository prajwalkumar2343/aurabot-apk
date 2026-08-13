package com.aura.app.dreams

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aura.app.LauncherActivity
import com.aura.app.R

class DreamNotificationPublisher(private val context: Context) {
    @SuppressLint("MissingPermission")
    fun publish(report: DreamReport) {
        if (report.proposals.isEmpty() || !canNotify()) return
        createChannel()
        val openAura = PendingIntent.getActivity(
            context,
            NotificationId,
            Intent(context, LauncherActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle("Aura found ${report.proposals.size} improvement${if (report.proposals.size == 1) "" else "s"}")
            .setContentText("Review the Dream report. Nothing changed automatically.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Review the Dream report. Nothing changed automatically."))
            .setAutoCancel(true)
            .setContentIntent(openAura)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NotificationId, notification) }
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(ChannelId, "Aura Dreams", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Private morning reports with reviewable Aura improvement proposals."
            }
        )
    }

    private companion object {
        const val ChannelId = "aura_dreams"
        const val NotificationId = 7301
    }
}
