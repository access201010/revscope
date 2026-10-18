package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.pruebas.AnalizadorArranqueFrio.Pasos as PasosArranque
import com.revscope.core.obd.taller.pruebas.AnalizadorBarridoTps.Pasos
import com.revscope.core.obd.taller.pruebas.AnalizadorBateria.Pasos as PasosBateria
import com.revscope.core.obd.taller.pruebas.AnalizadorMapBaro.Pasos as PasosMap
import com.revscope.core.obd.taller.pruebas.AnalizadorMinimo.Pasos as PasosMinimo
import com.revscope.core.obd.telemetry.captura.MuestraCaptura

// Las pruebas guiadas del Taller, cada una con su analizador.
object CatalogoPruebas {

    private const val SOSTENER_MS = 5_000L
    private const val BARRIDO_LENTO_MS = 8_000L
    private const val PID_TPS_RELATIVO = "45"
    private const val MINIMO_MS = 45_000L
    private const val RETORNO_MS = 8_000L
    private const val CONTACTO_MS = 10_000L
    private const val ARRANQUE_MAX_MS = 15_000L
    private const val CALENTAMIENTO_MAX_MS = 10 * 60_000L
    private const val RAFAGA_ARRANQUE_MS = 8_000L
    private const val CARGA_MINIMO_MS = 15_000L
    private const val RPM_ALTAS_MS = 10_000L

    val barridoTps = DefinicionPrueba(
        tipo = TipoPrueba.TPS_BARRIDO,
        pids = listOf(AnalizadorBarridoTps.PID_TPS),
        pidsOpcionales = listOf(PID_TPS_RELATIVO),
        precondiciones = listOf(
            Precondicion.AdaptadorConectado,
            Precondicion.MotorApagado(
                "Switch off the engine but leave the ignition on: opening the throttle fully with the engine running is not safe",
            ),
            Precondicion.MotoDetenida,
            Precondicion.PidDisponible(
                AnalizadorBarridoTps.PID_TPS,
                "TPS",
                "Measure the TPS signal with the multimeter (TPS template)",
            ),
        ),
        pasos = listOf(
            sostener(Pasos.CERRADO_1, "Release the throttle completely and do not touch it"),
            sostener(Pasos.MEDIO, "Move the throttle halfway and hold it steady"),
            sostener(Pasos.A_FONDO, "Open the throttle fully and hold it there"),
            sostener(Pasos.CERRADO_2, "Release the throttle completely again"),
            PasoPrueba(
                clave = Pasos.BARRIDO_LENTO,
                titulo = TextosBarridoTps.TITULOS_PASO.getValue(Pasos.BARRIDO_LENTO),
                instruccion = "Move slowly and steadily from fully closed to fully open, then back again",
                modo = ModoPaso.Grabar(BARRIDO_LENTO_MS),
                descartarInicioMs = 0,
            ),
        ),
        analizar = AnalizadorBarridoTps::analizar,
    )

    val minimoRetorno = DefinicionPrueba(
        tipo = TipoPrueba.MINIMO_RETORNO,
        pids = listOf(AnalizadorMinimo.PID_RPM, AnalizadorMinimo.PID_TPS, AnalizadorMinimo.PID_ECT),
        precondiciones = listOf(
            Precondicion.AdaptadorConectado,
            Precondicion.MotorEncendido(),
            Precondicion.MotoDetenida,
            Precondicion.EnNeutro,
                Precondicion.PidDisponible(AnalizadorMinimo.PID_RPM, "RPM", "Idle speed cannot be measured without RPM: check the connection to the ECU"),
        ),
        pasos = listOf(
            PasoPrueba(
                clave = PasosMinimo.MINIMO,
                titulo = TextosMinimo.TITULOS_PASO.getValue(PasosMinimo.MINIMO),
                instruccion = "Do not touch the throttle: let the engine idle",
                modo = ModoPaso.Grabar(MINIMO_MS),
            ),
        ) + PasosMinimo.RETORNOS.map(::retorno),
        analizar = AnalizadorMinimo::analizar,
    )

    val arranqueFrio = DefinicionPrueba(
        tipo = TipoPrueba.ARRANQUE_FRIO,
        pids = listOf(AnalizadorArranqueFrio.PID_RPM, AnalizadorArranqueFrio.PID_ECT, AnalizadorArranqueFrio.PID_IAT),
        pidsOpcionales = listOf(AnalizadorArranqueFrio.PID_TPS, AnalizadorArranqueFrio.PID_AMBIENTE),
        precondiciones = listOf(
            Precondicion.AdaptadorConectado,
            Precondicion.MotorApagado("Switch off the engine but leave the ignition on: the test starts before you start the engine"),
            Precondicion.MotoDetenida,
            Precondicion.MotorFrio,
            Precondicion.PidDisponible(
                AnalizadorArranqueFrio.PID_ECT,
                "Engine temperature",
                "Warm-up cannot be tracked without it: measure the sensor with the multimeter (ECT template)",
            ),
        ),
        pasos = listOf(
            PasoPrueba(
                clave = PasosArranque.CONTACTO,
                titulo = TextosArranqueFrio.TITULOS_PASO.getValue(PasosArranque.CONTACTO),
                instruccion = "Ignition on, engine off: do not start it yet",
                modo = ModoPaso.Grabar(CONTACTO_MS),
                pidPrincipal = AnalizadorArranqueFrio.PID_ECT,
            ),
            PasoPrueba(
                clave = PasosArranque.ARRANQUE,
                titulo = TextosArranqueFrio.TITULOS_PASO.getValue(PasosArranque.ARRANQUE),
                instruccion = "Start the engine as you normally would",
                modo = ModoPaso.Accion(ARRANQUE_MAX_MS),
                descartarInicioMs = 0,
                terminarCuando = CriterioFin(AnalizadorArranqueFrio::arrancoSostenido),
                pidPrincipal = AnalizadorArranqueFrio.PID_RPM,
            ),
            PasoPrueba(
                clave = PasosArranque.CALENTAMIENTO,
                titulo = TextosArranqueFrio.TITULOS_PASO.getValue(PasosArranque.CALENTAMIENTO),
                instruccion = "Let it idle without revving; you can finish before it reaches 60 °C",
                modo = ModoPaso.GrabarHasta(CALENTAMIENTO_MAX_MS),
                descartarInicioMs = 0,
                terminarCuando = CriterioFin(::calentado),
                pidPrincipal = AnalizadorArranqueFrio.PID_ECT,
            ),
        ),
        analizar = AnalizadorArranqueFrio::analizar,
    )

