package com.aura.app.automations

import android.Manifest
import android.os.Build

class AutomationPermissionPlanner {
    fun requiredPermissions(spec: AutomationSpec): Set<String> {
        val permissions = linkedSetOf<String>()
        if (spec.trigger.type == AutomationTriggerTypes.Geofence) {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissions += Manifest.permission.ACCESS_BACKGROUND_LOCATION
            }
        }
        if (spec.actions.any { it.type == AutomationActionTypes.Notify || it.requireConfirmation }) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions += Manifest.permission.POST_NOTIFICATIONS
            }
        }
        if (spec.actions.any { it.type == AutomationActionTypes.DirectSms }) {
            permissions += Manifest.permission.SEND_SMS
        }
        return permissions
    }
}
