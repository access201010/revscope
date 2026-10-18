package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.pruebas.AnalizadorMinimo.Pasos
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.PosicionEnBanda

// Redacción del mínimo y retorno: cifras medidas, la banda con su origen y «compatible con…», nunca un dictamen.
object TextosMinimo {

    private val RPM_S = TextoBanda.unidad("rpm/s")

    val TITULOS_PASO = mapOf(
        Pasos.MINIMO to "Idle",
        Pasos.RETORNO_1 to "Throttle return 1",
        Pasos.RETORNO_2 to "Throttle return 2",
        Pasos.RETORNO_3 to "Throttle return 3",
    )

    fun resultado(a: AnalisisMinimo) = ResultadoPrueba(
        tipo = TipoPrueba.MINIMO_RETORNO,
        veredicto = a.patron.veredicto,
        titulo = titulo(a),
        interpretacion = interpretacion(a),
        siguientePaso = siguientePaso(a),
        hallazgos = hallazgos(a),
        bajaConfianza = false,
        detalle = a,
    )

    fun titulo(a: AnalisisMinimo): String = when (a.patron) {
        PatronMinimo.SE_APAGA -> tituloApagones(a)
        PatronMinimo.INESTABLE -> "Unstable idle"
        PatronMinimo.RETORNO_BAJO -> "RPM drop too low on throttle release"
        PatronMinimo.FUERA_DE_BANDA -> "Stable idle, outside the reference range"
        PatronMinimo.TPS_RALENTI_FUERA -> "Stable idle, but TPS at idle is outside the closed-throttle range"
        PatronMinimo.SIN_RETORNOS -> "Stable idle; no throttle returns detected"
        PatronMinimo.SIN_DATOS -> "Not enough RPM data"
        PatronMinimo.NORMAL -> "Stable idle with good return"
    }

    private fun tituloApagones(a: AnalisisMinimo): String {
        val enMinimo = a.minimo?.apagones ?: 0
        val alSoltar = "${a.apagonesAlSoltar} of ${a.retornosValidos.size} times when releasing the throttle"
        return when {
            enMinimo == 0 -> "Stalled $alSoltar"
            a.apagonesAlSoltar == 0 -> "Stalled at idle without touching the throttle"
            else -> "Stalled at idle and $alSoltar"
        }
    }

    // ── Interpretación ──────────────────────────────────────────────────────

    private fun interpretacion(a: AnalisisMinimo): String {
        val m = a.minimo ?: return "No RPM readings were received at idle, so there is nothing to measure."
        return listOfNotNull(
            frasesMinimo(a, m),
            fraseBanda(a),
            fraseRetornos(a),
            a.ectMediaC?.let { "Engine temperature was ${FormatoTaller.numero(it, 0)} °C at idle." },
            compatibilidad(a.patron),
        ).joinToString(" ")
    }

    private fun frasesMinimo(a: AnalisisMinimo, m: EstadisticaMinimo): String {
        val deriva = a.comprobacion(ClavesBanda.MINIMO_DERIVA_MAX)
        val base = if (deriva?.cumple == false) {
            "Idle ${if (m.derivaRpmS < 0) "fell" else "rose"} from ${rpm(m.inicioRpm)} to ${rpm(m.finRpm)} rpm over " +
                "${FormatoTaller.compacto(FormatoTaller.redondear(m.duracionS, 1))} s (${FormatoTaller.numero(m.derivaRpmS, 0)} $RPM_S)"
        } else {
            "Average idle was ${rpm(m.mediaRpm)} rpm, with a variation of ${rpm(m.desviacionRpm)} rpm"
        }
        val oscila = m.oscilacion?.let { ", oscillating by ±${rpm(it.amplitudRpm)} rpm${it.periodoS?.let { p -> " every ${seg(p)} s" }.orEmpty()}" }
        return base + oscila.orEmpty() + if (m.inestable) ": unstable${bandasEstabilidad(a)}." else "."
    }

    private fun bandasEstabilidad(a: AnalisisMinimo): String {
        val citas = listOf(ClavesBanda.MINIMO_DERIVA_MAX, ClavesBanda.MINIMO_DESVIACION_MAX)
            .mapNotNull { a.comprobacion(it)?.banda }
            .map { TextoBanda.citar(it, 0) }
        return if (citas.isEmpty()) "" else " (${citas.joinToString("; ")})"
    }

