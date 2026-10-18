package com.revscope.feature.dtc

import com.revscope.core.obd.diagnostics.BorradoDtc
import com.revscope.core.obd.diagnostics.RechazoBorradoDtc

data class ConfirmacionBorradoDtc(
    val codigos: List<String>,
    val rechazo: RechazoBorradoDtc?,
    val freezeFrameEnSesion: Boolean = false,
)

data class ResultadoBorradoUi(
    val antes: List<String>,
    val despues: List<String>,
    val rechazadoPorEcu: Boolean,
    val enviado: Boolean = true,
    val antesLeido: Boolean = true,
    val despuesLeido: Boolean = true,
)

fun resultadoBorradoUi(borrado: BorradoDtc): ResultadoBorradoUi = ResultadoBorradoUi(
    antes = borrado.antes.activos.map { it.code },
    despues = borrado.despues.activos.map { it.code },
    rechazadoPorEcu = borrado.rechazadoPorCondiciones,
    enviado = borrado.respuestaCruda != null,
    antesLeido = borrado.antes.activosConfirmados,
    despuesLeido = borrado.despues.activosConfirmados,
)

// Sin 0D reciente (muchas motos no lo reportan o no hay sondeo) la persona puede declarar que el
// vehículo está detenido; en movimiento nunca. El MCP no tiene esa salida: allí no hay nadie mirando.
fun permiteBorrarDesdeUi(rechazo: RechazoBorradoDtc?, declaraDetenido: Boolean): Boolean = when (rechazo) {
    null -> true
    RechazoBorradoDtc.SinVelocidadReciente -> declaraDetenido
    RechazoBorradoDtc.SinConfirmar, is RechazoBorradoDtc.EnMovimiento -> false
}

fun textoRechazoUi(rechazo: RechazoBorradoDtc): String = when (rechazo) {
    RechazoBorradoDtc.SinConfirmar -> "Code clearing has not been confirmed."
    RechazoBorradoDtc.SinVelocidadReciente ->
        "There is no recent speed reading to confirm that the vehicle is stationary."
    is RechazoBorradoDtc.EnMovimiento ->
        "The vehicle is travelling at ${rechazo.kmh} km/h. Stop before clearing the codes."
}

const val AVISO_READINESS_DTC =
    "Readiness monitors will be incomplete. In Colombia, tecnomecánica inspection may fail the vehicle until it completes drive cycles."
const val AVISO_REAPARICION_DTC = "Clearing a code does not repair the fault: if it is still present, the code will return."

fun avisosBorradoDtc(freezeFrameEnSesion: Boolean): List<String> =
    listOf(AVISO_READINESS_DTC, avisoFreezeFrame(freezeFrameEnSesion), AVISO_REAPARICION_DTC)

private fun avisoFreezeFrame(enSesion: Boolean): String =
    if (enSesion) {
        "The freeze frame (engine data when the fault was recorded) will be cleared from the ECU; it has been saved to the session."
    } else {
        "The freeze frame (engine data when the fault was recorded) will be lost. Save the reading to a session first."
    }

fun textoResultadoBorrado(resultado: ResultadoBorradoUi): String = when {
    resultado.rechazadoPorEcu ->
        "The ECU rejected the clear request (7F 04 22: conditions not correct). Many ECUs only clear codes with the engine " +
            "off: switch off the engine, leave the ignition on and try again. The codes are still stored."
    !resultado.enviado ->
        "Could not send the clear request: the adapter did not respond. Read the codes again before trying once more."
    resultado.despues.isNotEmpty() ->
        "The clear request was sent, but the ECU still reports ${resultado.despues.joinToString()}: the fault is still present."
    !resultado.despuesLeido ->
        "The clear request was sent, but the ECU could not be reread to confirm it. Read the codes again."
    else -> "Codes cleared. Before: ${textoAntes(resultado)}. After: no active codes."
}

private fun textoAntes(resultado: ResultadoBorradoUi): String =
    if (resultado.antesLeido) codigosOSinCodigos(resultado.antes) else "could not read codes"

private fun codigosOSinCodigos(codigos: List<String>): String = codigos.joinToString().ifEmpty { "no active codes" }
