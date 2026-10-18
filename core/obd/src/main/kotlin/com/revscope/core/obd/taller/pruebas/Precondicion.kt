package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.taller.FormatoTaller

data class ContextoPrueba(
    val conectado: Boolean,
    val lecturas: Map<String, ObdReading>,
    val soportado: (String) -> Boolean,
    val ahoraMs: Long,
) {
    // Una lectura vieja (el sondeo pausado, un viaje anterior) no dice cómo está la moto ahora.
    fun reciente(pid: String): ObdReading? = lecturas[pid]?.takeIf { ahoraMs - it.timestamp <= VIGENCIA_MS }

    fun velocidadKmh(): Double? = listOfNotNull(reciente(PID_VELOCIDAD), reciente(ObdSessionManager.GPS_SPEED_PID))
        .maxOfOrNull { it.value }

    companion object {
        const val VIGENCIA_MS = 10_000L
        const val PID_RPM = "0C"
        const val PID_VELOCIDAD = "0D"
    }
}

// [aviso]: se puede seguir, pero hay algo que el técnico debe saber o confirmar (p. ej. un arranque tibio).
data class ResultadoPrecondicion(val texto: String, val cumple: Boolean, val queHacer: String? = null, val aviso: Boolean = false)

sealed interface Precondicion {
    fun evaluar(ctx: ContextoPrueba): ResultadoPrecondicion

    data object AdaptadorConectado : Precondicion {
        override fun evaluar(ctx: ContextoPrueba) = if (ctx.conectado) {
            ResultadoPrecondicion("Adapter connected", cumple = true)
        } else {
            ResultadoPrecondicion("No adapter connected", cumple = false, "Connect the adapter and switch on the ignition")
        }
    }

    data class MotorApagado(val queHacer: String) : Precondicion {
        override fun evaluar(ctx: ContextoPrueba): ResultadoPrecondicion {
            val rpm = ctx.reciente(ContextoPrueba.PID_RPM)?.value
                ?: return ResultadoPrecondicion("Engine off (RPM reading unavailable)", cumple = true)
            if (rpm < 1.0) return ResultadoPrecondicion("Engine off (RPM = 0)", cumple = true)
            return ResultadoPrecondicion("Engine running (${FormatoTaller.numero(rpm, 0)} rpm)", cumple = false, queHacer)
        }
    }

    data class MotorEncendido(val minRpm: Double = MIN_RPM_ENCENDIDO) : Precondicion {
        override fun evaluar(ctx: ContextoPrueba): ResultadoPrecondicion {
            val rpm = ctx.reciente(ContextoPrueba.PID_RPM)?.value
                ?: return ResultadoPrecondicion("No RPM reading", cumple = false, ENCENDER)
            if (rpm > minRpm) return ResultadoPrecondicion("Engine running (${FormatoTaller.numero(rpm, 0)} rpm)", cumple = true)
            return ResultadoPrecondicion("Engine off or about to stall (${FormatoTaller.numero(rpm, 0)} rpm)", cumple = false, ENCENDER)
        }

        private companion object {
            const val MIN_RPM_ENCENDIDO = 400.0
            const val ENCENDER = "Start the engine and let it idle without touching the throttle"
        }
    }

    // La ECU no informa la marcha por OBD: lo confirma el técnico.
    data object EnNeutro : Precondicion {
        override fun evaluar(ctx: ContextoPrueba) = ResultadoPrecondicion(
            "Gearbox in neutral: please confirm (the ECU does not report this)",
            cumple = true,
            "You will rev the engine to about 3,000 rpm: put the gearbox in neutral before starting",
            aviso = true,
        )
    }

    // Frío si motor y aire difieren menos de 5 °C (típico tras ≥ 6 h de reposo). Tibio no impide la prueba:
    // el resultado sale etiquetado como «arranque tibio».
    data object MotorFrio : Precondicion {
        const val DIFERENCIA_MAX_C = 5.0

        override fun evaluar(ctx: ContextoPrueba): ResultadoPrecondicion {
            val ect = ctx.reciente(PID_ECT)?.value
            val iat = ctx.reciente(PID_IAT)?.value
            if (ect == null || iat == null) return sinTemperaturas()
            val medidas = "engine ${grados(ect)}, air ${grados(iat)}"
            if (ect - iat < DIFERENCIA_MAX_C) return ResultadoPrecondicion("Cold engine ($medidas)", cumple = true)
            return ResultadoPrecondicion(
                "Warm engine ($medidas): this will be treated as a warm start",
                cumple = true,
                "For a true cold start, repeat after leaving the engine overnight (typically at least 6 hours)",
                aviso = true,
            )
        }

        private fun sinTemperaturas() = ResultadoPrecondicion(
            "No engine or intake-air temperature reading",
            cumple = true,
            "The ignition-on step will determine this; if the ECU does not report them, the result cannot confirm whether the engine was cold",
            aviso = true,
        )

        private fun grados(x: Double) = "${FormatoTaller.numero(x, 0)} °C"

        private const val PID_ECT = "05"
        private const val PID_IAT = "0F"
    }

    // No se puede leer si la farola está prendida: se avisa para que el voltaje en reposo se lea con esa carga.
    data object LucesConContacto : Precondicion {
        override fun evaluar(ctx: ContextoPrueba) = ResultadoPrecondicion(
            "Headlamp and lights: on many motorbikes the headlamp stays on with the ignition (AHO)",
            cumple = true,
            "This lowers the resting voltage and is accounted for in the range; switch the lights off before starting on a car",
            aviso = true,
        )
    }

    // Sin PID 33 la prueba sigue con el barómetro del teléfono o la altitud GPS, que es una estimación.
    data object BarometricaDisponible : Precondicion {
        override fun evaluar(ctx: ContextoPrueba) = if (ctx.soportado(PID_BARO)) {
            ResultadoPrecondicion("ECU barometric pressure available (PID $PID_BARO)", cumple = true)
        } else {
            ResultadoPrecondicion(
                "The ECU does not report barometric pressure (PID $PID_BARO)",
                cumple = true,
                "The phone's barometer will be used, or GPS altitude if it has none (estimated, ±3 kPa): enable location",
                aviso = true,
            )
        }

        private const val PID_BARO = "33"
    }

    data object MotoDetenida : Precondicion {
        override fun evaluar(ctx: ContextoPrueba): ResultadoPrecondicion {
            val kmh = ctx.velocidadKmh()
                ?: return ResultadoPrecondicion("Motorbike stationary (no speed reading: please confirm)", cumple = true)
            if (kmh <= 0.0) return ResultadoPrecondicion("Motorbike stationary (0 km/h)", cumple = true)
            return ResultadoPrecondicion(
                "Motorbike moving (${FormatoTaller.numero(kmh, 0)} km/h)",
                cumple = false,
                "Stop the motorbike: this test must be carried out while stationary",
            )
        }
    }

    data class PidDisponible(val pid: String, val nombre: String, val alternativa: String) : Precondicion {
        override fun evaluar(ctx: ContextoPrueba) = if (ctx.soportado(pid)) {
            ResultadoPrecondicion("$nombre available on this ECU (PID $pid)", cumple = true)
        } else {
            ResultadoPrecondicion("$nombre unavailable on this ECU (PID $pid)", cumple = false, alternativa)
        }
    }
}
