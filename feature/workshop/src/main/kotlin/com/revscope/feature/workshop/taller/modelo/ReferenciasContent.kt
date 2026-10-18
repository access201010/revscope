package com.revscope.feature.workshop.taller.modelo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.revscope.core.designsystem.BarraConVolver
import com.revscope.core.designsystem.EmptyState
import com.revscope.core.designsystem.ErrorState
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.OrigenBanda
import com.revscope.feature.workshop.taller.TarjetaTaller

data class AccionesReferencias(
    val onVolver: () -> Unit = {},
    val onEditar: (String) -> Unit = {},
    val onRestablecer: (String) -> Unit = {},
    val onCambiarMinimo: (String) -> Unit = {},
    val onCambiarMaximo: (String) -> Unit = {},
    val onGuardar: () -> Unit = {},
    val onCerrarDialogo: () -> Unit = {},
    val onReintentar: () -> Unit = {},
)

@Composable
fun ReferenciasContent(estado: ReferenciasUi, acciones: AccionesReferencias = AccionesReferencias()) {
    Column(Modifier.fillMaxSize().background(RevScopeColors.Background)) {
        BarraConVolver("Reference values", acciones.onVolver, subtitulo = estado.modelo ?: estado.vehiculo)
        when {
            estado.cargando -> CargandoReferencias()
            estado.error != null -> ErrorReferencias(estado.error, acciones.onReintentar)
            estado.bandas.isEmpty() -> EmptyState("No reference values are available for this vehicle type.", Modifier.padding(16.dp))
            else -> ListaReferencias(estado, acciones)
        }
    }
    estado.dialogo?.let { DialogoBanda(it, acciones) }
}

@Composable
private fun CargandoReferencias() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = RevScopeColors.Accent)
    }
}

@Composable
private fun ErrorReferencias(mensaje: String, onReintentar: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        ErrorState(mensaje, onReintentar)
    }
}

