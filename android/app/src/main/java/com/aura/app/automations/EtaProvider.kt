package com.aura.app.automations

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class EtaRequest(
    val originLatitude: Double,
    val originLongitude: Double,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
    val mode: String = EtaTravelModes.Driving,
    val averageSpeedKph: Double = 30.0
)

data class EtaEstimate(
    val minutes: Int,
    val distanceKm: Double,
    val provider: String,
    val confidence: String
) {
    fun values(): Map<String, String> = mapOf(
        "etaMinutes" to minutes.toString(),
        "etaDistanceKm" to String.format(Locale.US, "%.1f", distanceKm),
        "etaProvider" to provider,
        "etaConfidence" to confidence
    )
}

interface EtaProvider {
    suspend fun estimate(request: EtaRequest): EtaEstimate?
}

class LocalDistanceEtaProvider : EtaProvider {
    override suspend fun estimate(request: EtaRequest): EtaEstimate {
        val distanceKm = haversineKm(
            startLat = request.originLatitude,
            startLng = request.originLongitude,
            endLat = request.destinationLatitude,
            endLng = request.destinationLongitude
        )
        val speed = speedFor(request)
        val minutes = ((distanceKm / speed) * 60.0).roundToInt().coerceAtLeast(1)
        return EtaEstimate(
            minutes = minutes,
            distanceKm = distanceKm,
            provider = "local_distance",
            confidence = "estimated"
        )
    }

    private fun speedFor(request: EtaRequest): Double =
        request.averageSpeedKph.takeIf { it > 0.0 } ?: when (request.mode) {
            EtaTravelModes.Walking -> 4.5
            EtaTravelModes.Cycling -> 14.0
            EtaTravelModes.Transit -> 22.0
            else -> 30.0
        }

    private fun haversineKm(startLat: Double, startLng: Double, endLat: Double, endLng: Double): Double {
        val earthRadiusKm = 6371.0
        val latDelta = Math.toRadians(endLat - startLat)
        val lngDelta = Math.toRadians(endLng - startLng)
        val a = sin(latDelta / 2) * sin(latDelta / 2) +
            cos(Math.toRadians(startLat)) *
            cos(Math.toRadians(endLat)) *
            sin(lngDelta / 2) *
            sin(lngDelta / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusKm * c
    }
}

object EtaTravelModes {
    const val Driving = "driving"
    const val Walking = "walking"
    const val Cycling = "cycling"
    const val Transit = "transit"
}
