package com.revscope.core.intelligence.debrief

import com.revscope.core.intelligence.provider.AiProviderFactory
import com.revscope.core.intelligence.provider.AiRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debrief de viaje por IA: los agregados se calculan localmente (SQL/Kotlin) y el
 * modelo solo narra — una llamada corta por viaje, sin datos crudos (patrón validado
 * en research 2026-08: la mejor relación valor/token de todas las features IA).
 */
@Singleton
class TripDebriefGenerator @Inject constructor(
    private val aiProviderFactory: AiProviderFactory,
) {

    data class TripDigest(
        val vehicleName: String,
        val isMotorcycle: Boolean,
        val distanceKm: Double,
        val durationMin: Long,
        val avgSpeedKmh: Int,
        val maxSpeedKmh: Int,
        val maxRpm: Int,
        val ecoScore: Int?,
        val fuelCostCop: Double?,
        val maxBrakingG: Float?,
        val maxLateralG: Float?,
        val maxLeanDeg: Float?,
        val best0to100s: Double?,
        /** Promedios de los viajes anteriores del mismo vehículo, para comparar. */
        val prevTripCount: Int,
        val prevAvgEcoScore: Int?,
        val prevAvgMaxSpeedKmh: Int?,
    )

    suspend fun generate(digest: TripDigest): Result<String> {
        val provider = aiProviderFactory.current()
            ?: return Result.failure(IllegalStateException("Set up an AI provider in Settings"))
        return provider.complete(
            AiRequest(
                system = SYSTEM_PROMPT,
                user = buildUserPrompt(digest),
                maxTokens = 400,
            )
        )
    }

    private fun buildUserPrompt(d: TripDigest): String = buildString {
        appendLine("Vehicle: ${d.vehicleName} (${if (d.isMotorcycle) "motorbike" else "car"})")
        appendLine("Trip: %.1f km in %d min · average speed %d km/h · max speed %d km/h · max RPM %d".format(
            d.distanceKm, d.durationMin, d.avgSpeedKmh, d.maxSpeedKmh, d.maxRpm))
        d.ecoScore?.let { appendLine("Eco score: $it/100") }
        d.fuelCostCop?.let { appendLine("Fuel cost: %.0f COP".format(it)) }
        d.maxBrakingG?.let { appendLine("Max braking G: %.2f G".format(it)) }
        d.maxLateralG?.let { appendLine("Max lateral G: %.2f G".format(it)) }
        d.maxLeanDeg?.takeIf { d.isMotorcycle }?.let { appendLine("Max lean: %.0f°".format(it)) }
        d.best0to100s?.let { appendLine("Best 0–100 km/h: %.1f s".format(it)) }
        if (d.prevTripCount > 0) {
            appendLine("History (${d.prevTripCount} previous trips): average eco score ${d.prevAvgEcoScore ?: "?"}, average max speed ${d.prevAvgMaxSpeedKmh ?: "?"} km/h")
        }
    }

    private companion object {
        const val SYSTEM_PROMPT =
            "You are a concise, direct driving coach. Respond in British English. " +
                "Using the trip data: (1) summarise the most notable points in 2–3 sentences, " +
                "(2) compare with the history if available, " +
                "(3) give no more than 2 practical driving or maintenance tips. " +
                "Maximum 130 words. No numbered lists or emojis, and do not invent missing data."
    }
}