@Composable
private fun ListaReferencias(estado: ReferenciasUi, acciones: AccionesReferencias) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text("Diagnostic ranges", color = RevScopeColors.TextPrimary, style = RevScopeType.title, modifier = Modifier.semantics { heading() })
        Text(
            "Typical values are general industry references, not manufacturer specifications. A range with a source " +
                "shows where it came from; your changes are labelled Edited by you.",
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.body,
        )
        if (estado.modelo == null) {
            Text(
                "Select a reference model in Profiles to save your own changes.",
                color = RevScopeColors.Warning,
                style = RevScopeType.body,
            )
        }
        estado.bandas.forEach { banda -> TarjetaBanda(banda, acciones) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaBanda(banda: BandaReferencia, acciones: AccionesReferencias) {
    TarjetaTaller {
        Text(nombreBanda(banda.clave), color = RevScopeColors.TextPrimary, style = RevScopeType.title)
        Text(valorBanda(banda), color = RevScopeColors.Accent, style = RevScopeType.numeros)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.BookmarkAdded, contentDescription = null, tint = colorOrigen(banda.origen), modifier = Modifier.size(18.dp))
            Text(origen(banda), color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall, modifier = Modifier.weight(1f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                onClick = { acciones.onEditar(banda.clave) },
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Edit ${nombreBanda(banda.clave)}" },
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = RevScopeColors.Accent, modifier = Modifier.size(18.dp))
                Text("Edit", color = RevScopeColors.Accent, style = RevScopeType.label, modifier = Modifier.padding(start = 6.dp))
            }
            if (banda.origen != OrigenBanda.TIPICO) {
                TextButton(
                    onClick = { acciones.onRestablecer(banda.clave) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        contentDescription = "Reset ${nombreBanda(banda.clave)} to typical"
                    },
                ) {
                    Icon(Icons.Filled.Restore, contentDescription = null, tint = RevScopeColors.TextSecondary, modifier = Modifier.size(18.dp))
                    Text("Reset to typical", color = RevScopeColors.TextSecondary, style = RevScopeType.label, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun DialogoBanda(dialogo: EdicionBandaUi, acciones: AccionesReferencias) {
    AlertDialog(
        onDismissRequest = acciones.onCerrarDialogo,
        containerColor = RevScopeColors.SurfaceHigh,
        title = { Text("Edit ${nombreBanda(dialogo.banda.clave)}", color = RevScopeColors.TextPrimary, style = RevScopeType.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CampoLimite("Minimum", dialogo.minimo, acciones.onCambiarMinimo)
                CampoLimite("Maximum", dialogo.maximo, acciones.onCambiarMaximo)
                Text("Unit: ${dialogo.banda.unidad}", color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
                dialogo.error?.let {
                    Text(it, color = RevScopeColors.Danger, style = RevScopeType.body, modifier = Modifier.semantics { contentDescription = "Error: $it" })
                }
                Text("Saving will change the source to “Edited by you”.", color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.onGuardar, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Save", color = RevScopeColors.Accent, style = RevScopeType.label)
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.onCerrarDialogo, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel", color = RevScopeColors.TextPrimary, style = RevScopeType.label)
            }
        },
    )
}

@Composable
private fun CampoLimite(etiqueta: String, valor: String, onValor: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValor,
        label = { Text(etiqueta) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        textStyle = RevScopeType.numeros,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = RevScopeColors.TextPrimary,
            unfocusedTextColor = RevScopeColors.TextPrimary,
            focusedBorderColor = RevScopeColors.Accent,
            unfocusedBorderColor = RevScopeColors.TextSecondary,
            focusedLabelColor = RevScopeColors.Accent,
            unfocusedLabelColor = RevScopeColors.TextSecondary,
            cursorColor = RevScopeColors.Accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

internal fun nombreBanda(clave: String): String = NOMBRES_BANDAS[clave] ?: clave.lowercase().replace('_', ' ')

internal fun valorBanda(banda: BandaReferencia): String {
    val minimo = banda.min
    val maximo = banda.max
    return when {
        minimo != null && maximo != null -> "${numero(minimo)}–${numero(maximo)} ${banda.unidad}"
        minimo != null -> "≥ ${numero(minimo)} ${banda.unidad}"
        else -> "≤ ${numero(maximo!!)} ${banda.unidad}"
    }
}

private fun numero(valor: Double): String =
    if (valor % 1.0 == 0.0) valor.toInt().toString() else valor.toString()

private fun origen(banda: BandaReferencia): String = when (banda.origen) {
    OrigenBanda.TIPICO -> "Typical"
    OrigenBanda.FUENTE -> "Source: ${banda.fuente}"
    OrigenBanda.USUARIO -> "Edited by you"
}

private fun colorOrigen(origen: OrigenBanda) = when (origen) {
    OrigenBanda.TIPICO -> RevScopeColors.TextSecondary
    OrigenBanda.FUENTE -> RevScopeColors.Success
    OrigenBanda.USUARIO -> RevScopeColors.Accent
}

private val NOMBRES_BANDAS = mapOf(
    ClavesBanda.TPS_CERRADO_V to "TPS, throttle closed",
    ClavesBanda.TPS_FONDO_V to "TPS, full throttle",
    ClavesBanda.TPS_RECORRIDO_MIN_V to "Minimum TPS range",
    ClavesBanda.TPS_RUIDO_MAX_V to "Maximum TPS noise",
    ClavesBanda.TPS_REPETIBILIDAD_V to "TPS repeatability",
    ClavesBanda.TPS_LINEALIDAD to "TPS linearity",
    ClavesBanda.REF_5V_V to "5 V reference",
    ClavesBanda.MASA_V to "Earth voltage drop",
    ClavesBanda.INYECTOR_OHM to "Injector resistance",
    ClavesBanda.MINIMO_RPM to "Idle speed",
    ClavesBanda.MINIMO_DERIVA_MAX to "Maximum idle drift",
    ClavesBanda.MINIMO_DESVIACION_MAX to "Maximum idle variation",
    ClavesBanda.RETORNO_VALLE_MIN to "Minimum return dip",
    ClavesBanda.MOTOR_FRIO_ECT_IAT_DELTA_C to "Cold ECT–IAT difference",
    ClavesBanda.BATERIA_CONTACTO_V to "Battery, ignition on",
    ClavesBanda.BATERIA_CONTACTO_CON_LUCES_V to "Battery, ignition and lights on",
    ClavesBanda.ARRANQUE_MIN_V to "Minimum cranking voltage",
    ClavesBanda.CARGA_V to "Charging voltage",
    ClavesBanda.SOBRECARGA_MAX_V to "Maximum overcharge voltage",
    ClavesBanda.MAP_KOEO_VS_BARO_KPA to "MAP vs barometric pressure, engine off",
    ClavesBanda.CHEQUEO_AJUSTE_DELTA_PP to "Fuel-trim change",
    ClavesBanda.CHEQUEO_VOLTAJE_DELTA_V to "Voltage change",
)
