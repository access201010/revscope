package com.revscope.feature.workshop.taller

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Verified
import androidx.compose.ui.graphics.vector.ImageVector

data class AccionesHub(
    val onIniciarDiagnostico: () -> Unit = {},
    val onAbrirSesion: (Long) -> Unit = {},
    val onConectarAdaptador: () -> Unit = {},
    val onElegirVehiculo: () -> Unit = {},
    val onVerTodas: () -> Unit = {},
    val onCodigos: () -> Unit = {},
    val onSensores: () -> Unit = {},
    val onChequeo: () -> Unit = {},
    val onPruebasGuiadas: (() -> Unit)? = null,
    val onMultimetro: (() -> Unit)? = null,
    val onAlDia: () -> Unit = {},
    val onMezcla: () -> Unit = {},
    val onEscaner: () -> Unit = {},
    val onOndaO2: () -> Unit = {},
    val onMode06: () -> Unit = {},
    val onMecanicoIa: () -> Unit = {},
    val onMarchas: () -> Unit = {},
    val onPerfiles: () -> Unit = {},
    val onMantenimiento: () -> Unit = {},
    val onOdometro: () -> Unit = {},
    val onVelocimetros: () -> Unit = {},
    val onConocimientoModelo: () -> Unit = {},
)

data class HerramientaTaller(
    val icono: ImageVector,
    val titulo: String,
    val descripcion: String,
    val requiereAdaptador: Boolean,
    val onAbrir: () -> Unit,
)

data class GrupoHerramientas(val titulo: String, val herramientas: List<HerramientaTaller>)

internal object HerramientasTaller {

    private const val ANCHO_MINIMO_DOS_COLUMNAS_DP = 240f

    // Con letra grande una rejilla de dos columnas parte las palabras: se pasa a una sola.
    fun columnas(anchoDisponibleDp: Float, escalaLetra: Float): Int =
        if (anchoDisponibleDp / escalaLetra >= ANCHO_MINIMO_DOS_COLUMNAS_DP) 2 else 1

    fun rapidas(a: AccionesHub): List<HerramientaTaller> = listOfNotNull(
        HerramientaTaller(
            Icons.Filled.BugReport,
            "Fault codes",
            "Read, understand and clear fault codes",
            true,
            a.onCodigos,
        ),
        a.onPruebasGuiadas?.let {
            HerramientaTaller(
                Icons.Filled.Science,
                "Guided tests",
                "TPS sweep, idle, battery and more",
                true,
                it,
            )
        },
        a.onMultimetro?.let {
            HerramientaTaller(
                Icons.Filled.ElectricalServices,
                "Multimeter",
                "Log your readings for each wire",
                false,
                it,
            )
        },
        HerramientaTaller(
            Icons.Filled.Timeline,
            "Sensors and capture",
            "Live traces and quick capture",
            true,
            a.onSensores,
        ),
        HerramientaTaller(
            Icons.Filled.MonitorHeart,
            "Health check",
            "Fault codes, readiness monitors, mixture and battery",
            false,
            a.onChequeo,
        ),
    )

    fun mas(a: AccionesHub): List<GrupoHerramientas> = listOf(
        GrupoHerramientas(
            "Vehicle checks",
            listOf(
                HerramientaTaller(
                    Icons.Filled.Verified,
                    "Colombian vehicle checks",
                    "Colombian services: SOAT, tecnomecánica, pico y placa, fines and comprehensive cover",
                    false,
                    a.onAlDia,
                ),
            ),
        ),
        GrupoHerramientas(
            "Diagnostics",
            listOf(
                HerramientaTaller(
                    Icons.Filled.Science,
                    "Mixture and combustion",
                    "Live interpretation of fuel trims, O2 sensor, lambda and MAF",
                    true,
                    a.onMezcla,
                ),
                HerramientaTaller(
                    Icons.Filled.Search,
                    "Advanced scanner (Mode 22)",
                    "Discover manufacturer-specific PIDs",
                    true,
                    a.onEscaner,
                ),
                HerramientaTaller(
                    Icons.AutoMirrored.Filled.ShowChart,
                    "O2 sensor waveform",
                    "Live oxygen sensor voltage graph",
                    true,
                    a.onOndaO2,
                ),
                HerramientaTaller(
                    Icons.AutoMirrored.Filled.FactCheck,
                    "On-board test results (Mode 06)",
                    "Manufacturer's internal monitoring tests",
                    true,
                    a.onMode06,
                ),
                HerramientaTaller(
                    Icons.AutoMirrored.Filled.Chat,
                    "AI Mechanic",
                    "Ask the mechanic: it already knows your vehicle's live status",
                    false,
                    a.onMecanicoIa,
                ),
            ),
        ),
        GrupoHerramientas(
            "Vehicle",
            listOf(
                HerramientaTaller(
                    Icons.Filled.MenuBook,
                    "Model information",
                    "ECU, parts, wiring and sourced specifications",
                    false,
                    a.onConocimientoModelo,
                ),
                HerramientaTaller(
                    Icons.Filled.Settings,
                    "Gear analyser",
                    "Calibrate RPM/speed ratios by gear",
                    true,
                    a.onMarchas,
                ),
                HerramientaTaller(
                    Icons.Filled.DirectionsCar,
                    "Vehicle profiles",
                    "Saved vehicles, redline and VIN",
                    false,
                    a.onPerfiles,
                ),
                HerramientaTaller(
                    Icons.Filled.Build,
                    "Maintenance",
                    "Oil, tyres, battery and other mileage-based items",
                    false,
                    a.onMantenimiento,
                ),
                HerramientaTaller(
                    Icons.Filled.Speed,
                    "Odometer check",
                    "ECU odometer and history to check for tampering",
                    false,
                    a.onOdometro,
                ),
                HerramientaTaller(
                    Icons.AutoMirrored.Filled.CompareArrows,
                    "Compare speed readings",
                    "Live OBD speed compared with GPS",
                    true,
                    a.onVelocimetros,
                ),
            ),
        ),
    )
}
