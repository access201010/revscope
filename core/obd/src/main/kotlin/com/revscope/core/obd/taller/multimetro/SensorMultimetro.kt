package com.revscope.core.obd.taller.multimetro

enum class SensorMultimetro(val titulo: String) {
    TPS("Throttle position sensor (TPS)"),
    MAP("Manifold absolute pressure sensor (MAP)"),
    ECT("Engine coolant temperature sensor (ECT)"),
    IAT("Intake air temperature sensor (IAT)"),
    INYECTOR("Injector"),
    BATERIA("Battery and charging"),
}
