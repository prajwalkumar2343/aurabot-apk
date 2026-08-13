package com.aura.app.automations

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationPermissionStatusResolverTest {
    @Test
    fun statusesMarkRuntimePermissionsGrantedIndividually() {
        val resolver = resolver(grantedRuntimePermissions = setOf(Manifest.permission.SEND_SMS))

        val statuses = resolver.statuses(
            setOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )

        assertTrue(statuses.single { it.permission == Manifest.permission.SEND_SMS }.granted)
        assertFalse(statuses.single { it.permission == Manifest.permission.ACCESS_FINE_LOCATION }.granted)
    }

    @Test
    fun statusesUseTargetedSettingsCategories() {
        val statuses = resolver().statuses(
            setOf(
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.SEND_SMS
            )
        ).associateBy { it.permission }

        assertEquals(
            AutomationPermissionSettingsTarget.Notifications,
            statuses.getValue(Manifest.permission.POST_NOTIFICATIONS).settingsTarget
        )
        assertEquals(
            AutomationPermissionSettingsTarget.Location,
            statuses.getValue(Manifest.permission.ACCESS_BACKGROUND_LOCATION).settingsTarget
        )
        assertEquals(
            AutomationPermissionSettingsTarget.Sms,
            statuses.getValue(Manifest.permission.SEND_SMS).settingsTarget
        )
    }

    @Test
    fun plannerNeverAddsAccessibilityPermission() {
        val spec = AutomationSpec(
            name = "supported",
            actions = listOf(AutomationAction(type = AutomationActionTypes.Notify))
        )

        assertTrue(AutomationPermissionPlanner().requiredPermissions(spec).none {
            it.contains("ACCESSIBILITY", ignoreCase = true)
        })
    }

    private fun resolver(
        grantedRuntimePermissions: Set<String> = emptySet(),
        notificationsEnabled: Boolean = true
    ) = AutomationPermissionStatusResolver(
        runtimePermissionGranted = { it in grantedRuntimePermissions },
        notificationsEnabled = { notificationsEnabled }
    )
}
