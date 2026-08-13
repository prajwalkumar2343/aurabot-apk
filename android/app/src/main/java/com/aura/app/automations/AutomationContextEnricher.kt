package com.aura.app.automations

interface AutomationContextEnricher {
    suspend fun enrich(spec: AutomationSpec, event: AutomationEvent): AutomationEvent
}

class DefaultAutomationContextEnricher(
    private val etaProvider: EtaProvider = LocalDistanceEtaProvider()
) : AutomationContextEnricher {
    override suspend fun enrich(spec: AutomationSpec, event: AutomationEvent): AutomationEvent {
        val geofence = spec.trigger.geofence
        val baseValues = event.values + mapOf(
            "automationId" to spec.id,
            "automationName" to spec.name,
            "placeName" to (geofence?.placeName ?: event.values["placeName"].orEmpty())
        )
        val etaValues = estimateEta(spec, baseValues)?.values() ?: emptyMap()
        return event.copy(automationId = spec.id, values = baseValues + etaValues)
    }

    private suspend fun estimateEta(spec: AutomationSpec, values: Map<String, String>): EtaEstimate? {
        val metadata = (spec.actions + spec.flow?.steps.orEmpty().mapNotNull { it.action }).firstOrNull { action ->
            action.type == AutomationActionTypes.EtaMessage ||
                action.type == AutomationActionTypes.DirectSms ||
                action.metadata["needsEta"] == "true"
        }?.metadata ?: return null
        val startLat = values["latitude"]?.toDoubleOrNull() ?: spec.trigger.geofence?.latitude ?: return null
        val startLng = values["longitude"]?.toDoubleOrNull() ?: spec.trigger.geofence?.longitude ?: return null
        val destinationLat = metadata["destinationLatitude"]?.toDoubleOrNull() ?: return null
        val destinationLng = metadata["destinationLongitude"]?.toDoubleOrNull() ?: return null
        return etaProvider.estimate(
            EtaRequest(
                originLatitude = startLat,
                originLongitude = startLng,
                destinationLatitude = destinationLat,
                destinationLongitude = destinationLng,
                mode = metadata["travelMode"] ?: EtaTravelModes.Driving,
                averageSpeedKph = metadata["averageSpeedKph"]?.toDoubleOrNull() ?: 30.0
            )
        )
    }
}
