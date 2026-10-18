package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.pruebas.AnalizadorArranqueFrio.Pasos
import kotlin.math.abs

// Redacción del arranque en frío: la condición (frío o tibio) va en el título, las cifras con su banda y
// origen, y la nota de los motores refrigerados por aire siempre, porque la ECU no dice cómo se enfría.
object TextosArranqueFrio {

    val TITULOS_PASO = mapOf(
        Pasos.CONTACTO to "Ignition on",
        Pasos.ARRANQUE to "Starting",
        Pasos.CALENTAMIENTO to "Warm-up",
    )

    const val NOTA_AIRE = "On air-cooled motorbikes, the 'engine' sensor usually measures cylinder-head or oil temperature: " +
        "it rises differently from coolant temperature."

    fun resultado(a: AnalisisArranqueFrio) = ResultadoPrueba(
        tipo = TipoPrueba.ARRANQUE_FRIO,
        veredicto = a.patron.veredicto,
        titulo = titulo(a),
        interpretacion = interpretacion(a),
        siguientePaso = siguientePaso(a),
        hallazgos = hallazgos(a),
        bajaConfianza = false,
        detalle = a,
    )

    fun titulo(a: AnalisisArranqueFrio): String {
        val cuerpo = cuerpoTitulo(a)
        return when (a.plausibilidad.condicion) {
            CondicionMotor.FRIO -> "Cold start: $cuerpo"
            CondicionMotor.TIBIO -> "Warm start: $cuerpo"
            CondicionMotor.SIN_DATO -> cuerpo.replaceFirstChar(Char::uppercase)
        }
    }

    private fun cuerpoTitulo(a: AnalisisArranqueFrio): String = when (a.patron) {
        PatronArranque.SIN_DATOS -> "not enough RPM data"
        PatronArranque.NO_ARRANCO -> "did not start"
        PatronArranque.SE_APAGA -> "stalled ${veces(a.apagones)} after starting"
        PatronArranque.SALTOS_ECT -> "engine temperature spikes"
        PatronArranque.VARIOS_INTENTOS -> "started after ${a.arranque.intentos} attempts${enTiempo(a)}"
        PatronArranque.IRREGULAR -> "started, with observations"
        PatronArranque.NORMAL -> "started on the first attempt${enTiempo(a)}"
    }

    private fun enTiempo(a: AnalisisArranqueFrio): String = a.arranque.tiempoS?.let { " in ${seg(it)} s" }.orEmpty()

    // ── Interpretación ──────────────────────────────────────────────────────

    private fun interpretacion(a: AnalisisArranqueFrio): String = listOfNotNull(
        fraseCondicion(a.plausibilidad),
        fraseArranque(a),
        fraseCalentamiento(a),
        frasePerfil(a.perfil),
        fraseSaltos(a),
        NOTA_AIRE,
    ).joinToString(" ")

    private fun fraseCondicion(p: PlausibilidadFrio): String {
        val ect = p.ectC
        val iat = p.iatC
        if (ect == null || iat == null) {
            return "No ${if (ect == null) "engine" else "intake-air"} temperature reading with the ignition on: could not " +
                "confirm that the engine was cold."
        }
        val medidas = "With the ignition on, the engine was at ${grados(ect)} and the air at ${grados(iat)} " +
            "(${grados(ect - iat)} difference; cold is below ${grados(p.limiteC)}, ${origenLimite(p)})."
        return when (p.condicion) {
            CondicionMotor.TIBIO -> "$medidas This is a warm start, not a start after leaving the engine overnight."
            else -> medidas
        }
    }

    private fun origenLimite(p: PlausibilidadFrio): String = p.banda?.etiquetaOrigen ?: "Typical (editable)"

    private fun fraseArranque(a: AnalisisArranqueFrio): String? {
        val r = a.arranque
        if (!r.arranco) return if (r.intentos == 0) "The engine did not turn over during the starting step." else "It did not stay running after ${intentos(r.intentos)}."
        val intento = if (r.intentos == 1) "on the first attempt" else "after ${r.intentos} attempts"
        val apagones = if (a.apagones > 0) " It then stalled ${veces(a.apagones)}." else ""
        return "Started $intento, ${seg(r.tiempoS ?: 0.0)} s after the starter was operated.$apagones"
    }

    private fun fraseCalentamiento(a: AnalisisArranqueFrio): String? {
        a.tiempoHasta60S?.let { return "Reached ${grados(AnalizadorArranqueFrio.CALENTADO_C)} in ${duracion(it)}." }
        val maximo = a.ectMaxC ?: return null
        return if (a.arranque.arranco) "Reached ${grados(maximo)} without reaching ${grados(AnalizadorArranqueFrio.CALENTADO_C)}." else null
    }

    private fun frasePerfil(p: PerfilMinimoRapido?): String? {
        if (p?.baja == null) return null
        val verbo = if (p.rpmFin <= p.rpmInicio) "fell" else "rose"
        val cierre = if (p.baja) "as expected" else "and should fall as the engine warms up"
        return "Fast idle $verbo from ${rpm(p.rpmInicio)} to ${rpm(p.rpmFin)} rpm as the engine temperature rose " +
            "from ${grados(p.ectInicioC)} to ${grados(p.ectFinC)}, $cierre."
    }

    private fun fraseSaltos(a: AnalisisArranqueFrio): String? {
        val mayor = a.saltosEct.maxByOrNull { abs(it.delta) } ?: return null
        return "Engine temperature jumped by ${grados(abs(mayor.delta))} in ${seg(mayor.duracionMs / 1_000.0)} s: " +
            "consistent with P0119 (intermittent engine-temperature sensor signal) or a poor connection at its plug."
    }

