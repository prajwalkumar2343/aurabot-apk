package com.aura.app.automations

import android.Manifest
import android.os.Build

class AutomationPermissionPlanner {
    fun requiredPermissions(spec: AutomationSpec): Set<String> {
        val permissions = linkedSetOf<String>()
        val actions = spec.allActions()
        if (spec.trigger.type == AutomationTriggerTypes.Geofence) {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissions += Manifest.permission.ACCESS_BACKGROUND_LOCATION
            }
        }
        if (actions.any { it.type == AutomationActionTypes.Notify || it.requireConfirmation }) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions += Manifest.permission.POST_NOTIFICATIONS
            }
        }
        if (actions.any { it.type == AutomationActionTypes.DirectSms }) {
            permissions += Manifest.permission.SEND_SMS
        }
        if (actions.any { it.type in AutomationActionTypeSets.CrossApp }) {
            permissions += AccessibilityService
        }
        return permissions
    }

    private fun AutomationSpec.allActions(): List<AutomationAction> =
        actions + flow?.steps.orEmpty().mapNotNull { it.action }

    companion object {
        const val AccessibilityService = "aura.permission.ACCESSIBILITY_SERVICE"
    }
}