    val bateriaCarga = DefinicionPrueba(
        tipo = TipoPrueba.BATERIA_CARGA,
        pids = listOf(AnalizadorBateria.PID_VOLTAJE),
        precondiciones = listOf(
            Precondicion.AdaptadorConectado,
            Precondicion.MotorApagado("Switch off the engine but leave the ignition on: the test measures the battery at rest before starting"),
            Precondicion.MotoDetenida,
            Precondicion.LucesConContacto,
        ),
        pasos = listOf(
            PasoPrueba(
                clave = PasosBateria.CONTACTO,
                titulo = TextosBateria.TITULOS_PASO.getValue(PasosBateria.CONTACTO),
                instruccion = "Ignition on, engine off: do not start it yet",
                modo = ModoPaso.Grabar(CONTACTO_MS),
            ),
            PasoPrueba(
                clave = PasosBateria.ARRANQUE,
                titulo = TextosBateria.TITULOS_PASO.getValue(PasosBateria.ARRANQUE),
                instruccion = "Start the engine as you normally would",
                modo = ModoPaso.Grabar(RAFAGA_ARRANQUE_MS),
                descartarInicioMs = 0,
            ),
            PasoPrueba(
                clave = PasosBateria.MINIMO,
                titulo = TextosBateria.TITULOS_PASO.getValue(PasosBateria.MINIMO),
                instruccion = "Let the engine idle without revving",
                modo = ModoPaso.Grabar(CARGA_MINIMO_MS),
            ),
            PasoPrueba(
                clave = PasosBateria.RPM_ALTAS,
                titulo = TextosBateria.TITULOS_PASO.getValue(PasosBateria.RPM_ALTAS),
                instruccion = "In neutral, raise the revs to about 3,000–4,000 rpm and hold them there",
                modo = ModoPaso.Sostener(RPM_ALTAS_MS),
            ),
        ),
        analizar = AnalizadorBateria::analizar,
        fuente = FuenteMuestras.VOLTAJE_ADAPTADOR,
        analizarSiSeCortaEn = setOf(PasosBateria.ARRANQUE),
    )

    val mapBaro = DefinicionPrueba(
        tipo = TipoPrueba.MAP_BARO,
        pids = listOf(AnalizadorMapBaro.PID_MAP),
        pidsOpcionales = listOf(AnalizadorMapBaro.PID_BARO),
        precondiciones = listOf(
            Precondicion.AdaptadorConectado,
            Precondicion.MotorApagado("Switch off the engine but leave the ignition on: a running engine creates vacuum and lowers the MAP reading"),
            Precondicion.MotoDetenida,
            Precondicion.PidDisponible(
                AnalizadorMapBaro.PID_MAP,
                "MAP",
                "Measure the MAP signal with the multimeter (MAP template)",
            ),
            Precondicion.BarometricaDisponible,
        ),
        pasos = listOf(
            PasoPrueba(
                clave = PasosMap.CONTACTO,
                titulo = TextosMapBaro.TITULOS_PASO.getValue(PasosMap.CONTACTO),
                instruccion = "Ignition on, engine off: do not touch the throttle",
                modo = ModoPaso.Grabar(CONTACTO_MS),
            ),
        ),
        analizar = AnalizadorMapBaro::analizar,
        usaAmbiente = true,
    )

    private val definiciones = listOf(barridoTps, minimoRetorno, arranqueFrio, bateriaCarga, mapBaro).associateBy { it.tipo }

    val disponibles: List<TipoPrueba> get() = TipoPrueba.entries.filter { it in definiciones }

    fun definicion(tipo: TipoPrueba): DefinicionPrueba? = definiciones[tipo]

    private fun retorno(clave: String) = PasoPrueba(
        clave = clave,
        titulo = TextosMinimo.TITULOS_PASO.getValue(clave),
        instruccion = "Raise the revs to about 3,000 rpm, then release the throttle sharply",
        modo = ModoPaso.Grabar(RETORNO_MS),
        descartarInicioMs = 0,
    )

    private fun calentado(muestras: List<MuestraCaptura>): Boolean =
        muestras.lastOrNull { it.pid == AnalizadorArranqueFrio.PID_ECT }?.let { it.valor >= AnalizadorArranqueFrio.CALENTADO_C } == true

    private fun sostener(clave: String, instruccion: String) = PasoPrueba(
        clave = clave,
        titulo = TextosBarridoTps.TITULOS_PASO.getValue(clave),
        instruccion = instruccion,
        modo = ModoPaso.Sostener(SOSTENER_MS),
    )
}
