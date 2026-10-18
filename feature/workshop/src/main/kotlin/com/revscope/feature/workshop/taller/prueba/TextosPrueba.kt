package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.pruebas.FasePaso
import com.revscope.core.obd.taller.pruebas.ModoPaso
import com.revscope.core.obd.taller.pruebas.PasoPrueba
import com.revscope.core.obd.taller.pruebas.PatronTps
import com.revscope.core.obd.taller.pruebas.TipoPrueba

internal object TextosPrueba {

    const val ACCION_LISTO = "Ready, I'm holding it steady"
    const val ACCION_TERMINAR = "Finish"
    const val CANCELAR = "Cancel test"

    fun descripcion(tipo: TipoPrueba): String = when (tipo) {
        TipoPrueba.TPS_BARRIDO ->
            "With the engine off and ignition on, move the throttle through five positions while the app reads the TPS " +
                "at the maximum rate. Each position is compared with its reference range; a slow sweep checks for " +
                "dropouts, spikes and dead spots."
        TipoPrueba.MINIMO_RETORNO ->
            "With the engine running in neutral, record 45 seconds at idle without touching the throttle, then raise " +
                "the revs to about 3,000 rpm three times and release sharply. This checks idle stability, the drop on " +
                "release and whether the engine stalls."
        TipoPrueba.ARRANQUE_FRIO ->
            "After leaving the engine overnight, compare engine and intake-air temperatures with the ignition on, " +
                "count starting attempts and track warm-up to 60 °C or until you tap Finish."
        TipoPrueba.BATERIA_CARGA ->
            "Read adapter voltage (AT RV) as quickly as it responds: battery voltage at rest with the ignition on, the " +
                "drop during starting, and charging at idle and higher revs. An adapter reset during starting is " +
                "recorded as a finding."
        TipoPrueba.MAP_BARO ->
            "With the engine off there is no vacuum, so the MAP should read barometric pressure. It is compared with " +
                "the ECU reading (PID 33), the phone barometer or an estimate from GPS altitude, each with its own tolerance."
    }

    fun paso(p: PasoPrueba): String = "${p.titulo}: ${modo(p.modo, conMeta = p.terminarCuando != null)}"

    private fun modo(m: ModoPaso, conMeta: Boolean): String = when (m) {
        is ModoPaso.Sostener -> "hold for ${segundos(m.ms)} s"
        is ModoPaso.Grabar -> "records automatically for ${segundos(m.ms)} s"
        is ModoPaso.GrabarHasta -> "records until ${if (conMeta) "the target is reached or " else ""}you tap Finish (max ${duracion(m.maxMs)})"
        is ModoPaso.Accion -> "records until the event is detected or you tap Finish (max ${duracion(m.maxMs)})"
    }

    // Espacio duro antes del «0.»: con letra grande no queda solo en el último renglón.
    fun subtexto(fase: FasePaso, modo: ModoPaso): String = when {
        fase == FasePaso.POSICIONANDO -> "When it is in position, tap Ready."
        fase == FasePaso.SOSTENIENDO -> "Hold it steady until the countdown reaches 0."
        modo is ModoPaso.Accion -> "It will finish when detected; otherwise, tap Finish."
        terminaConToque(modo) -> "Tap Finish when you are done."
        else -> "Recording starts automatically; the countdown shows how much time is left."
    }

    fun accionPrincipal(fase: FasePaso, modo: ModoPaso): String? = when {
        fase == FasePaso.POSICIONANDO -> ACCION_LISTO
        fase == FasePaso.GRABANDO && terminaConToque(modo) -> ACCION_TERMINAR
        else -> null
    }

    fun estadoFase(fase: FasePaso): String = when (fase) {
        FasePaso.POSICIONANDO -> "Position the throttle and tap Ready"
        FasePaso.SOSTENIENDO -> "Hold steady"
        FasePaso.GRABANDO -> "Recording"
    }

    // El código SAE al que apunta cada patrón, para abrir su guía en el centro DTC.
    fun codigoGuia(patron: PatronTps): String? = when (patron) {
        PatronTps.SENAL_BAJA_TODO_EL_RECORRIDO -> "P0122"
        PatronTps.SENAL_ALTA -> "P0123"
        PatronTps.CORTES_O_SALTOS -> "P0124"
        PatronTps.RANGO_DESEMPENO -> "P0121"
        PatronTps.SIN_BARRIDO, PatronTps.NORMAL -> null
    }

    fun hz(valor: Double): String = "${FormatoTaller.numero(valor, 1)} Hz"

    private fun terminaConToque(modo: ModoPaso) = modo is ModoPaso.GrabarHasta || modo is ModoPaso.Accion

    private fun segundos(ms: Long): String = FormatoTaller.compacto(ms / 1_000.0)

    // Los topes largos (el calentamiento) se leen mejor en minutos: «máx. 10 min», no «máx. 600 s».
    private fun duracion(ms: Long): String =
        if (ms >= MINUTOS_DESDE_MS) "${FormatoTaller.compacto(ms / 60_000.0)} min" else "${segundos(ms)} s"

    private const val MINUTOS_DESDE_MS = 2 * 60_000L
}
