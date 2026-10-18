package com.revscope.core.obd.taller.sesion

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.BandasTipicas
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.workshop.MetricasChequeo
import kotlin.math.abs

enum class MetricaComparada(val etiqueta: String) {
    CODIGOS("Fault codes"),
    AJUSTE_LARGO("Long-term trim"),
    CORRECCION_TOTAL("Total correction"),
    VOLTAJE("Voltage"),
    MOTOR("Engine"),
    MONITORES("Readiness monitors"),
}

data class FilaComparacion(
    val metrica: MetricaComparada,
    val base: String,
    val ahora: String,
    val cambio: String,
    val veredicto: Veredicto,
    val referencia: String? = null,
)

data class UmbralesComparacion(val ajuste: BandaReferencia, val voltaje: BandaReferencia) {
    companion object {
        private val TIPICAS = BandasTipicas.para(VehicleType.MOTORCYCLE)

        fun desde(bandas: Map<String, BandaReferencia>) = UmbralesComparacion(
            ajuste = bandas[ClavesBanda.CHEQUEO_AJUSTE_DELTA_PP] ?: TIPICAS.getValue(ClavesBanda.CHEQUEO_AJUSTE_DELTA_PP),
            voltaje = bandas[ClavesBanda.CHEQUEO_VOLTAJE_DELTA_V] ?: TIPICAS.getValue(ClavesBanda.CHEQUEO_VOLTAJE_DELTA_V),
        )
    }
}

// Una base es un solo chequeo: las diferencias numéricas piden atención como mucho, nunca se declaran falla.
object ComparadorChequeo {

    private const val SIN_DATO = "No data"
    private const val VACIO = "—"
    private const val ECT_OPERACION_C = 60.0

    fun comparar(base: MetricasChequeo, ahora: MetricasChequeo, umbrales: UmbralesComparacion): List<FilaComparacion> =
        listOfNotNull(
            codigos(base, ahora),
            porcentaje(MetricaComparada.AJUSTE_LARGO, base.ltft, ahora.ltft, umbrales.ajuste),
            porcentaje(MetricaComparada.CORRECCION_TOTAL, base.correccionTotal, ahora.correccionTotal, umbrales.ajuste),
            voltaje(base, ahora, umbrales.voltaje),
            motor(base.ect, ahora.ect),
            monitores(base, ahora),
        )

    private fun codigos(base: MetricasChequeo, ahora: MetricasChequeo): FilaComparacion {
        if (!base.dtcsLeidos || !ahora.dtcsLeidos) {
            return FilaComparacion(
                MetricaComparada.CODIGOS, textoCodigos(base), textoCodigos(ahora),
                "Fault codes were not read in one of the checks", Veredicto.INFO,
            )
        }
        val nuevos = ahora.dtcs - base.dtcs.toSet()
        val (cambio, veredicto) = when {
            nuevos.isNotEmpty() -> "New fault code: ${nuevos.joinToString()}" to Veredicto.FALLA
            ahora.dtcs.isNotEmpty() -> "Still present: ${ahora.dtcs.joinToString()}" to Veredicto.ATENCION
            base.dtcs.isNotEmpty() -> "No longer present: ${base.dtcs.joinToString()}" to Veredicto.OK
            else -> "No fault codes in either check" to Veredicto.OK
        }
        return FilaComparacion(MetricaComparada.CODIGOS, textoCodigos(base), textoCodigos(ahora), cambio, veredicto)
    }

    private fun textoCodigos(m: MetricasChequeo): String = when {
        !m.dtcsLeidos -> SIN_DATO
        m.dtcs.isEmpty() -> "None"
        else -> m.dtcs.joinToString()
    }

    private fun porcentaje(metrica: MetricaComparada, base: Double?, ahora: Double?, umbral: BandaReferencia): FilaComparacion? {
        if (base == null && ahora == null) return null
        val texto = { v: Double? -> v?.let { "${FormatoTaller.conSigno(it, 1)} %" } ?: VACIO }
        return numerica(metrica, base, ahora, umbral, texto) { "Changed by ${FormatoTaller.conSigno(it, 1)} pp" }
    }

