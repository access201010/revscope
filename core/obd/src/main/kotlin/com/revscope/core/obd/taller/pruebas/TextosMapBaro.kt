package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.FormatoTaller
import kotlin.math.abs
import kotlin.math.roundToLong

// Redacción del MAP contra la barométrica: la fuente de la referencia y su incertidumbre siempre a la vista,
// «compatible con…» y nunca un dictamen. Las unidades van con espacio duro.
object TextosMapBaro {

    val TITULOS_PASO = mapOf(AnalizadorMapBaro.Pasos.CONTACTO to "Contacto")

    const val NOTA_ECU = "Many ECUs calculate barometric pressure using the MAP sensor when the ignition is switched on: " +
        "a matching reading does not rule out a miscalibrated MAP sensor."

    fun resultado(a: AnalisisMapBaro) = ResultadoPrueba(
        tipo = TipoPrueba.MAP_BARO,
        veredicto = a.patron.veredicto,
        titulo = titulo(a),
        interpretacion = interpretacion(a),
        siguientePaso = siguientePaso(a),
        hallazgos = hallazgos(a),
        bajaConfianza = false,
        detalle = a,
    )

    fun titulo(a: AnalisisMapBaro): String = when (a.patron) {
        PatronMapBaro.SIN_DATOS -> "No MAP readings"
        PatronMapBaro.SIN_REFERENCIA -> "MAP at ${kpa(a.map?.mediaKpa)}, no barometric pressure available for comparison"
        PatronMapBaro.MAP_BAJO -> "MAP reads ${kpa(abs(a.deltaKpa ?: 0.0))} below barometric pressure"
        PatronMapBaro.MAP_ALTO -> "MAP reads ${kpa(abs(a.deltaKpa ?: 0.0))} above barometric pressure"
        PatronMapBaro.NO_CONCLUYENTE -> "MAP near barometric pressure; inconclusive"
        PatronMapBaro.NORMAL -> "MAP matches barometric pressure"
    }

    // ── Interpretación ──────────────────────────────────────────────────────

    private fun interpretacion(a: AnalisisMapBaro): String {
        val map = a.map ?: return "No MAP readings (PID 0B) were received with the ignition on, so there is nothing to compare."
        return listOfNotNull(
            "With the engine off, the MAP read ${kpa(map.mediaKpa)} (${rango(map)}).",
            fraseReferencia(a),
            fraseEquivalencia(a),
            fraseVeredicto(a),
            NOTA_ECU.takeIf { a.referencia?.origen == OrigenBarometrica.ECU },
        ).joinToString(" ")
    }

    private fun fraseReferencia(a: AnalisisMapBaro): String? {
        val ref = a.referencia ?: return "No reference barometric pressure was available: the ECU did not report PID 33, " +
            "the phone has no barometer and no GPS altitude was available."
        val banda = a.banda?.let { TextoBanda.citar(it, 0) } ?: "≤ ${kpa(AnalizadorMapBaro.BANDA_TIPICA_KPA)}, Typical (editable)"
        return "Reference from ${ref.enFrase}: ${kpa(ref.kPa)}. Difference: ${kpaConSigno(a.deltaKpa ?: 0.0)}; " +
            "with no vacuum, $banda is expected, plus the source uncertainty of ±${kpa(ref.incertidumbreKpa)}."
    }

    private fun fraseEquivalencia(a: AnalisisMapBaro): String? {
        if (a.patron == PatronMapBaro.NORMAL || a.patron == PatronMapBaro.NO_CONCLUYENTE) return null
        val altitud = a.altitudEquivalenteM ?: return null
        return "${kpa(a.map?.mediaKpa)} is equivalent to atmospheric pressure at an altitude of about ${metros(altitud)}."
    }

    private fun fraseVeredicto(a: AnalisisMapBaro): String? = when (a.patron) {
        PatronMapBaro.MAP_BAJO -> "Low reading: consistent with a miscalibrated MAP sensor (P0106) or low 5 V reference. " +
            "The MAP shares that reference with the TPS: if the TPS also reads low, check the shared reference."
        PatronMapBaro.MAP_ALTO ->
            "High reading: consistent with a miscalibrated MAP sensor (P0106), high signal (P0108) or an open sensor earth."
        PatronMapBaro.NO_CONCLUYENTE -> fraseNoConcluyente(a)
        PatronMapBaro.NORMAL -> "Within range: the MAP reads pressure correctly with the engine off."
        else -> null
    }

    private fun fraseNoConcluyente(a: AnalisisMapBaro): String {
        val ref = a.referencia
        val alt = a.alternativa
        if (ref != null && alt != null && a.contrasteDiscrepa) {
            return "Matches ${ref.origen.enFrase} but not ${alt.origen.enFrase}: conflicting references make the result inconclusive."
        }
        return "The difference is outside the typical range but within the source uncertainty, so this reference cannot provide a conclusive result."
    }

