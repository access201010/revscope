package com.revscope.feature.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.TextoAjustable
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.workshop.SpeedDeltaAverager
import kotlin.math.roundToInt

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val AccentColor = Color(0xFFE8FF00)
private val TextColor = Color(0xFFE6E8F0)
private val TextMutedColor = Color(0xFF6B7089)
private val WarnColor = Color(0xFFFFC107)

@Composable
fun SpeedComparisonScreen(
    onNavigateBack: () -> Unit,
    viewModel: SpeedComparisonViewModel = hiltViewModel(),
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val obdSpeed by viewModel.obdSpeedKmh.collectAsState()
    val gpsSpeed by viewModel.gpsSpeedKmh.collectAsState()
    val averageDeltaPercent by viewModel.averageDeltaPercent.collectAsState()
    SpeedComparisonContent(
        conectado = connectionState is ConnectionState.Connected,
        velocidadObd = obdSpeed,
        velocidadGps = gpsSpeed,
        promedioPorcentaje = averageDeltaPercent,
        onReiniciar = viewModel::resetAverage,
        onBack = onNavigateBack,
    )
}

@Composable
internal fun SpeedComparisonContent(
    conectado: Boolean,
    velocidadObd: Double?,
    velocidadGps: Double?,
    promedioPorcentaje: Double?,
    onReiniciar: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(BgColor)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextColor)
            }
            Text(
                "Compare speedometers",
                color = TextColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }

        when {
            !conectado -> EmptyState("Connect the adapter to compare speed readings")
            velocidadGps == null -> EmptyState(
                "Waiting for GPS signal — make sure you have a clear view of the sky and are moving",
            )
            else -> ComparisonContent(
                obdSpeed = velocidadObd ?: 0.0,
                gpsSpeed = velocidadGps,
                averageDeltaPercent = promedioPorcentaje,
                onReset = onReiniciar,
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceColor,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Text(message, color = TextMutedColor, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun ComparisonContent(
    obdSpeed: Double,
    gpsSpeed: Double?,
    averageDeltaPercent: Double?,
    onReset: () -> Unit,
) {
    val gps = gpsSpeed ?: 0.0
    val deltaAbs = obdSpeed - gps
    val deltaPercent = deltaPercentOrNull(obdSpeed, gps)

    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SpeedColumn("OBD", obdSpeed, Modifier.weight(1f))
        SpeedColumn("GPS", gps, Modifier.weight(1f))
    }

    Spacer(Modifier.height(20.dp))
    DeltaCard(deltaAbs, deltaPercent)

    Spacer(Modifier.height(16.dp))
    AverageCard(averageDeltaPercent, onReset)

    Spacer(Modifier.height(16.dp))
    Text(
        "The vehicle speedometer usually reads higher than the actual GPS speed. " +
            "Measure on a straight road at a constant speed.",
        color = TextMutedColor,
        fontSize = 12.sp,
    )
}

private fun deltaPercentOrNull(obdKmh: Double, gpsKmh: Double): Double? =
    if (gpsKmh > SpeedDeltaAverager.MIN_GPS_SPEED_KMH) SpeedDeltaAverager.deltaPercent(obdKmh, gpsKmh) else null

@Composable
private fun SpeedColumn(label: String, speed: Double, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TextoAjustable(
            texto = speed.roundToInt().toString(),
            estilo = RevScopeType.numeros.copy(fontSize = 44.sp),
            color = TextColor,
            minimo = 20.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(label, color = TextMutedColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DeltaCard(deltaAbs: Double, deltaPercent: Double?) {
    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Difference", color = TextMutedColor, fontSize = 12.sp)
            Text(
                "%+.1f km/h".format(deltaAbs),
                color = TextColor,
                style = RevScopeType.numeros.copy(fontSize = 22.sp),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                deltaPercent?.let { "%+.1f%%".format(it) } ?: "-- (increase speed above 10 km/h)",
                color = if ((deltaPercent ?: 0.0) > 0) WarnColor else TextMutedColor,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun AverageCard(averageDeltaPercent: Double?, onReset: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Average for this session", color = TextMutedColor, fontSize = 12.sp)
            Text(
                averageDeltaPercent?.let { "%+.1f%%".format(it) } ?: "--",
                color = AccentColor,
                style = RevScopeType.numeros.copy(fontSize = 26.sp),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                Text("Reset average")
            }
        }
    }
}
