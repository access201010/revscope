package com.revscope.core.obd.taller.pid

import com.revscope.core.obd.protocol.ResponseParser

data class CapacidadesEcu(
    val codigoProtocolo: String?,
    val protocolo: String,
    val seleccionAutomatica: Boolean,
    val esKLine: Boolean,
    val pidsAnunciados: List<String>,
    val pidsPorRango: Map<String, List<String>>,
    val tasaEsperada: String,
    val tasaMedidaHz: Double?,
    val explicacion: String,
) {
    val cantidadPids: Int get() = pidsAnunciados.size

    companion object {
        private val CODIGOS = setOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C')
        private val CODIGOS_K_LINE = setOf('3', '4', '5')
        private val CODIGOS_CAN = setOf('6', '7', '8', '9', 'A', 'B', 'C')
        private val RANGOS = linkedMapOf(
            "01 00" to 0x01..0x20,
            "01 20" to 0x21..0x40,
            "01 40" to 0x41..0x60,
            "01 60" to 0x61..0x80,
        )
        private val PID_HEX = Regex("^[0-9A-F]{1,2}$")

        fun desde(
            atDpn: String?,
            pidsAnunciados: Collection<String>,
            tasaMedidaHz: Double? = null,
        ): CapacidadesEcu {
            val protocolo = interpretarProtocolo(atDpn)
            val pids = normalizarPids(pidsAnunciados)
            return CapacidadesEcu(
                codigoProtocolo = protocolo.codigo?.toString(),
                protocolo = nombreProtocolo(protocolo.codigo),
                seleccionAutomatica = protocolo.automatico,
                esKLine = protocolo.codigo in CODIGOS_K_LINE,
                pidsAnunciados = pids,
                pidsPorRango = agruparPorRango(pids),
                tasaEsperada = tasaEsperada(protocolo.codigo),
                tasaMedidaHz = tasaMedidaHz?.takeIf { it.isFinite() && it > 0.0 },
                explicacion = explicacion(protocolo.codigo),
            )
        }

        private fun interpretarProtocolo(atDpn: String?): ProtocoloDpn {
            val limpio = atDpn?.let(ResponseParser::cleanResponse).orEmpty()
            val automatico = limpio.length == 2 && limpio.startsWith('A')
            val codigo = if (automatico) limpio.last() else limpio.singleOrNull()
            return ProtocoloDpn(codigo?.takeIf { it in CODIGOS }, automatico)
        }

        private fun nombreProtocolo(codigo: Char?): String = when (codigo) {
            '0' -> "Automatic (protocol not detected)"
            '1' -> "SAE J1850 PWM"
            '2' -> "SAE J1850 VPW"
            '3' -> "ISO 9141-2 (K-line)"
            '4' -> "ISO 14230-4 (K-line, 5-baud initialisation)"
            '5' -> "ISO 14230-4 (K-line, fast initialisation)"
            '6' -> "ISO 15765-4 CAN (11 bits, 500 kbit/s)"
            '7' -> "ISO 15765-4 CAN (29 bits, 500 kbit/s)"
            '8' -> "ISO 15765-4 CAN (11 bits, 250 kbit/s)"
            '9' -> "ISO 15765-4 CAN (29 bits, 250 kbit/s)"
            'A' -> "SAE J1939 CAN (29 bits, 250 kbit/s)"
            'B' -> "USER1 CAN (11 bits, 125 kbit/s)"
            'C' -> "USER2 CAN (11 bits, 50 kbit/s)"
            else -> "Unidentified protocol"
        }

        private fun normalizarPids(pids: Collection<String>): List<String> = pids
            .map(String::trim)
            .map(String::uppercase)
            .filter(PID_HEX::matches)
            .map { it.padStart(2, '0') }
            .filter { it.toInt(16) in 0x01..0x80 }
            .distinct()
            .sortedBy { it.toInt(16) }

        private fun agruparPorRango(pids: List<String>): Map<String, List<String>> = RANGOS.mapValues { (_, rango) ->
            pids.filter { it.toInt(16) in rango }
        }

        private fun tasaEsperada(codigo: Char?): String = when (codigo) {
            in CODIGOS_K_LINE -> "Low: one request at a time; measure it during capture"
            in CODIGOS_CAN -> "High; depends on the ECU and requested PIDs"
            '1', '2' -> "Moderate; depends on the adapter and ECU"
            else -> "Not estimated; measure it during capture"
        }

        private fun explicacion(codigo: Char?): String = if (codigo in CODIGOS_K_LINE) {
            "K-line motorbike ECUs (for example, Delphi MT05) expose few PIDs; " +
                "RevScope shows only those advertised by the ECU."
        } else {
            "RevScope shows only the PIDs advertised by the ECU."
        }
    }
}

private data class ProtocoloDpn(val codigo: Char?, val automatico: Boolean)
