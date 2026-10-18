package com.revscope.core.obd.taller.multimetro

internal object TextosMultimetro {

    // ── Sensores con referencia, masa y señal ───────────────────────────────

    fun tituloSensor(plantilla: PlantillaCableado, celdas: List<EvaluacionCelda>): String {
        val partes = listOfNotNull(
            alimentacion(estado(celdas, FuncionCable.REF_5V), estado(celdas, FuncionCable.MASA)),
            senal(plantilla, celdas.filter { it.funcion == FuncionCable.SENAL }),
        )
            return partes.joinToString("; ").ifEmpty { "No readings yet" }.capitalizar()
    }

    fun interpretacionSensor(celdas: List<EvaluacionCelda>): String? {
        val ref = estado(celdas, FuncionCable.REF_5V)
        val masa = estado(celdas, FuncionCable.MASA)
        val senal = estado(celdas, FuncionCable.SENAL)
        return when {
            ref == EstadoGrupo.BAJO -> REFERENCIA_BAJA
            masa == EstadoGrupo.ALTO -> MASA_CON_CAIDA
            senal == EstadoGrupo.BAJO -> senalBaja(ref, masa)
            senal == EstadoGrupo.ALTO -> SENAL_ALTA
                todoCorrecto(ref, masa, senal) -> "The sensor circuit is responding within typical ranges."
            else -> null
        }
    }

    private fun estado(celdas: List<EvaluacionCelda>, funcion: FuncionCable): EstadoGrupo =
        VeredictoMultimetro.grupo(celdas.filter { it.funcion == funcion })

    private fun alimentacion(ref: EstadoGrupo, masa: EstadoGrupo): String? {
        if (ref == EstadoGrupo.CORRECTO && masa == EstadoGrupo.CORRECTO) return "reference and earth correct"
        val partes = listOfNotNull(textoReferencia(ref), textoMasa(masa))
        return partes.takeIf { it.isNotEmpty() }?.joinToString(" and ")
    }

    private fun textoReferencia(e: EstadoGrupo): String? = when (e) {
        EstadoGrupo.CORRECTO -> "reference correct"
        EstadoGrupo.BAJO -> "5 V reference low"
        EstadoGrupo.ALTO -> "5 V reference high"
        EstadoGrupo.MIXTO -> "reference unstable"
        EstadoGrupo.SIN_MEDIR, EstadoGrupo.SIN_REFERENCIA -> null
    }

    private fun textoMasa(e: EstadoGrupo): String? = when (e) {
        EstadoGrupo.CORRECTO -> "earth correct"
        EstadoGrupo.ALTO -> "earth with voltage drop"
        EstadoGrupo.BAJO -> "earth below 0 V (check the probes)"
        EstadoGrupo.MIXTO -> "earth unstable"
        EstadoGrupo.SIN_MEDIR, EstadoGrupo.SIN_REFERENCIA -> null
    }

    private fun senal(plantilla: PlantillaCableado, celdas: List<EvaluacionCelda>): String? = when (VeredictoMultimetro.grupo(celdas)) {
        EstadoGrupo.SIN_MEDIR -> null
        EstadoGrupo.SIN_REFERENCIA -> "signal has no expected value: compare it with the manual"
        EstadoGrupo.CORRECTO -> "signal within range"
        EstadoGrupo.BAJO -> "signal low ${donde(plantilla, celdas, EstadoCelda.BAJO)}"
        EstadoGrupo.ALTO -> "signal high ${donde(plantilla, celdas, EstadoCelda.ALTO)}"
        EstadoGrupo.MIXTO ->
              "signal low ${donde(plantilla, celdas, EstadoCelda.BAJO)} and high ${donde(plantilla, celdas, EstadoCelda.ALTO)}"
    }

    // «En todo el recorrido» solo si se midieron todas las posiciones y todas salieron del mismo lado.
    private fun donde(plantilla: PlantillaCableado, celdas: List<EvaluacionCelda>, estado: EstadoCelda): String {
        val fuera = celdas.filter { it.estado == estado }
        val posiciones = plantilla.cable(FuncionCable.SENAL)?.esperadoPorCondicion?.keys.orEmpty()
        val todas = posiciones.size > 1 && fuera.map { it.condicion }.toSet() == posiciones
        if (todas) return "throughout the sweep"
        return "(" + enumerar(fuera.map { etiquetaCondicion(plantilla, it.condicion) }) + ")"
    }

