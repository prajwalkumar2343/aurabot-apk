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
            enabledGeofenceCount = 0,
            hasPermissions = false,
            removeGeofences = { events += "remove:${it.joinToString()}" },
            addGeofences = { events += "add" }
        )

        assertEquals(listOf("remove:old-geofence"), events)
    }

    @Test
    fun restoreReportsMissingPermissionsBeforeChangingRegistrations() = runTest {
        val events = mutableListOf<String>()

        val failure = runCatching {
            GeofenceRegistrationCoordinator.restore(
                automationIds = listOf("home"),
                enabledGeofenceCount = 1,
                hasPermissions = false,
                removeGeofences = { events += "remove" },
                addGeofences = { events += "add" }
            )
        }.exceptionOrNull()

        assertEquals(
            "Geofence registration failed: Fine and background location permissions are required to arm geofence automations",
            failure?.message
        )
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun restoreRejectsPlatformCapacityOverflowBeforeChangingRegistrations() = runTest {
        val events = mutableListOf<String>()

        val failure = runCatching {
            GeofenceRegistrationCoordinator.restore(
                automationIds = List(101) { "geofence-$it" },
                enabledGeofenceCount = 101,
                hasPermissions = true,
                removeGeofences = { events += "remove" },
                addGeofences = { events += "add" }
            )
        }.exceptionOrNull()

        assertEquals(
            "Geofence registration failed: At most 100 geofence automations can be enabled; " +
                "101 are currently enabled",
            failure?.message
        )
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun restoreAllowsThePlatformCapacityBoundary() = runTest {
        val events = mutableListOf<String>()

        GeofenceRegistrationCoordinator.restore(
            automationIds = List(100) { "geofence-$it" },
            enabledGeofenceCount = GeofenceRegistrationCoordinator.MaxActiveGeofences,
            hasPermissions = true,
            removeGeofences = { events += "remove:${it.size}" },
            addGeofences = { events += "add" }
        )

        assertEquals(listOf("remove:100", "add"), events)
    }

    @Test
    fun restoreAttemptsAddAfterRemovalFailureAndReportsBothFailures() = runTest {
        val events = mutableListOf<String>()

        val failure = runCatching {
            GeofenceRegistrationCoordinator.restore(
                automationIds = listOf("work"),
                enabledGeofenceCount = 1,
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
                enabledGeofenceCount = 1,
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