    // ── Hallazgos y siguiente paso ──────────────────────────────────────────

    private fun hallazgos(a: AnalisisArranqueFrio): List<String> = buildList {
        val p = a.plausibilidad
        if (p.motorBajoAire) add("After resting, the engine reads ${grados(-(p.diferenciaC ?: 0.0))} colder than the air: check the ECT and IAT sensors")
        if (p.aireLejosDelAmbiente) {
            add("With the engine cold, intake air (${grados(p.iatC ?: 0.0)}) differs from ambient (${grados(p.ambienteC ?: 0.0)}): check the IAT sensor")
        }
        if (a.arranque.arranco && a.arranque.intentos > 1) add("Started after ${a.arranque.intentos} attempts")
        if (a.apagones > 0) add("Stalled ${veces(a.apagones)} after starting")
        a.saltosEct.forEach { add("Engine temperature jumped by ${grados(abs(it.delta))} in ${seg(it.duracionMs / 1_000.0)} s") }
        if (a.ectBajaMientrasCalienta) add("Engine temperature fell by ${grados(a.caidaEctMaxC)} during warm-up")
        if (a.perfil?.baja == false) {
            add("Fast idle rose from ${rpm(a.perfil.rpmInicio)} to ${rpm(a.perfil.rpmFin)} rpm during warm-up")
        }
    }

    private fun siguientePaso(a: AnalisisArranqueFrio): String? = when (a.patron) {
        PatronArranque.SIN_DATOS -> "Check the adapter is still connected and repeat the test"
        PatronArranque.NO_ARRANCO -> "Measure the battery with a multimeter while starting (battery template), and check the fuel supply and ignition spark"
        PatronArranque.SE_APAGA -> "Run the idle and return test with the engine cold, and check the idle control"
        PatronArranque.SALTOS_ECT ->
            "Check the engine-temperature sensor plug and wiring, then measure it with a multimeter (ECT template)"
        PatronArranque.VARIOS_INTENTOS ->
            "Repeat the test after leaving the engine overnight; if it still struggles to start, measure the battery while cranking (battery template)"
        PatronArranque.IRREGULAR -> siguienteIrregular(a)
        PatronArranque.NORMAL -> if (a.plausibilidad.condicion == CondicionMotor.TIBIO) REPETIR_EN_FRIO else null
    }

    private fun siguienteIrregular(a: AnalisisArranqueFrio): String = when {
        !a.plausibilidad.coherente -> "Measure the temperature sensors with a multimeter (ECT and IAT templates)"
        a.ectBajaMientrasCalienta -> "Measure the engine-temperature sensor with a multimeter (ECT template)"
        else -> "Check the idle control: fast idle should fall as the engine warms up"
    }

    private const val REPETIR_EN_FRIO = "To confirm a cold start, repeat the test after leaving the engine overnight (typically at least 6 hours)"

    // ── Medidas: una línea por cifra, para la pantalla y el informe ──────────

    fun medidas(a: AnalisisArranqueFrio): List<String> = listOfNotNull(
        medidaContacto(a.plausibilidad),
        medidaArranque(a.arranque),
        "Stalls after starting: ${a.apagones}",
        a.perfil?.let { p ->
            "Fast idle: ${rpm(p.rpmInicio)} rpm at ${grados(p.ectInicioC)} → ${rpm(p.rpmFin)} rpm at ${grados(p.ectFinC)}"
        },
        medidaCalentamiento(a),
        "Engine temperature jumps: " + if (a.saltosEct.isEmpty()) "none" else a.saltosEct.size.toString(),
    )

    private fun medidaContacto(p: PlausibilidadFrio): String {
        val partes = listOfNotNull(
            p.ectC?.let { "engine ${grados(it)}" },
            p.iatC?.let { "air ${grados(it)}" },
            p.ambienteC?.let { "ambient ${grados(it)}" },
        )
        val condicion = when (p.condicion) {
            CondicionMotor.FRIO -> "cold"
            CondicionMotor.TIBIO -> "warm"
            CondicionMotor.SIN_DATO -> "no data"
        }
        return "Ignition on: ${partes.ifEmpty { listOf("no temperature readings") }.joinToString(", ")} · $condicion"
    }

    private fun medidaArranque(r: ArranqueMedido): String = if (r.arranco) {
        "Starting: ${intentos(r.intentos)}, ${seg(r.tiempoS ?: 0.0)} s to start"
    } else {
        "Starting: did not start (${intentos(r.intentos)})"
    }

    private fun medidaCalentamiento(a: AnalisisArranqueFrio): String? {
        a.tiempoHasta60S?.let { return "Time to ${grados(AnalizadorArranqueFrio.CALENTADO_C)}: ${duracion(it)}" }
        return a.ectMaxC?.let { "Time to ${grados(AnalizadorArranqueFrio.CALENTADO_C)}: not reached (maximum ${grados(it)})" }
    }

    // ── Formato ─────────────────────────────────────────────────────────────

    private fun duracion(s: Double): String =
        if (s < 120) "${seg(s)} s" else "${FormatoTaller.numero(s / 60, 1)} min"

    private fun veces(n: Int) = if (n == 1) "once" else "$n times"

    private fun intentos(n: Int) = if (n == 1) "1 attempt" else "$n attempts"

    private fun grados(x: Double) = "${FormatoTaller.numero(x, 0)} °C"

    private fun rpm(x: Double) = FormatoTaller.numero(x, 0)

    private fun seg(x: Double) = FormatoTaller.compacto(FormatoTaller.redondear(x, 1))
}
