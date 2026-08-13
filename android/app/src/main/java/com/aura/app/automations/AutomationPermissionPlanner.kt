package com.aura.app.automations

import android.Manifest
import android.os.Build
import com.aura.app.BuildConfig

class AutomationPermissionPlanner(
    private val directSmsAvailable: Boolean = BuildConfig.DIRECT_SMS_AVAILABLE
) {
    fun requiredPermissions(spec: AutomationSpec): Set<String> {
        val permissions = linkedSetOf<String>()
        val actions = spec.allActions()
        if (spec.trigger.type == AutomationTriggerTypes.Geofence) {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissions += Manifest.permission.ACCESS_BACKGROUND_LOCATION
            }
        }
        val hasApprovalCheckpoint = spec.flow?.steps.orEmpty().any { it.type == AutomationFlowStepTypes.Checkpoint }
        if (hasApprovalCheckpoint || actions.any { it.type == AutomationActionTypes.Notify || it.requireConfirmation }) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions += Manifest.permission.POST_NOTIFICATIONS
            }
        }
        if (directSmsAvailable && actions.any { it.sendsDirectSms() }) {
            permissions += Manifest.permission.SEND_SMS
        }
        return permissions
    }

    private fun AutomationSpec.allActions(): List<AutomationAction> =
        actions + flow?.steps.orEmpty().mapNotNull { it.action }

}
