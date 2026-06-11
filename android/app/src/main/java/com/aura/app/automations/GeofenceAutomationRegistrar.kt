package com.aura.app.automations

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await

interface AutomationGeofenceRegistrar {
    suspend fun restore(automations: List<AutomationSpec>)
    suspend fun remove(automationId: String)
}

class GeofenceAutomationRegistrar(private val context: Context) : AutomationGeofenceRegistrar {
    private val geofencingClient = LocationServices.getGeofencingClient(context)

    override suspend fun restore(automations: List<AutomationSpec>) {
        val automationIds = automations.map { it.id }.filter { it.isNotBlank() }
        if (automationIds.isNotEmpty()) {
            runCatching { geofencingClient.removeGeofences(automationIds).await() }
        }
        val geofences = automations
            .filter { it.enabled && it.trigger.type == AutomationTriggerTypes.Geofence }
            .mapNotNull { it.toGeofence() }
        if (geofences.isEmpty() || !hasGeofencePermissions()) return

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_EXIT)
            .addGeofences(geofences)
            .build()
        geofencingClient.addGeofences(request, pendingIntent()).await()
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