    private fun voltaje(base: MetricasChequeo, ahora: MetricasChequeo, umbral: BandaReferencia): FilaComparacion? {
        if (base.voltaje == null && ahora.voltaje == null) return null
        val texto = { v: Double? -> v?.let { "${FormatoTaller.numero(it, 1)} V" } ?: VACIO }
        if (estadosDistintos(base.motorEncendido, ahora.motorEncendido)) {
            return FilaComparacion(
                MetricaComparada.VOLTAJE, texto(base.voltaje), texto(ahora.voltaje),
                "Not comparable: engine ${estadoMotor(base.motorEncendido)} at baseline and ${estadoMotor(ahora.motorEncendido)} now",
                Veredicto.INFO,
            )
        }
        return numerica(MetricaComparada.VOLTAJE, base.voltaje, ahora.voltaje, umbral, texto) {
            "Changed by ${FormatoTaller.conSigno(it, 1)} V"
        }
    }

    private fun numerica(
        metrica: MetricaComparada,
        base: Double?,
        ahora: Double?,
        umbral: BandaReferencia,
        texto: (Double?) -> String,
        textoCambio: (Double) -> String,
    ): FilaComparacion {
        val referencia = referencia(umbral)
        if (base == null || ahora == null) {
            return FilaComparacion(metrica, texto(base), texto(ahora), "No data to compare", Veredicto.INFO, referencia)
        }
        val delta = FormatoTaller.redondear(ahora - base, 2)
        val limite = umbral.max ?: 0.0
        val (cambio, veredicto) =
            if (abs(delta) > limite) textoCambio(delta) to Veredicto.ATENCION
            else "No significant change (${FormatoTaller.conSigno(delta, 1)})" to Veredicto.OK
        return FilaComparacion(metrica, texto(base), texto(ahora), cambio, veredicto, referencia)
    }

    private fun referencia(umbral: BandaReferencia): String =
        "±${FormatoTaller.compacto(umbral.max ?: 0.0)} ${umbral.unidad} · ${umbral.etiquetaOrigen}"

    private fun estadosDistintos(base: Boolean?, ahora: Boolean?): Boolean = base != null && ahora != null && base != ahora

    private fun estadoMotor(encendido: Boolean?): String = if (encendido == true) "running" else "off"

    private fun motor(base: Double?, ahora: Double?): FilaComparacion? {
        if (base == null && ahora == null) return null
        val texto = { v: Double? -> v?.let { "${FormatoTaller.numero(it, 0)} °C" } ?: VACIO }
        return FilaComparacion(MetricaComparada.MOTOR, texto(base), texto(ahora), contextoMotor(base, ahora), Veredicto.INFO)
    }

    private fun contextoMotor(base: Double?, ahora: Double?): String = when {
        base == null || ahora == null -> "Context: data missing from one of the checks"
        esFrio(ahora) && !esFrio(base) -> "Context: the engine is cold now; cold fuel trims are not directly comparable"
        !esFrio(ahora) && esFrio(base) -> "Context: the baseline was taken with the engine cold"
        else -> "Context: similar temperature"
    }

    private fun esFrio(ect: Double): Boolean = ect < ECT_OPERACION_C

    private fun monitores(base: MetricasChequeo, ahora: MetricasChequeo): FilaComparacion? {
        if (base.monitoresTotales == null && ahora.monitoresTotales == null) return null
        val texto = { m: MetricasChequeo ->
            if (m.monitoresTotales == null) VACIO else "${m.monitoresCompletos} of ${m.monitoresTotales}"
        }
        val completosBase = base.monitoresCompletos
        val completosAhora = ahora.monitoresCompletos
        val (cambio, veredicto) = when {
            completosBase == null || completosAhora == null -> "No data to compare" to Veredicto.INFO
            completosAhora < completosBase ->
                "Fewer monitors complete: were fault codes cleared or was the battery disconnected?" to Veredicto.ATENCION
            else -> "No significant change" to Veredicto.OK
        }
        return FilaComparacion(MetricaComparada.MONITORES, texto(base), texto(ahora), cambio, veredicto)
    }
}
