package com.revscope.core.obd.taller.dtc

import com.revscope.core.obd.taller.multimetro.SensorMultimetro
import com.revscope.core.obd.taller.pruebas.TipoPrueba

data class CatalogoDtc(
    val version: Int,
    val fuenteDescripciones: String,
    val aviso: String,
    val guias: List<GuiaDtc>,
)

data class GuiaDtc(
    val codigo: String,
    val titulo: String,
    val sistema: String,
    val urgencia: UrgenciaDtc,
    val causas: List<String>,
    val verificaciones: List<VerificacionDtc>,
    val notasMoto: List<String>,
    val relacionados: List<String>,
)

data class VerificacionDtc(val paso: String, val detalle: String, val accion: AccionGuia?)

enum class UrgenciaDtc(val etiqueta: String) {
    DETENERSE("Stop and check before riding or driving further"),
    REVISAR_PRONTO("Check soon"),
    PUEDE_ESPERAR("Can wait until the next service"),
}

sealed interface AccionGuia {
    val clave: String
    val etiqueta: String

    data class Prueba(val tipo: TipoPrueba) : AccionGuia {
        override val clave get() = "$PREFIJO_PRUEBA${tipo.name}"
        override val etiqueta get() = "Guided test: ${tipo.titulo}"
    }

    data class Multimetro(val sensor: SensorMultimetro) : AccionGuia {
        override val clave get() = "$PREFIJO_MULTIMETRO${sensor.name}"
        override val etiqueta get() = "Measure with multimeter: ${sensor.titulo}"
    }

    data object BorrarCodigos : AccionGuia {
        override val clave = "DTC:BORRAR"
        override val etiqueta = "Clear codes"
    }

    companion object {
        private const val PREFIJO_PRUEBA = "PRUEBA:"
        private const val PREFIJO_MULTIMETRO = "MULTIMETRO:"

        fun desde(clave: String): AccionGuia? = when {
            clave == BorrarCodigos.clave -> BorrarCodigos
            clave.startsWith(PREFIJO_PRUEBA) -> prueba(clave.removePrefix(PREFIJO_PRUEBA))
            clave.startsWith(PREFIJO_MULTIMETRO) -> multimetro(clave.removePrefix(PREFIJO_MULTIMETRO))
            else -> null
        }

        private fun prueba(nombre: String): AccionGuia? =
            TipoPrueba.entries.firstOrNull { it.name == nombre }?.let(::Prueba)

        private fun multimetro(nombre: String): AccionGuia? =
            SensorMultimetro.entries.firstOrNull { it.name == nombre }?.let(::Multimetro)
    }
}
