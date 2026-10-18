package com.revscope.core.obd.taller.referencia

enum class OrigenBanda { TIPICO, FUENTE, USUARIO }

enum class PosicionEnBanda { DENTRO, BAJO, ALTO }

data class BandaReferencia(
    val clave: String,
    val min: Double?,
    val max: Double?,
    val unidad: String,
    val origen: OrigenBanda,
    val fuente: String = "",
) {
    init {
        require(min != null || max != null) { "Range $clave needs at least one limit" }
        require(min == null || max == null || min <= max) { "Range $clave has a minimum greater than its maximum" }
        require(origen != OrigenBanda.FUENTE || fuente.isNotBlank()) { "Range $clave with source origin must cite the source" }
    }

    val etiquetaOrigen: String
        get() = when (origen) {
            OrigenBanda.TIPICO -> "Typical (editable)"
            OrigenBanda.FUENTE -> "Source: $fuente"
            OrigenBanda.USUARIO -> "Edited by you"
        }

    fun clasificar(valor: Double): PosicionEnBanda = when {
        min != null && valor < min -> PosicionEnBanda.BAJO
        max != null && valor > max -> PosicionEnBanda.ALTO
        else -> PosicionEnBanda.DENTRO
    }
}
