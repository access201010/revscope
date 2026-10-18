package com.revscope.core.obd.taller.multimetro

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.referencia.ClavesBanda

object CondicionesMultimetro {
    const val CERRADO = "CERRADO"
    const val MEDIO = "MEDIO"
    const val FONDO = "FONDO"
    const val CONTACTO = "CONTACTO"
    const val DESCONECTADO = "DESCONECTADO"
    const val ARRANQUE = "ARRANQUE"
    const val CARGA = "CARGA"
}

// Plantillas típicas sin colores: los colores cambian con cada modelo y solo se muestran si vienen del modelo.
object PlantillasGenericas {

    private const val POR_DETRAS =
        "Back-probe the connector without unplugging it, with the ignition on and engine off: touch the black probe " +
            "to battery negative and the red probe to each wire."

    fun para(sensor: SensorMultimetro, tipo: VehicleType): PlantillaCableado = when (sensor) {
        SensorMultimetro.TPS -> tps()
        SensorMultimetro.MAP -> map()
        SensorMultimetro.ECT -> temperatura(SensorMultimetro.ECT)
        SensorMultimetro.IAT -> temperatura(SensorMultimetro.IAT)
        SensorMultimetro.INYECTOR -> inyector()
        SensorMultimetro.BATERIA -> bateria(tipo)
    }

    private fun tps(): PlantillaCableado {
        val condiciones = listOf(
            Condicion(CondicionesMultimetro.CERRADO, "Throttle closed"),
            Condicion(CondicionesMultimetro.MEDIO, "Half throttle"),
            Condicion(CondicionesMultimetro.FONDO, "Full throttle"),
        )
        val claves = condiciones.map { it.clave }
        val senal = mapOf(
            CondicionesMultimetro.CERRADO to Esperado.Banda(ClavesBanda.TPS_CERRADO_V),
            CondicionesMultimetro.MEDIO to Esperado.EntreCondiciones(CondicionesMultimetro.CERRADO, CondicionesMultimetro.FONDO),
            CondicionesMultimetro.FONDO to Esperado.Banda(ClavesBanda.TPS_FONDO_V),
        )
        return PlantillaCableado(
            sensor = SensorMultimetro.TPS,
            titulo = "3-wire TPS",
            cables = listOf(
                cable(FuncionCable.REF_5V, claves.associateWith { Esperado.Banda(ClavesBanda.REF_5V_V) }),
                cable(FuncionCable.MASA, claves.associateWith { Esperado.Banda(ClavesBanda.MASA_V) }),
                cable(FuncionCable.SENAL, senal),
            ),
            condiciones = condiciones,
            unidad = "V",
            dondeMedir = "$POR_DETRAS Move the throttle to each position and hold it there while taking a reading.",
            condicionesEcu = setOf(CondicionesMultimetro.CERRADO, CondicionesMultimetro.FONDO),
        )
    }

    private fun map() = PlantillaCableado(
        sensor = SensorMultimetro.MAP,
        titulo = "3-wire MAP",
        cables = listOf(
            cable(FuncionCable.REF_5V, contacto(Esperado.Banda(ClavesBanda.REF_5V_V))),
            cable(FuncionCable.MASA, contacto(Esperado.Banda(ClavesBanda.MASA_V))),
            cable(
                FuncionCable.SENAL,
                contacto(Esperado.SinValor("Proportional to barometric pressure: compare it with the MAP test")),
            ),
        ),
        condiciones = listOf(condicionContacto()),
        unidad = "V",
        dondeMedir = POR_DETRAS,
    )

    private fun temperatura(sensor: SensorMultimetro) = PlantillaCableado(
        sensor = sensor,
        titulo = if (sensor == SensorMultimetro.ECT) "2-wire engine temperature sensor" else "2-wire intake air temperature sensor",
        cables = listOf(
            cable(
                FuncionCable.SENAL,
                contacto(Esperado.SinValor("Depends on temperature: compare it with the manual's table")),
            ),
            cable(FuncionCable.MASA, contacto(Esperado.Banda(ClavesBanda.MASA_V))),
        ),
        condiciones = listOf(condicionContacto()),
        unidad = "V",
        dondeMedir = POR_DETRAS,
    )

    private fun inyector() = PlantillaCableado(
        sensor = SensorMultimetro.INYECTOR,
        titulo = "Injector resistance",
        cables = listOf(
            Cable(
                FuncionCable.OTRO,
                color = null,
                esperadoPorCondicion = mapOf(CondicionesMultimetro.DESCONECTADO to Esperado.Banda(ClavesBanda.INYECTOR_OHM)),
                etiqueta = "Coil (across terminals)",
            ),
        ),
        condiciones = listOf(Condicion(CondicionesMultimetro.DESCONECTADO, "Unplugged")),
        unidad = "Ω",
        dondeMedir = "Ignition off, injector unplugged: set the multimeter to ohms and measure across the two injector terminals.",
    )

    private fun bateria(tipo: VehicleType): PlantillaCableado {
        val reposo = if (tipo == VehicleType.MOTORCYCLE) ClavesBanda.BATERIA_CONTACTO_CON_LUCES_V else ClavesBanda.BATERIA_CONTACTO_V
        return PlantillaCableado(
            sensor = SensorMultimetro.BATERIA,
            titulo = "Battery and charging",
            cables = listOf(
                Cable(
                    FuncionCable.ALIMENTACION_12V,
                    color = null,
                    esperadoPorCondicion = mapOf(
                        CondicionesMultimetro.CONTACTO to Esperado.Banda(reposo),
                        CondicionesMultimetro.ARRANQUE to Esperado.Banda(ClavesBanda.ARRANQUE_MIN_V),
                        CondicionesMultimetro.CARGA to Esperado.Banda(ClavesBanda.CARGA_V),
                    ),
                    etiqueta = "Battery terminals",
                ),
            ),
            condiciones = listOf(
                Condicion(CondicionesMultimetro.CONTACTO, "Ignition on"),
                Condicion(CondicionesMultimetro.ARRANQUE, "While starting"),
                Condicion(CondicionesMultimetro.CARGA, "Engine at 3,000 rpm"),
            ),
            unidad = "V",
            dondeMedir = "At the battery terminals, connect the red probe to positive and the black probe to negative. " +
                "While starting, note the lowest reading."
        )
    }

    private fun cable(funcion: FuncionCable, esperado: Map<String, Esperado>) = Cable(funcion, color = null, esperadoPorCondicion = esperado)

    private fun contacto(esperado: Esperado) = mapOf(CondicionesMultimetro.CONTACTO to esperado)

    private fun condicionContacto() = Condicion(CondicionesMultimetro.CONTACTO, "Contacto")
}
