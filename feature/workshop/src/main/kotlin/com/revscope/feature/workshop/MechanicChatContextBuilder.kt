package com.revscope.feature.workshop

import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.workshop.DiagnosticRules
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TYPE_MOTORCYCLE = "MOTORCYCLE"
private const val MAX_HISTORY_TURNS = 8

private const val RPM_PID = "0C"
private const val COOLANT_TEMP_PID = "05"

/** Matches ObdSessionManager.VBAT_PID — duplicated as a literal to keep this object dependency-free. */
private const val VOLTAGE_PID = "VBAT"
private const val SHORT_TRIM_B1_PID = "06"
private const val LONG_TRIM_B1_PID = "07"

private val LIVE_PID_LABELS = linkedMapOf(
    RPM_PID to "RPM",
    COOLANT_TEMP_PID to "Temp",
    VOLTAGE_PID to "Voltage",
    SHORT_TRIM_B1_PID to "Short-term trim B1",
    LONG_TRIM_B1_PID to "Long-term trim B1",
)

/**
 * Pure formatting logic for the "AI Mechanic" chat — turns already-fetched vehicle data into
 * the compact context block embedded in the system prompt, and folds the in-memory
 * conversation history into a single transcript for [com.revscope.core.intelligence.provider.AiRequest.user]
 * (the shared [com.revscope.core.intelligence.provider.AiProvider] interface has no multi-turn
 * concept — every provider only sees one system + one user string).
 */
object MechanicChatContextBuilder {

    data class ChequeoItem(val titulo: String, val nivel: DiagnosticRules.Nivel)

    data class ViajeResumen(val startedAtEpochMs: Long, val distanceKm: Float, val ecoScore: Int?)

    data class VehicleContext(
        val profileName: String?,
        val profileType: String?,
        val fuelType: String?,
        val odometroKm: Double?,
        val conectado: Boolean,
        val lecturas: Map<String, ObdReading>,
        val ultimoChequeo: List<ChequeoItem>,
        val ultimosViajes: List<ViajeResumen>,
        val mantenimientoProximo: String?,
    )

    data class ChatTurn(val question: String, val answer: String)

    fun buildSystemPrompt(context: VehicleContext): String = buildString {
        append("You are an expert, practical mechanic specialising in ${tipoVehiculoTexto(context.profileType)}. ")
        append("Respond in British English, concisely and practically (maximum 6 sentences unless the user asks for detail). ")
        append("If the problem is serious or safety-related, say so clearly and recommend professional inspection or repair.")
        append("\n\nCurrent vehicle data:\n")
        append(buildContextBlock(context))
    }

    fun buildUserMessage(history: List<ChatTurn>, newQuestion: String): String = buildString {
        history.takeLast(MAX_HISTORY_TURNS).forEach { turn ->
            appendLine("User: ${turn.question}")
            appendLine("Mechanic: ${turn.answer}")
        }
        append("User: $newQuestion")
    }

    private fun buildContextBlock(context: VehicleContext): String = buildString {
        appendLine("Profile: ${perfilTexto(context)}")
        appendLine("Connection: ${conexionTexto(context)}")
        appendLine("Last health check: ${chequeoTexto(context.ultimoChequeo)}")
        appendLine("Recent journeys: ${viajesTexto(context.ultimosViajes)}")
        append("Maintenance: ${context.mantenimientoProximo ?: "not configured"}")
    }

    private fun tipoVehiculoTexto(profileType: String?): String = when (profileType) {
        TYPE_MOTORCYCLE -> "motorcycles"
        null -> "vehicles"
        else -> "cars"
    }

    private fun perfilTexto(context: VehicleContext): String {
        val nombre = context.profileName ?: return "not configured"
        val tipo = tipoVehiculoTexto(context.profileType)
        val combustible = context.fuelType ?: "unknown"
        val odometro = context.odometroKm?.let { String.format(Locale.US, "%,.0f km", it) } ?: "no data"
        return "$nombre ($tipo), fuel $combustible, app odometer: $odometro"
    }

    private fun conexionTexto(context: VehicleContext): String {
        if (!context.conectado) return "disconnected"
        val lecturas = LIVE_PID_LABELS.mapNotNull { (pid, label) ->
            context.lecturas[pid]?.let { "$label ${formatValor(it)} ${it.unit}" }
        }
        if (lecturas.isEmpty()) return "connected — no readings yet"
        return "connected — ${lecturas.joinToString(", ")}"
    }

    private fun formatValor(reading: ObdReading): String =
        if (reading.value % 1.0 == 0.0) {
            reading.value.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", reading.value)
        }

    private fun chequeoTexto(items: List<ChequeoItem>): String {
        if (items.isEmpty()) return "no data"
        return items.joinToString("; ") { "${it.titulo} (${it.nivel.name})" }
    }

    private fun viajesTexto(viajes: List<ViajeResumen>): String {
        if (viajes.isEmpty()) return "no data"
        return viajes.joinToString("; ") { viaje ->
            val eco = viaje.ecoScore?.toString() ?: "no data"
            val distancia = String.format(Locale.US, "%.0f", viaje.distanceKm)
            "${fechaCorta(viaje.startedAtEpochMs)} — $distancia km, eco $eco"
        }
    }

    private fun fechaCorta(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(epochMs))
}
