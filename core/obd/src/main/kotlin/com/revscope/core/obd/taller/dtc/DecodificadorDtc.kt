package com.revscope.core.obd.taller.dtc

enum class SistemaDtc(val letra: Char, val nombre: String) {
    TREN_MOTRIZ('P', "Powertrain (engine and transmission)"),
    CHASIS('C', "Chassis"),
    CARROCERIA('B', "Body"),
    RED('U', "Network communication between modules"),
}

enum class AmbitoDtc(val etiqueta: String) {
    GENERICO("Generic, SAE-defined"),
    FABRICANTE("Manufacturer-specific"),
}

data class EstructuraDtc(
    val codigo: String,
    val sistema: SistemaDtc,
    val ambito: AmbitoDtc,
    val subsistema: String?,
) {
    val explicacion: String
        get() = when (ambito) {
            AmbitoDtc.FABRICANTE -> DecodificadorDtc.MENSAJE_FABRICANTE
            AmbitoDtc.GENERICO -> listOfNotNull(
                "Generic SAE-defined fault code",
                sistema.nombre.enMinuscula(),
                subsistema?.let { "subsystem: ${it.enMinuscula()}" },
            ).joinToString(" · ")
        }

    private fun String.enMinuscula(): String = replaceFirstChar { it.lowercaseChar() }
}

// Estructura de SAE J2012: letra = sistema, 2.º carácter = genérico o del fabricante, 3.º = subsistema (P genéricos).
object DecodificadorDtc {

    const val MENSAJE_FABRICANTE = "Manufacturer-specific code: check the vehicle's service manual"
    const val EJEMPLO_FORMATO = "a P, C, B or U followed by four characters, for example P0122"

    private val FORMATO = Regex("^[PCBU][0-3][0-9A-F]{3}$")

    private val SUBSISTEMAS_P0 = mapOf(
        '0' to "Fuel and air metering and auxiliary emissions controls",
        '1' to "Fuel and air metering",
        '2' to "Fuel and air metering (injector circuit)",
        '3' to "Ignition system or misfire",
        '4' to "Auxiliary emissions controls",
        '5' to "Vehicle speed, idle control and auxiliary inputs",
        '6' to "ECU and output circuits",
        '7' to "Transmission",
        '8' to "Transmission",
        '9' to "Transmission",
        'A' to "Hybrid propulsion",
        'B' to "Hybrid propulsion",
        'C' to "Hybrid propulsion",
    )

    private val SUBSISTEMAS_P2 = mapOf(
        '0' to "Fuel and air metering and auxiliary emissions controls",
        '1' to "Fuel and air metering",
        '2' to "Fuel and air metering",
        '3' to "Ignition system or misfire",
        '4' to "Auxiliary emissions controls",
        '5' to "Auxiliary inputs",
        '6' to "ECU and auxiliary outputs",
        '7' to "Transmission",
    )

    fun normalizar(texto: String): String? = texto.trim().uppercase().takeIf(FORMATO::matches)

    fun decodificar(texto: String): EstructuraDtc? {
        val codigo = normalizar(texto) ?: return null
        val sistema = SistemaDtc.entries.first { it.letra == codigo[0] }
        val ambito = ambitoDe(codigo)
        return EstructuraDtc(codigo, sistema, ambito, subsistemaDe(codigo, ambito))
    }

    fun mensajeSinGuia(estructura: EstructuraDtc): String = when (estructura.ambito) {
        AmbitoDtc.FABRICANTE -> "$MENSAJE_FABRICANTE. RevScope does not make up its description."
        AmbitoDtc.GENERICO ->
            "Not in RevScope's local guide: check SAE J2012 or the vehicle's service manual for the exact description."
    }

    private fun ambitoDe(codigo: String): AmbitoDtc =
        if (codigo[0] == 'P') ambitoTrenMotriz(codigo) else ambitoOtrosSistemas(codigo[1])

    // P0, P2 y P34-P39 son de SAE; P1 y P30-P33 los define cada fabricante.
    private fun ambitoTrenMotriz(codigo: String): AmbitoDtc = when (codigo[1]) {
        '1' -> AmbitoDtc.FABRICANTE
        '3' -> if (codigo[2] in '0'..'3') AmbitoDtc.FABRICANTE else AmbitoDtc.GENERICO
        else -> AmbitoDtc.GENERICO
    }

    // En C, B y U: 0 es de SAE, 1 y 2 del fabricante y 3 está reservado por SAE.
    private fun ambitoOtrosSistemas(segundo: Char): AmbitoDtc =
        if (segundo == '1' || segundo == '2') AmbitoDtc.FABRICANTE else AmbitoDtc.GENERICO

    private fun subsistemaDe(codigo: String, ambito: AmbitoDtc): String? {
        if (codigo[0] != 'P' || ambito != AmbitoDtc.GENERICO) return null
        return when (codigo[1]) {
            '0' -> SUBSISTEMAS_P0[codigo[2]]
            '2' -> SUBSISTEMAS_P2[codigo[2]]
            else -> null
        }
    }
}