    // Con el mínimo inestable, la media no dice dónde queda el ralentí: la banda se calla.
    private fun fraseBanda(a: AnalisisMinimo): String? {
        val c = mediaFueraDeBanda(a)?.takeIf { it.banda != null } ?: return null
        return "The average, ${rpm(c.valor ?: 0.0)} rpm, is ${TextoBanda.lado(c.posicion)} the range " +
            "(${TextoBanda.citar(checkNotNull(c.banda), 0)})."
    }

    private fun fraseRetornos(a: AnalisisMinimo): String? {
        val validos = a.retornosValidos
        if (validos.isEmpty()) return null
        if (a.apagonesAlSoltar > 0) return "The engine stalled ${a.apagonesAlSoltar} out of ${validos.size} times when the throttle was released."
        val peor = validos.minBy { it.valleFraccion ?: 1.0 }
        val tiempos = validos.mapNotNull { it.tiempoS }
        val vuelta = if (tiempos.isEmpty()) "" else " and returned to idle within ${seg(tiempos.max())} s"
        return "On release, the lowest dip was ${rpm(peor.valleRpm ?: 0.0)} rpm " +
            "(${pct(peor.valleFraccion)} of idle speed)$vuelta."
    }

    private fun compatibilidad(patron: PatronMinimo): String? = when (patron) {
        PatronMinimo.SE_APAGA, PatronMinimo.INESTABLE, PatronMinimo.RETORNO_BAJO ->
            "Consistent with poor idle control: idle-control valve or stepper motor, a dirty throttle body, an air leak " +
                "or a TPS signal the ECU does not recognise as idle."
        else -> null
    }

    // ── Hallazgos y siguiente paso ──────────────────────────────────────────

    private fun hallazgos(a: AnalisisMinimo): List<String> = buildList {
        val m = a.minimo ?: return@buildList
        if (m.inestable) add(hallazgoInestable(m))
        if (m.apagones > 0) add("Stalled ${veces(m.apagones)} at idle without touching the throttle")
        a.retornos.filter { it.seApago }.takeIf { it.isNotEmpty() }?.let { add("Stalled on throttle release during ${nombres(it)}") }
        addAll(a.retornos.mapNotNull(::hallazgoRetorno))
        hallazgoValle(a)?.let(::add)
        hallazgoBanda(a)?.let(::add)
        hallazgoTps(a)?.let(::add)
    }

    private fun hallazgoInestable(m: EstadisticaMinimo): String {
        val osc = m.oscilacion?.let { ", oscila ±${rpm(it.amplitudRpm)} rpm" }.orEmpty()
        return "Unstable idle: drift of ${FormatoTaller.numero(m.derivaRpmS, 1)} $RPM_S and variation of " +
            "${rpm(m.desviacionRpm)} rpm$osc"
    }

    private fun hallazgoRetorno(r: RetornoMedido): String? {
        val nombre = TITULOS_PASO[r.clave] ?: r.clave
        return when {
            !r.acelerada -> "$nombre: throttle input not detected (reached ${rpm(r.picoRpm)} rpm); not counted"
            !r.seApago && r.tiempoS == null -> "$nombre: did not return to within ±10% of idle before the step ended"
            else -> null
        }
    }

    private fun hallazgoValle(a: AnalisisMinimo): String? {
        val c = a.comprobacion(ClavesBanda.RETORNO_VALLE_MIN)?.takeIf { !it.cumple && a.apagonesAlSoltar == 0 } ?: return null
        val banda = c.banda?.let { " (${TextoBanda.citar(it, 0)})" }.orEmpty()
        return "Throttle-release dip was ${FormatoTaller.numero(c.valor ?: 0.0, 0)}% of idle speed, below the range$banda"
    }

    private fun hallazgoBanda(a: AnalisisMinimo): String? {
        val c = mediaFueraDeBanda(a) ?: return null
        val banda = c.banda?.let { " (${TextoBanda.citar(it, 0)})" }.orEmpty()
        return "Idle speed of ${rpm(c.valor ?: 0.0)} rpm, ${TextoBanda.lado(c.posicion)} the range$banda"
    }

    private fun mediaFueraDeBanda(a: AnalisisMinimo): Comprobacion? {
        if (a.minimo?.inestable != false) return null
        return a.comprobacion(ClavesBanda.MINIMO_RPM)?.takeIf { !it.cumple }
    }

