package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.pruebas.AnalizadorBateria.Pasos
import com.revscope.core.obd.taller.referencia.ClavesBanda

// Redacción de batería y carga: cada voltaje con su banda y origen, el desfase de AT RV siempre dicho y
// «compatible con…», nunca un dictamen. Las unidades van con espacio duro.
object TextosBateria {

    val TITULOS_PASO = mapOf(
        Pasos.CONTACTO to "Contacto",
        Pasos.ARRANQUE to "Arranque",
        Pasos.MINIMO to "Idle",
        Pasos.RPM_ALTAS to "Higher revs",
    )

    const val AVISO_FAROLA = "On many motorbikes, the headlamp stays on with the ignition (AHO), which lowers the voltage."
    const val NOTA_AT_RV = "AT RV measures voltage at the OBD connector with a typical error of ±0.1–0.2 V. Calibrate the vehicle offset " +
        "with a multimeter across the battery terminals for a more accurate reading."

    fun resultado(a: AnalisisBateria) = ResultadoPrueba(
        tipo = TipoPrueba.BATERIA_CARGA,
        veredicto = a.patron.veredicto,
        titulo = titulo(a),
        interpretacion = interpretacion(a),
        siguientePaso = siguientePaso(a),
        hallazgos = hallazgos(a),
        bajaConfianza = a.bajaConfianza,
        detalle = a,
    )

    fun titulo(a: AnalisisBateria): String = when (a.patron) {
        PatronBateria.SIN_DATOS -> "No adapter voltage readings"
        PatronBateria.SOBRECARGA -> "Overcharging: ${v(a.cargaMaxV)} with the engine running"
        PatronBateria.ARRANQUE_BAJO -> "Voltage drops to ${v(a.arranque?.valleV)} while starting"
        PatronBateria.ADAPTADOR_REINICIADO -> "Adapter reset while starting"
        PatronBateria.NO_CARGA -> "Not charging: ${v(a.rpmAltas?.mediaV)} at higher revs"
        PatronBateria.CARGA_ALTA -> "High charging voltage: ${v(a.rpmAltas?.mediaV)} at higher revs"
        PatronBateria.SIN_CARGA_EN_MINIMO -> "Charges at higher revs but not at idle"
        PatronBateria.BATERIA_BAJA -> "Charging is normal, but the battery is low at rest"
        PatronBateria.NORMAL -> "Battery and charging voltage within range"
    }

    // ── Interpretación ──────────────────────────────────────────────────────

    private fun interpretacion(a: AnalisisBateria): String {
        if (a.patron == PatronBateria.SIN_DATOS) return "The adapter returned no voltage readings (AT RV), so there is nothing to measure."
        return listOfNotNull(
            fraseContacto(a),
            fraseArranque(a),
            fraseCarga(a),
            fraseVeredicto(a),
            fraseDesfase(a.desfase),
        ).joinToString(" ")
    }

    private fun fraseContacto(a: AnalisisBateria): String? {
        val contacto = a.contacto ?: return null
        val banda = banda(a, AnalizadorBateria.claveContacto(a.conFarola))
        val condicion = if (a.conFarola) "with the headlamp on" else "with no electrical loads"
        val farola = if (a.conFarola) " $AVISO_FAROLA" else ""
        return "With the ignition on and engine off, the reading was ${v(contacto.mediaV)}; $banda is expected $condicion.$farola"
    }

    private fun fraseArranque(a: AnalisisBateria): String? {
        val r = a.arranque ?: return null
        if (r.adaptadorReiniciado) return fraseReinicio(r)
        if (!r.visto) return "No voltage drop was detected while starting."
        return "While starting, voltage fell to ${v(r.valleV)} (${v(r.caidaV)} below the resting value); ${banda(a, ClavesBanda.ARRANQUE_MIN_V)} is expected."
    }

    private fun fraseReinicio(r: ArranqueVoltaje): String {
        val ultimo = r.valleV?.let { " The lowest recorded reading was ${v(it)}." }.orEmpty()
        val cuanto = if (r.enlacePerdido) {
            "the connection to the adapter was lost"
        } else {
            "the adapter did not respond for ${seg(r.sinRespuestaMs / 1_000.0)} s"
        }
        return "During starting, $cuanto and reset. This usually happens when voltage drops significantly " +
            "(typically below ~${v(AnalizadorBateria.REINICIO_TIPICO_V, 0)}, depending on the adapter).$ultimo"
    }

    private fun fraseCarga(a: AnalisisBateria): String? {
        val altas = a.rpmAltas
        val minimo = a.minimo
        if (altas == null && minimo == null) return null
        val partes = listOfNotNull(minimo?.let { "${v(it.mediaV)} at idle" }, altas?.let { "${v(it.mediaV)} at higher revs" })
        return "With the engine running, ${partes.joinToString(" and ")}; charging voltage should be ${banda(a, ClavesBanda.CARGA_V)}."
    }

