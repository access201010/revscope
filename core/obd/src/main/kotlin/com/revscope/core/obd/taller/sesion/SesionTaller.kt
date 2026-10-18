package com.revscope.core.obd.taller.sesion

data class SesionTaller(
    val id: Long = 0,
    val vehiculoId: Long,
    val claveModelo: String? = null,
    val inicio: Long,
    val cierre: Long? = null,
    val titulo: String,
    val sintomas: Set<Sintoma> = emptySet(),
    val sintomasTexto: String = "",
    val notas: String = "",
    val odometroKm: Double? = null,
    val chequeoBaseId: Long? = null,
    val interpretacion: String = "",
    val pasosMarcados: Set<String> = emptySet(),
) {
    val abierta: Boolean get() = cierre == null
}

enum class Sintoma(val etiqueta: String) {
    NO_ARRANCA("Does not start"),
    ARRANQUE_DIFICIL_FRIO("Hard to start when cold"),
    NO_SOSTIENE_MINIMO_FRIO("Will not idle when cold"),
    MINIMO_INESTABLE("Unstable idle"),
    SE_APAGA_AL_SOLTAR("Stalls when the throttle is released"),
    SE_AHOGA_AL_ACELERAR("Hesitates when accelerating"),
    PIERDE_POTENCIA("Loss of power"),
    TIRONES("Jerking or surging"),
    CONSUMO_ALTO("High fuel consumption"),
    MIL_ENCENDIDA("Engine warning light on"),
    BATERIA_DESCARGA("Battery discharges"),
    SOBRECALIENTA("Overheating"),
    OTRO("Other"),
}

data class EventoTaller(
    val id: Long = 0,
    val sesionId: Long,
    val instante: Long,
    val tipo: TipoEvento,
    val origen: OrigenEvento = OrigenEvento.APP,
    val titulo: String,
    val resumen: String = "",
    val veredicto: Veredicto = Veredicto.INFO,
    val payloadVersion: Int = 1,
    val payloadJson: String = "{}",
    val adjunto: String? = null,
)

enum class TipoEvento {
    NOTA, SINTOMAS, DTC_LECTURA, DTC_BORRADO, CHEQUEO, PRUEBA_GUIADA, CAPTURA,
    MEDICION_MULTIMETRO, INSTANTANEA_SENSORES,
}

enum class OrigenEvento { APP, MCP }

enum class Veredicto { OK, ATENCION, FALLA, INFO }
