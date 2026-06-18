package com.aura.app.automations

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

interface AutomationGeofenceRegistrar {
    suspend fun restore(automations: List<AutomationSpec>)
    suspend fun remove(automationId: String)
}

class GeofenceAutomationRegistrar(private val context: Context) : AutomationGeofenceRegistrar {
    private val geofencingClient = LocationServices.getGeofencingClient(context)

    @SuppressLint("MissingPermission")
    override suspend fun restore(automations: List<AutomationSpec>) {
        val automationIds = automations.map { it.id }.filter { it.isNotBlank() }
        val enabledGeofenceAutomations = automations
            .filter { it.enabled && it.trigger.type == AutomationTriggerTypes.Geofence }
        val geofences = enabledGeofenceAutomations
            .mapNotNull { it.toGeofence() }
        GeofenceRegistrationCoordinator.restore(
            automationIds = automationIds,
            hasEnabledGeofences = geofences.isNotEmpty(),
            hasPermissions = hasGeofencePermissions(),
            removeGeofences = { ids -> geofencingClient.removeGeofences(ids).await() },
            addGeofences = {
                val request = GeofencingRequest.Builder()
                    .setInitialTrigger(
                        GeofenceRegistrationCoordinator.initialTrigger(
                            enabledGeofenceAutomations.mapNotNull { it.trigger.geofence?.transition }
                        )
                    )
                    .addGeofences(geofences)
                    .build()
                geofencingClient.addGeofences(request, pendingIntent()).await()
            }
        )
    }

    override suspend fun remove(automationId: String) {
        geofencingClient.removeGeofences(listOf(automationId)).await()
    }

    fun hasGeofencePermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        return fine && background
    }

    private fun AutomationSpec.toGeofence(): Geofence? {
        val config = trigger.geofence ?: return null
        val transition = when (config.transition) {
            AutomationTriggerTypes.GeofenceEnter -> Geofence.GEOFENCE_TRANSITION_ENTER
            else -> Geofence.GEOFENCE_TRANSITION_EXIT
        }
        return Geofence.Builder()
            .setRequestId(id)
            .setCircularRegion(config.latitude, config.longitude, config.radiusMeters)
            .setTransitionTypes(transition)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()
    }

    private fun pendingIntent(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, GeofenceTransitionReceiver::class.java),
            flags
        )
    }
}

internal object GeofenceRegistrationCoordinator {
    fun initialTrigger(transitions: List<String>): Int =
        transitions.fold(0) { triggerMask, transition ->
            triggerMask or when (transition) {
                AutomationTriggerTypes.GeofenceEnter -> GeofencingRequest.INITIAL_TRIGGER_ENTER
                else -> GeofencingRequest.INITIAL_TRIGGER_EXIT
            }
        }

    suspend fun restore(
        automationIds: List<String>,
        hasEnabledGeofences: Boolean,
        hasPermissions: Boolean,
        removeGeofences: suspend (List<String>) -> Unit,
        addGeofences: suspend () -> Unit
    ) {
        val failures = mutableListOf<Exception>()
        if (automationIds.isNotEmpty()) {
            captureFailure { removeGeofences(automationIds) }?.let(failures::add)
        }
        if (hasEnabledGeofences) {
            if (!hasPermissions) {
                failures += IllegalStateException(
                    "Fine and background location permissions are required to arm geofence automations"
                )
            } else {
                captureFailure(addGeofences)?.let(failures::add)
            }
        }
        if (failures.isNotEmpty()) throw GeofenceRegistrationException(failures)
    }

    private suspend fun captureFailure(block: suspend () -> Unit): Exception? =
        try {
            block()
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            error
        }
}

internal class GeofenceRegistrationException(failures: List<Exception>) : Exception(
    failures.joinToString(
        prefix = "Geofence registration failed: ",
        separator = "; "
    ) { it.message ?: it::class.simpleName ?: "Unknown error" },
    failures.firstOrNull()
) {
    init {
        failures.drop(1).forEach(::addSuppressed)
    }
}
