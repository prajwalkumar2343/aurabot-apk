package com.aura.app.automations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CancellationException

class GeofenceTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null || geofencingEvent.hasError()) return
        val eventType = when (geofencingEvent.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> AutomationEvents.GeofenceEnter
            Geofence.GEOFENCE_TRANSITION_EXIT -> AutomationEvents.GeofenceExit
            else -> return
        }
        val location = geofencingEvent.triggeringLocation
        val occurredAt = location?.time?.takeIf { it > 0L } ?: System.currentTimeMillis()
        val scheduler = AutomationWorkScheduler(context.applicationContext)
        geofencingEvent.triggeringGeofences.orEmpty().forEach { geofence ->
            scheduler.enqueueEvent(
                deliveryId = "geofence:${geofence.requestId}:$eventType:$occurredAt",
                event = AutomationEvent(
                    type = eventType,
                    automationId = geofence.requestId,
                    occurredAt = occurredAt,
                    values = mapOf(
                        "latitude" to (location?.latitude?.toString() ?: ""),
                        "longitude" to (location?.longitude?.toString() ?: ""),
                        "etaMinutes" to "",
                        "transition" to eventType
                    )
                )
            )
        }
    }
}

internal object GeofenceTransitionCoordinator {
    suspend fun handle(automationIds: List<String>, execute: suspend (String) -> Unit) {
        var firstFailure: Exception? = null
        automationIds.forEach { automationId ->
            try {
                execute(automationId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (firstFailure == null) {
                    firstFailure = error
                } else {
                    firstFailure?.addSuppressed(error)
                }
            }
        }
        firstFailure?.let { throw it }
    }
}