    private fun hallazgoTps(a: AnalisisMinimo): String? {
        val t = a.tpsRalenti?.takeIf { it.posicion != null && it.posicion != PosicionEnBanda.DENTRO } ?: return null
        val banda = t.banda?.let { " (${TextoBanda.citar(it, 1)})" }.orEmpty()
        val consecuencia = if (t.posicion == PosicionEnBanda.BAJO) {
            "the ECU may not recognise the idle position"
        } else {
            "the throttle may not be closing fully or the stop may be misadjusted"
        }
        return "TPS at idle ${FormatoTaller.numero(t.minV, 2)}–${FormatoTaller.numero(t.maxV, 2)} V, " +
            "${TextoBanda.lado(t.posicion)} the closed-throttle range$banda: $consecuencia"
    }

    private fun siguientePaso(a: AnalisisMinimo): String? {
        val tpsFuera = a.tpsRalenti?.posicion.let { it != null && it != PosicionEnBanda.DENTRO }
        if (tpsFuera && a.patron != PatronMinimo.SIN_DATOS) {
            return "Run the TPS sweep with the engine off and measure its signal with a multimeter (TPS template)"
        }
        return when (a.patron) {
            PatronMinimo.SE_APAGA, PatronMinimo.INESTABLE, PatronMinimo.RETORNO_BAJO ->
                "Check the throttle body and idle control, look for air leaks, then repeat the test with the engine cold " +
                    "and warm to compare"
            PatronMinimo.FUERA_DE_BANDA ->
                "Check the manufacturer's idle-speed specification for this model; edit the typical range if it differs"
            PatronMinimo.SIN_RETORNOS -> "Repeat the test, raising the revs to about 3,000 rpm for each return"
            PatronMinimo.SIN_DATOS -> "Check the adapter is still connected and repeat the test"
            PatronMinimo.TPS_RALENTI_FUERA, PatronMinimo.NORMAL -> null
        }
    }

    // ── Medidas: una línea por cifra, para la pantalla y el informe ──────────

    fun medidas(a: AnalisisMinimo): List<String> {
        val m = a.minimo ?: return emptyList()
        return listOfNotNull(
            "Idle: average ${rpm(m.mediaRpm)} rpm, variation ${rpm(m.desviacionRpm)} rpm, range ${rpm(m.minRpm)}–" +
                "${rpm(m.maxRpm)} rpm (${m.n} samples over ${seg(m.duracionS)} s)",
            "Drift: ${FormatoTaller.numero(m.derivaRpmS, 1)} $RPM_S (${rpm(m.inicioRpm)}–${rpm(m.finRpm)} rpm)",
            "Oscillation: " + (m.oscilacion?.let { o -> "±${rpm(o.amplitudRpm)} rpm${o.periodoS?.let { " every ${seg(it)} s" }.orEmpty()}" } ?: "none"),
            a.tpsRalenti?.let { t ->
                "TPS at idle: ${FormatoTaller.numero(t.minV, 2)}–${FormatoTaller.numero(t.maxV, 2)} V " +
                    "(average ${FormatoTaller.numero(t.mediaV, 2)} V)"
            },
            a.ectMediaC?.let { "Engine temperature ${FormatoTaller.numero(it, 0)} °C at idle" },
        ) + a.retornos.map(::medidaRetorno)
    }

    private fun medidaRetorno(r: RetornoMedido): String {
        val nombre = TITULOS_PASO[r.clave] ?: r.clave
        return when {
            !r.acelerada -> "$nombre: no throttle input (maximum ${rpm(r.picoRpm)} rpm)"
            r.seApago -> "$nombre: peak ${rpm(r.picoRpm)} rpm; stalled on release"
            else -> "$nombre: peak ${rpm(r.picoRpm)} rpm, dip ${rpm(r.valleRpm ?: 0.0)} rpm (${pct(r.valleFraccion)} of idle speed), " +
                (r.tiempoS?.let { "returned in ${seg(it)} s" } ?: "did not return to within ±10% of idle")
        }
    }

    // ── Formato ─────────────────────────────────────────────────────────────

    private fun nombres(retornos: List<RetornoMedido>): String {
        val lista = retornos.map { (TITULOS_PASO[it.clave] ?: it.clave).lowercase() }
        return if (lista.size == 1) lista.first() else "${lista.dropLast(1).joinToString(", ")} and ${lista.last()}"
    }

    private fun veces(n: Int) = if (n == 1) "once" else "$n times"

    private fun rpm(x: Double) = FormatoTaller.numero(x, 0)

    private fun seg(x: Double) = FormatoTaller.numero(x, 1)

    private fun pct(fraccion: Double?) = "${FormatoTaller.numero((fraccion ?: 0.0) * 100, 0)} %"
}
