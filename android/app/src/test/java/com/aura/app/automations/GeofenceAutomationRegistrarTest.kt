package com.aura.app.automations

import com.google.android.gms.location.GeofencingRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceAutomationRegistrarTest {
    @Test
    fun initialTriggerMatchesConfiguredTransitions() {
        assertEquals(
            GeofencingRequest.INITIAL_TRIGGER_ENTER,
            GeofenceRegistrationCoordinator.initialTrigger(listOf(AutomationTriggerTypes.GeofenceEnter))
        )
        assertEquals(
            GeofencingRequest.INITIAL_TRIGGER_EXIT,
            GeofenceRegistrationCoordinator.initialTrigger(listOf(AutomationTriggerTypes.GeofenceExit))
        )
        assertEquals(
            GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_EXIT,
            GeofenceRegistrationCoordinator.initialTrigger(
                listOf(AutomationTriggerTypes.GeofenceEnter, AutomationTriggerTypes.GeofenceExit)
            )
        )
    }

    @Test
    fun restoreRemovesStaleRegistrationsWithoutAddingWhenNoneAreEnabled() = runTest {
        val events = mutableListOf<String>()

        GeofenceRegistrationCoordinator.restore(
            automationIds = listOf("old-geofence"),
            hasEnabledGeofences = false,
            hasPermissions = false,
            removeGeofences = { events += "remove:${it.joinToString()}" },
            addGeofences = { events += "add" }
        )

        assertEquals(listOf("remove:old-geofence"), events)
    }

    @Test
    fun restoreReportsMissingPermissionsWithoutAttemptingAdd() = runTest {
        val events = mutableListOf<String>()

        val failure = runCatching {
            GeofenceRegistrationCoordinator.restore(
                automationIds = listOf("home"),
                hasEnabledGeofences = true,
                hasPermissions = false,
                removeGeofences = { events += "remove" },
                addGeofences = { events += "add" }
            )
        }.exceptionOrNull()

        assertEquals(
            "Geofence registration failed: Fine and background location permissions are required to arm geofence automations",
            failure?.message
        )
        assertEquals(listOf("remove"), events)
    }

    @Test
    fun restoreAttemptsAddAfterRemovalFailureAndReportsBothFailures() = runTest {
        val events = mutableListOf<String>()

        val failure = runCatching {
            GeofenceRegistrationCoordinator.restore(
                automationIds = listOf("work"),
                hasEnabledGeofences = true,
                hasPermissions = true,
                removeGeofences = {
                    events += "remove"
                    error("remove unavailable")
                },
                addGeofences = {
                    events += "add"
                    error("add unavailable")
                }
            )
        }.exceptionOrNull()

        assertEquals(
            "Geofence registration failed: remove unavailable; add unavailable",
            failure?.message
        )
        assertEquals(listOf("remove", "add"), events)
    }

    @Test
    fun restorePropagatesCancellationWithoutAttemptingAdd() = runTest {
        val events = mutableListOf<String>()

        val failure = runCatching {
            GeofenceRegistrationCoordinator.restore(
                automationIds = listOf("work"),
                hasEnabledGeofences = true,
                hasPermissions = true,
                removeGeofences = {
                    events += "remove"
                    throw CancellationException("cancelled")
                },
                addGeofences = { events += "add" }
            )
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(listOf("remove"), events)
    }
}