    // ── Hallazgos y siguiente paso ──────────────────────────────────────────

    private fun hallazgos(a: AnalisisMapBaro): List<String> = buildList {
        val delta = a.deltaKpa
        if (delta != null && (a.patron == PatronMapBaro.MAP_BAJO || a.patron == PatronMapBaro.MAP_ALTO)) {
            add("MAP reads ${kpa(abs(delta))} ${lado(delta)} barometric pressure (tolerance ±${kpa(a.toleranciaKpa ?: 0.0)})")
        }
        contraste(a)?.let(::add)
        val map = a.map
        if (map != null && map.variacionKpa > AnalizadorMapBaro.VARIACION_MAX_KPA) {
            add("MAP varied by ${kpa(map.variacionKpa)} with the engine off: unstable signal, consistent with a poor connection")
        }
    }

    private fun contraste(a: AnalisisMapBaro): String? {
        val alt = a.alternativa ?: return null
        val delta = a.deltaAlternativaKpa ?: return null
        if (!a.contrasteDiscrepa) return null
        return "Compared with ${alt.origen.enFrase} (${kpa(alt.kPa)}), MAP reads ${kpa(abs(delta))} ${lado(delta)}: " +
            "outside ±${kpa(a.toleranciaAlternativaKpa ?: 0.0)}"
    }

    private fun siguientePaso(a: AnalisisMapBaro): String? = when (a.patron) {
        PatronMapBaro.SIN_DATOS -> "Check the adapter is still connected and that the ECU reports MAP (PID 0B)"
        PatronMapBaro.SIN_REFERENCIA ->
            "Enable location on the phone (for GPS altitude), or use a phone with a barometer and repeat the test"
        PatronMapBaro.MAP_BAJO, PatronMapBaro.MAP_ALTO -> MEDIR_MAP
        PatronMapBaro.NO_CONCLUYENTE -> if (a.contrasteDiscrepa) MEDIR_MAP else REPETIR_CON_BAROMETRO
        PatronMapBaro.NORMAL -> if (a.referencia?.origen == OrigenBarometrica.ECU && a.alternativa == null) CONTRASTAR else null
    }

    private const val MEDIR_MAP = "Measure the 5 V reference, earth and MAP signal with a multimeter (MAP template)"
    private const val REPETIR_CON_BAROMETRO =
        "Repeat using the phone barometer or ECU barometric pressure (PID 33): both are more accurate references than altitude"
    private const val CONTRASTAR = "To rule out a miscalibrated MAP sensor, repeat the test with a phone that has a barometer"

    // ── Medidas: una línea por cifra, para la pantalla y el informe ──────────

    fun medidas(a: AnalisisMapBaro): List<String> {
        val map = a.map ?: return emptyList()
        return listOfNotNull(
            "MAP with engine off: ${kpa(map.mediaKpa)} (${rango(map)})",
            a.referencia?.let { "Barometric pressure: ${kpa(it.kPa)} · ${it.etiqueta}" } ?: "Barometric pressure: no source",
            a.deltaKpa?.let { "Difference: ${kpaConSigno(it)} (source tolerance ±${kpa(a.toleranciaKpa ?: 0.0)})" },
            a.altitudEquivalenteM?.let { "MAP is equivalent to an altitude of about ${metros(it)}" },
            a.alternativa?.let { alt ->
                "Cross-check: ${alt.origen.nombre} ${kpa(alt.kPa)} (difference ${kpaConSigno(a.deltaAlternativaKpa ?: 0.0)})"
            },
        )
    }

    // ── Formato ─────────────────────────────────────────────────────────────

    private fun rango(m: MapMedido): String {
        val desde = FormatoTaller.compacto(r1(m.minKpa))
        val hasta = FormatoTaller.compacto(r1(m.maxKpa))
        return if (desde == hasta) "stable across ${m.n} readings" else "$desde to ${kpa(m.maxKpa)}, ${m.n} readings"
    }

    private fun lado(delta: Double) = if (delta < 0) "below" else "above"

    private fun kpa(x: Double?) = "${FormatoTaller.compacto(r1(x ?: 0.0))} kPa"

    private fun kpaConSigno(x: Double) = "${FormatoTaller.conSigno(x, 1)} kPa"

    // Redondeada a la centena y con separador de miles: es una equivalencia, no una medida.
    private fun metros(m: Double): String {
        val centenas = (m / 100).roundToLong() * 100
        val texto = if (abs(centenas) >= 1_000) "${centenas / 1_000} ${"%03d".format(abs(centenas) % 1_000)}" else centenas.toString()
        return "$texto m"
    }

    private fun r1(x: Double) = FormatoTaller.redondear(x, 1)
}