    private fun senalBaja(ref: EstadoGrupo, masa: EstadoGrupo): String =
        if (ref == EstadoGrupo.CORRECTO && masa == EstadoGrupo.CORRECTO) SENSOR_O_CONTACTO
        else "Also measure the reference and earth: if both are correct, ${SENSOR_O_CONTACTO.replaceFirstChar(Char::lowercase)}"

    private fun todoCorrecto(vararg grupos: EstadoGrupo): Boolean =
        grupos.any { it == EstadoGrupo.CORRECTO } && grupos.all { it == EstadoGrupo.CORRECTO || it == EstadoGrupo.SIN_MEDIR }

    // ── Plantillas sin señal: inyector y batería ────────────────────────────

    fun tituloGeneral(plantilla: PlantillaCableado, celdas: List<EvaluacionCelda>): String {
        val fuera = celdas.filter { it.fueraDeBanda }
        return when {
            fuera.isNotEmpty() -> "Outside range: " + enumerar(fuera.map { celdaCorta(plantilla, it) })
            celdas.any { it.estado == EstadoCelda.DENTRO } -> "All readings are within range"
            celdas.isEmpty() -> "No readings yet"
            else -> "No expected values available for comparison"
        }
    }

    fun interpretacionGeneral(plantilla: PlantillaCableado, celdas: List<EvaluacionCelda>): String? =
        celdas.filter { it.fueraDeBanda }
            .mapNotNull { PISTAS[Triple(plantilla.sensor, it.condicion, it.estado)] }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" ")

    private fun celdaCorta(plantilla: PlantillaCableado, c: EvaluacionCelda): String {
        val cable = plantilla.cable(c.funcion)?.etiqueta ?: c.funcion.etiqueta
        val lado = if (c.estado == EstadoCelda.BAJO) "low" else "high"
            return "${cable.lowercase()}, ${etiquetaCondicion(plantilla, c.condicion)} ($lado)"
    }

    // ── Comunes ─────────────────────────────────────────────────────────────

    fun etiquetaCondicion(plantilla: PlantillaCableado, clave: String): String =
        plantilla.condicion(clave)?.etiqueta?.lowercase() ?: clave

    fun enumerar(partes: List<String>): String = when (partes.size) {
        0 -> ""
        1 -> partes.single()
        else -> partes.dropLast(1).joinToString(", ") + " and " + partes.last()
    }

    private fun String.capitalizar(): String = replaceFirstChar(Char::uppercase)

    private const val SENSOR_O_CONTACTO =
        "Points to the sensor (damaged or not equivalent to the original) or a poor connection between the sensor and plug."
    private const val REFERENCIA_BAJA =
        "With a low reference, every sensor sharing it will read low (MAP commonly shares it with TPS): check the ECU's " +
            "5 V supply before replacing a sensor."
    private const val MASA_CON_CAIDA =
        "An earth voltage drop adds to the signal: check the sensor earth terminal and its path back to the ECU."
    private const val SENAL_ALTA =
        "Points to the sensor or a short between the signal and 5 V reference."

    private val PISTAS = mapOf(
        Triple(SensorMultimetro.BATERIA, CondicionesMultimetro.CONTACTO, EstadoCelda.BAJO) to
              "Battery low at rest: charge it and measure again before assessing starting and charging.",
        Triple(SensorMultimetro.BATERIA, CondicionesMultimetro.ARRANQUE, EstadoCelda.BAJO) to
              "Large voltage drop while starting: consistent with a weak battery or loose or corroded terminals.",
        Triple(SensorMultimetro.BATERIA, CondicionesMultimetro.CARGA, EstadoCelda.BAJO) to
              "Insufficient charging: consistent with a faulty stator or regulator/rectifier.",
        Triple(SensorMultimetro.BATERIA, CondicionesMultimetro.CARGA, EstadoCelda.ALTO) to
              "Overcharging: consistent with a regulator/rectifier that is not limiting the charge.",
        Triple(SensorMultimetro.INYECTOR, CondicionesMultimetro.DESCONECTADO, EstadoCelda.BAJO) to
              "Low resistance: consistent with a shorted coil or low-impedance injector (compare with the manual).",
        Triple(SensorMultimetro.INYECTOR, CondicionesMultimetro.DESCONECTADO, EstadoCelda.ALTO) to
              "High resistance: consistent with an open coil or corroded terminal."
    )
}
