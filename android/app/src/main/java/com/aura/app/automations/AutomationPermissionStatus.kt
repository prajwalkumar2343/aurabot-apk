package com.aura.app.automations

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class AutomationPermissionStatus(
    val permission: String,
    val label: String,
    val granted: Boolean,
    val settingsTarget: AutomationPermissionSettingsTarget
)

enum class AutomationPermissionSettingsTarget {
    RuntimePermission,
    Notifications,
    Location,
    Sms,
    AppDetails
}

class AutomationPermissionStatusResolver(
    private val runtimePermissionGranted: (String) -> Boolean,
    private val notificationsEnabled: () -> Boolean
) {
    fun statuses(requiredPermissions: Set<String>): List<AutomationPermissionStatus> =
        requiredPermissions.map { permission ->
            AutomationPermissionStatus(
                permission = permission,
                label = labelFor(permission),
                granted = isGranted(permission),
                settingsTarget = settingsTargetFor(permission)
            )
        }

    fun statuses(spec: AutomationSpec, planner: AutomationPermissionPlanner = AutomationPermissionPlanner()): List<AutomationPermissionStatus> =
        statuses(planner.requiredPermissions(spec))

    private fun isGranted(permission: String): Boolean =
        when (permission) {
            Manifest.permission.POST_NOTIFICATIONS -> {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    runtimePermissionGranted(permission) && notificationsEnabled()
            }
            else -> runtimePermissionGranted(permission)
        }

    private fun labelFor(permission: String): String =
        when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION -> "Location"
            Manifest.permission.ACCESS_COARSE_LOCATION -> "Approximate location"
            Manifest.permission.ACCESS_BACKGROUND_LOCATION -> "Background location"
            Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
            Manifest.permission.SEND_SMS -> "SMS"
            else -> permission.substringAfterLast('.').replace('_', ' ').lowercase()
                .replaceFirstChar { it.uppercase() }
        }

    private fun settingsTargetFor(permission: String): AutomationPermissionSettingsTarget =
        when (permission) {
            Manifest.permission.POST_NOTIFICATIONS -> AutomationPermissionSettingsTarget.Notifications
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION -> AutomationPermissionSettingsTarget.Location
            Manifest.permission.SEND_SMS -> AutomationPermissionSettingsTarget.Sms
            else -> AutomationPermissionSettingsTarget.AppDetails
        }
}

class AndroidAutomationPermissionStatusResolver(private val context: Context) {
    private val resolver = AutomationPermissionStatusResolver(
        runtimePermissionGranted = { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        },
        notificationsEnabled = {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    )

    fun statuses(spec: AutomationSpec, planner: AutomationPermissionPlanner = AutomationPermissionPlanner()): List<AutomationPermissionStatus> =
        resolver.statuses(spec, planner)
}

object AutomationPermissionSettingsIntents {
    fun intentFor(context: Context, status: AutomationPermissionStatus): Intent =
        when (status.settingsTarget) {
            AutomationPermissionSettingsTarget.Notifications -> notificationSettingsIntent(context)
            AutomationPermissionSettingsTarget.Location,
            AutomationPermissionSettingsTarget.Sms,
            AutomationPermissionSettingsTarget.AppDetails,
            AutomationPermissionSettingsTarget.RuntimePermission -> appDetailsIntent(context)
        }

    fun appDetailsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri()
        )

    private fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
}
