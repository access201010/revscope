package com.revscope.core.obd.taller.multimetro

// La unidad va con espacio duro: en una columna angosta «5 V» no se parte en dos renglones.
enum class FuncionCable(val etiqueta: String) {
    REF_5V("5 V reference"),
    MASA("Earth"),
    SENAL("Signal"),
    ALIMENTACION_12V("12 V supply"),
    CALEFACTOR("Heater"),
    OTRO("Other"),
}
