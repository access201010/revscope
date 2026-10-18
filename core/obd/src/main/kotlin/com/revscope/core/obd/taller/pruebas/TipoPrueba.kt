package com.revscope.core.obd.taller.pruebas

enum class TipoPrueba(val titulo: String) {
    TPS_BARRIDO("TPS sweep"),
    MINIMO_RETORNO("Idle and return"),
    ARRANQUE_FRIO("Cold start"),
    BATERIA_CARGA("Battery and charging"),
    MAP_BARO("MAP vs barometric pressure"),
}
