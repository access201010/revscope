package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.pruebas.AnalizadorBarridoTps.Pasos
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.PosicionEnBanda
import com.revscope.core.obd.taller.sesion.Veredicto

// Redacción de «compatible con…», citando cada banda con su origen (típica, con fuente o editada).
object TextosBarridoTps {

    private const val SIN_BARRIDO_EVALUADO =
        "there were too few samples during the slow sweep to check for spikes or dead spots"

    val TITULOS_PASO = mapOf(
        Pasos.CERRADO_1 to "Throttle closed",
        Pasos.MEDIO to "Half throttle",
        Pasos.A_FONDO to "Full throttle",
        Pasos.CERRADO_2 to "Throttle closed again",
        Pasos.BARRIDO_LENTO to "Slow sweep",
    )

    fun resultado(a: AnalisisBarridoTps) = ResultadoPrueba(
        tipo = TipoPrueba.TPS_BARRIDO,
        veredicto = a.patron.veredicto,
        titulo = titulo(a.patron),
        interpretacion = interpretacion(a) + notaConfianza(a),
        siguientePaso = siguientePaso(a.patron),
        hallazgos = hallazgos(a),
        bajaConfianza = a.bajaConfianza,
        detalle = a,
    )

    fun sinDatos(faltan: List<String>) = ResultadoPrueba(
        tipo = TipoPrueba.TPS_BARRIDO,
        veredicto = Veredicto.ATENCION,
        titulo = "Not enough TPS data",
        interpretacion = "No TPS samples were received during: ${faltan.joinToString { TITULOS_PASO[it] ?: it }}. " +
            "Without them, the readings cannot be compared with the ranges.",
        siguientePaso = "Check the adapter is still connected and repeat the test",
        hallazgos = emptyList(),
        bajaConfianza = false,
        detalle = SinDatosTps(faltan),
    )

    fun titulo(patron: PatronTps): String = when (patron) {
        PatronTps.CORTES_O_SALTOS -> "Signal dropouts or spikes"
        PatronTps.SENAL_BAJA_TODO_EL_RECORRIDO -> "Signal consistently low throughout the sweep"
        PatronTps.SENAL_ALTA -> "Signal high with the throttle closed"
        PatronTps.RANGO_DESEMPENO -> "Signal outside the range or inconsistent"
        PatronTps.SIN_BARRIDO -> "Held positions within typical values; slow sweep not assessed"
        PatronTps.NORMAL -> "TPS signal within typical values"
    }

    fun siguientePaso(patron: PatronTps): String? = when (patron) {
        PatronTps.CORTES_O_SALTOS ->
            "Check the TPS plug and wiring, then measure the signal with a multimeter while moving the throttle slowly"
        PatronTps.SENAL_BAJA_TODO_EL_RECORRIDO ->
            "Measure the 5 V reference, earth and TPS signal with a multimeter (TPS template), confirm the part is " +
                "the correct original part, and check the plug"
        PatronTps.SENAL_ALTA -> "Measure the TPS earth and signal with a multimeter (TPS template) with the throttle closed"
        PatronTps.RANGO_DESEMPENO ->
            "Check the mechanical throttle stop and repeat the test; if the result persists, measure the signal with a multimeter"
        PatronTps.SIN_BARRIDO -> "Repeat the test and, when prompted, move the throttle slowly from fully closed to fully open"
        PatronTps.NORMAL -> null
    }

    private fun interpretacion(a: AnalisisBarridoTps): String = when (a.patron) {
        PatronTps.CORTES_O_SALTOS ->
            "The signal had ${cuantos(a.cuenta(TipoIrregularidad.CORTE), "dropout", "dropouts")} and " +
                "${cuantos(a.cuenta(TipoIrregularidad.SALTO), "spike", "spikes")} during the test: consistent with " +
                "P0124 (intermittent TPS signal) or a worn sensor track."
        PatronTps.SENAL_BAJA_TODO_EL_RECORRIDO -> senalBaja(a)
        PatronTps.SENAL_ALTA ->
            "With the throttle closed, ${v(a.cerradoV)} is above the range (${bandaDe(a, ClavesBanda.TPS_CERRADO_V)}): " +
                "consistent with P0123 (high TPS signal), a short between the reference and signal, signal at 5 V or an open earth."
        PatronTps.RANGO_DESEMPENO ->
            "The sweep is ${v(a.recorridoV)}, but ${a.comprobaciones.filterNot { it.cumple }.joinToString("; ") { falla(it, a) }}: " +
                "consistent with P0121 (TPS range or performance) or an incorrectly adjusted throttle stop."
        PatronTps.SIN_BARRIDO ->
            "Closed ${v(a.cerradoV)} (${bandaDe(a, ClavesBanda.TPS_CERRADO_V)}), full throttle ${v(a.fondoV)} " +
                "(${bandaDe(a, ClavesBanda.TPS_FONDO_V)}) and sweep ${v(a.recorridoV)}: stable and within range at the " +
                "held positions, but $SIN_BARRIDO_EVALUADO."
        PatronTps.NORMAL ->
            "Closed ${v(a.cerradoV)} (${bandaDe(a, ClavesBanda.TPS_CERRADO_V)}), full throttle ${v(a.fondoV)} " +
                "(${bandaDe(a, ClavesBanda.TPS_FONDO_V)}) and sweep ${v(a.recorridoV)}: stable and within range, with no " +
                "dropouts, spikes or dead spots."
    }