    private fun fraseVeredicto(a: AnalisisBateria): String? = when (a.patron) {
        PatronBateria.SOBRECARGA -> "Above the maximum (${banda(a, ClavesBanda.SOBRECARGA_MAX_V)}): consistent with a " +
            "regulator/rectifier that is not limiting the charge; this can damage the battery and lights."
        PatronBateria.ARRANQUE_BAJO -> "Consistent with a weak or sulphated battery, loose or corroded terminals or earths, " +
            "or a starter motor drawing too much current."
        PatronBateria.NO_CARGA -> "Consistent with a faulty regulator/rectifier, stator or connector, or an open charging fuse."
        PatronBateria.CARGA_ALTA -> "Slightly above range but not overcharging: consistent with a regulator starting to fail."
        PatronBateria.SIN_CARGA_EN_MINIMO ->
            "Voltage does not rise above the resting value at idle. This can be normal on small motorbikes, but the battery will discharge with the lights on."
        PatronBateria.BATERIA_BAJA -> "Consistent with a discharged battery or a drain when the ignition is off."
        else -> null
    }

    private fun fraseDesfase(d: DesfaseVoltaje): String = if (d.calibrado) {
        "Voltage corrected using the vehicle offset (${conSigno(d.voltios)}, ${d.origen})."
    } else {
        NOTA_AT_RV
    }

    // ── Hallazgos y siguiente paso ──────────────────────────────────────────

    private fun hallazgos(a: AnalisisBateria): List<String> = buildList {
        val r = a.arranque
        if (r?.adaptadorReiniciado == true) add("The adapter reset while starting: severe voltage drop (typically below ~8 V)")
        if (r != null && !r.adaptadorReiniciado && !r.visto && r.valleV != null) {
            add("No voltage drop detected while starting: the engine may not have been started during that step")
        }
        if (a.bajaConfianza) add("At ${hz(r?.tasaHz)} readings per second, the actual voltage dip may be deeper than recorded")
        if (a.patron == PatronBateria.SOBRECARGA) add("Overcharging: ${v(a.cargaMaxV)} with the engine running")
        if (a.patron == PatronBateria.ARRANQUE_BAJO) add("Voltage fell to ${v(r?.valleV)} while starting")
        if (a.patron == PatronBateria.NO_CARGA) add("Reading at higher revs was ${v(a.rpmAltas?.mediaV)}: not charging")
    }

    private fun siguientePaso(a: AnalisisBateria): String? = when (a.patron) {
        PatronBateria.SIN_DATOS -> "Check that the adapter responds to AT RV and repeat the test"
        PatronBateria.ARRANQUE_BAJO, PatronBateria.ADAPTADOR_REINICIADO ->
            "Measure the battery across its terminals while starting (battery template), and check the terminals and earth connections"
        PatronBateria.SOBRECARGA, PatronBateria.NO_CARGA, PatronBateria.CARGA_ALTA, PatronBateria.SIN_CARGA_EN_MINIMO ->
            "Measure charging voltage across the battery terminals (battery template), and check the regulator/rectifier, stator and connector"
        PatronBateria.BATERIA_BAJA -> "Charge the battery and repeat the test; if it drops again at rest, check for a drain with the ignition off"
        PatronBateria.NORMAL -> null
    }

    // ── Medidas: una línea por cifra, para la pantalla y el informe ──────────

    fun medidas(a: AnalisisBateria): List<String> = listOfNotNull(
        a.contacto?.let { "Contacto: ${tramo(it)}" },
        a.arranque?.let(::medidaArranque),
        a.minimo?.let { "Idle: ${tramo(it)}" },
        a.rpmAltas?.let { "Rpm altas: ${tramo(it)}" },
        "Offset: " + if (a.desfase.calibrado) "${conSigno(a.desfase.voltios)} (${a.desfase.origen})" else "not calibrated (AT RV ±0.1–0.2 V, typical)",
    )

    private fun medidaArranque(r: ArranqueVoltaje): String = when {
        r.adaptadorReiniciado -> "Starting: adapter reset" + (r.valleV?.let { " (last reading, ${v(it)})" } ?: "")
        !r.visto -> "Starting: no visible voltage drop (${hz(r.tasaHz)} readings/s)"
        else -> "Starting: low ${v(r.valleV)}, drop of ${v(r.caidaV)} (${hz(r.tasaHz)} readings/s)"
    }

    // ── Formato ─────────────────────────────────────────────────────────────

    private fun tramo(t: TramoVoltaje): String {
        val desde = FormatoTaller.numero(t.minV, 1)
        val hasta = FormatoTaller.numero(t.maxV, 1)
        return if (desde == hasta) v(t.mediaV) else "${v(t.mediaV)} (de $desde a ${v(t.maxV)})"
    }

    private fun banda(a: AnalisisBateria, clave: String): String {
        val c = a.comprobacion(clave)?.banda ?: return "the typical range"
        return TextoBanda.citar(c, 1)
    }

    private fun v(x: Double?, decimales: Int = 1) = "${FormatoTaller.numero(x ?: 0.0, decimales)} V"

    private fun conSigno(x: Double) = "${FormatoTaller.conSigno(x, 2)} V"

    private fun hz(x: Double?) = FormatoTaller.numero(x ?: 0.0, 0)

    private fun seg(x: Double) = FormatoTaller.numero(x, 1)
}
