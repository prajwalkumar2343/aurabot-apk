package com.aura.app.automations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aura.app.AuraApplication
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GeofenceTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val geofencingEvent = GeofencingEvent.fromIntent(intent)
                if (geofencingEvent == null || geofencingEvent.hasError()) return@launch
                val eventType = when (geofencingEvent.geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> AutomationEvents.GeofenceEnter
                    Geofence.GEOFENCE_TRANSITION_EXIT -> AutomationEvents.GeofenceExit
                    else -> return@launch
                }
                val location = geofencingEvent.triggeringLocation
                val container = (context.applicationContext as AuraApplication).container
                geofencingEvent.triggeringGeofences.orEmpty().forEach { geofence ->
                    container.automationEngine.handle(
                        AutomationEvent(
                            type = eventType,
                            automationId = geofence.requestId,
                            values = mapOf(
                                "latitude" to (location?.latitude?.toString() ?: ""),
                                "longitude" to (location?.longitude?.toString() ?: ""),
                                "etaMinutes" to "",
                                "transition" to eventType
                            )
                        )
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