    private fun senalBaja(a: AnalisisBarridoTps): String {
        val typical = a.recorridoTipicoV?.let { " (≈${v(it)})" }.orEmpty()
        val fraction = a.fraccionRecorrido?.let { ", ${FormatoTaller.numero(it * 100, 0)}% of typical$typical" }.orEmpty()
        return "Closed ${v(a.cerradoV)} (${bandaDe(a, ClavesBanda.TPS_CERRADO_V)}) and full throttle ${v(a.fondoV)} " +
            "(${bandaDe(a, ClavesBanda.TPS_FONDO_V)}): the signal stays low throughout the sweep. Travel is " +
            "${v(a.recorridoV)}$fraction. It rises steadily; the repeated closed-throttle reading is ${v(a.repetibilidadV)}: " +
            "consistent with P0122 (low TPS signal)."
    }

    private fun notaConfianza(a: AnalisisBarridoTps): String {
        val depende = a.patron == PatronTps.CORTES_O_SALTOS || a.patron == PatronTps.RANGO_DESEMPENO
        if (!a.bajaConfianza || !depende) return ""
        return " Low-confidence detection due to the ${hz(a.tasaHz)} sample rate."
    }

    private fun hallazgos(a: AnalisisBarridoTps): List<String> = buildList {
        a.comprobaciones.filterNot { it.cumple }.forEach { add(falla(it, a).replaceFirstChar(Char::uppercase)) }
        irregularidades(a)?.let(::add)
        if (!a.barridoEvaluado && a.patron != PatronTps.SIN_BARRIDO) add(SIN_BARRIDO_EVALUADO.replaceFirstChar(Char::uppercase))
        if (a.bajaConfianza) {
            add("Sample rate of ${hz(a.tasaHz)}, below 8 Hz: spike and dead-spot detection has low confidence")
        }
    }

    private fun irregularidades(a: AnalisisBarridoTps): String? {
        val partes = listOf(
            a.cuenta(TipoIrregularidad.CORTE) to ("dropout" to "dropouts"),
            a.cuenta(TipoIrregularidad.SALTO) to ("spike" to "spikes"),
            a.cuenta(TipoIrregularidad.ZONA_MUERTA) to ("dead spot" to "dead spots"),
        ).filter { it.first > 0 }.map { (n, nombres) -> cuantos(n, nombres.first, nombres.second) }
        return partes.takeIf { it.isNotEmpty() }?.joinToString(", ")?.replaceFirstChar(Char::uppercase)
    }

    private fun falla(c: Comprobacion, a: AnalisisBarridoTps): String = when {
        c.clave == AnalizadorBarridoTps.CLAVE_ORDEN -> "positions are out of order (closed < half throttle < full throttle)"
        c.clave == AnalizadorBarridoTps.CLAVE_SIN_ZONAS_MUERTAS ->
            "${cuantos(a.cuenta(TipoIrregularidad.ZONA_MUERTA), "dead spot", "dead spots")} while opening or closing the throttle"
        c.valor == null -> "${c.etiqueta.lowercase()} could not be calculated"
        else -> "${c.etiqueta.lowercase()} ${valor(c)} ${lado(c.posicion)} the range (${c.banda?.let(::banda)})"
    }

    private fun lado(posicion: PosicionEnBanda?): String =
        if (posicion == PosicionEnBanda.BAJO) "below" else "above"

    private fun valor(c: Comprobacion): String {
        val x = c.valor ?: return "—"
        return if (c.unidad.isEmpty()) FormatoTaller.numero(x, 2) else "${FormatoTaller.numero(x, 2)} ${c.unidad}"
    }

    private fun bandaDe(a: AnalisisBarridoTps, clave: String): String =
        a.comprobacion(clave)?.banda?.let(::banda) ?: "no reference range"

    fun banda(b: BandaReferencia): String = "${rango(b)}, ${b.etiquetaOrigen}"

    private fun rango(b: BandaReferencia): String {
        val unidad = if (b.unidad == "V") " V" else ""
        val min = b.min
        val max = b.max
        return when {
            min != null && max != null -> "${numeroBanda(min)}–${numeroBanda(max)}$unidad"
            min != null -> "≥ ${numeroBanda(min)}$unidad"
            else -> "≤ ${numeroBanda(max ?: 0.0)}$unidad"
        }
    }

    private fun numeroBanda(x: Double): String =
        FormatoTaller.numero(x, if (FormatoTaller.redondear(x, 1) == x) 1 else 2)

    private fun cuantos(n: Int, uno: String, varios: String): String = if (n == 1) "1 $uno" else "$n $varios"

    private fun v(x: Double): String = "${FormatoTaller.numero(x, 2)} V"

    private fun hz(x: Double): String = "${FormatoTaller.numero(x, 1)} Hz"
}
