package com.revscope.core.obd.diagnostics

import com.revscope.core.obd.protocol.DtcServicio

object AvisoLecturaDtc {

    const val ACTIVOS_SIN_RESPUESTA =
        "The ECU did not respond to the active fault-code request (03): this does not confirm that there are no faults. " +
            "Read the codes again."
    const val ENLACE_PERDIDO_AL_FINAL = "Connection was lost at the end of the read: freeze-frame data may be incomplete."

    fun de(scan: DtcScan): String? = when {
        !scan.activosConfirmados -> ACTIVOS_SIN_RESPUESTA
        scan.serviciosFallidos.isNotEmpty() -> serviciosSinRespuesta(scan.serviciosFallidos)
        scan.enlacePerdido -> ENLACE_PERDIDO_AL_FINAL
        else -> null
    }

    fun sinCodigos(scan: DtcScan): String? {
        if (scan.todos.isNotEmpty() || !scan.activosConfirmados) return null
        val leidos = DtcServicio.entries.filterNot { it in scan.serviciosFallidos }
        return "No fault codes ${enumerar(leidos.map(::etiqueta), "or")}"
    }

    fun etiqueta(servicio: DtcServicio): String = when (servicio) {
        DtcServicio.ACTIVOS -> "active"
        DtcServicio.PENDIENTES -> "pending"
        DtcServicio.PERMANENTES -> "permanent"
    }

    private fun serviciosSinRespuesta(servicios: Set<DtcServicio>): String {
        val lista = servicios.sortedBy { it.ordinal }.map { "${etiqueta(it)} (${it.comando})" }
        return "The ECU did not respond to ${enumerar(lista, "and")} fault codes. Some ECUs do not support these requests; " +
            "active codes were read."
    }

    private fun enumerar(partes: List<String>, conector: String): String =
        if (partes.size < 2) partes.joinToString() else partes.dropLast(1).joinToString(", ") + " $conector " + partes.last()
}
