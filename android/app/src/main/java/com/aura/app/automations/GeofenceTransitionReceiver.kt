package com.aura.app.automations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aura.app.AuraApplication
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GeofenceTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AutomationBroadcastWork.run(
                    operation = {
                        val geofencingEvent = GeofencingEvent.fromIntent(intent)
                        if (geofencingEvent == null || geofencingEvent.hasError()) return@run
                        val eventType = when (geofencingEvent.geofenceTransition) {
                            Geofence.GEOFENCE_TRANSITION_ENTER -> AutomationEvents.GeofenceEnter
                            Geofence.GEOFENCE_TRANSITION_EXIT -> AutomationEvents.GeofenceExit
                            else -> return@run
                        }
                        val location = geofencingEvent.triggeringLocation
                        val container = (context.applicationContext as AuraApplication).container
                        GeofenceTransitionCoordinator.handle(
                            automationIds = geofencingEvent.triggeringGeofences.orEmpty().map { it.requestId },
                            execute = { automationId ->
                                container.automationEngine.handle(
                                    AutomationEvent(
                                        type = eventType,
                                        automationId = automationId,
                                        values = mapOf(
                                            "latitude" to (location?.latitude?.toString() ?: ""),
                                            "longitude" to (location?.longitude?.toString() ?: ""),
                                            "etaMinutes" to "",
                                            "transition" to eventType
                                        )
                                    )
                                )
                            }
                        )
                    },
                    reportFailure = { error ->
                        Log.e(TAG, "Geofence automation delivery failed", error)
                    }
                )
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GeofenceAutomation"
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
