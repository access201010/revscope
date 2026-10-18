package com.revscope.feature.workshop.taller

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.designsystem.ConfirmarDestructivoDialog
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType

data class OpcionAgregar(
    val icono: ImageVector,
    val titulo: String,
    val descripcion: String,
    val requiereAdaptador: Boolean,
    val onElegir: () -> Unit,
)

internal object OpcionesAgregar {
    fun de(a: AccionesSesion): List<OpcionAgregar> = listOfNotNull(
        OpcionAgregar(
            Icons.Filled.BugReport,
            "Read fault codes",
            "Active, pending, permanent and freeze-frame codes",
            true,
            a.onLeerCodigos,
        ),
        a.onPruebaGuiada?.let { OpcionAgregar(Icons.Filled.Science, "Guided test", "Step by step, with a verdict", true, it) },
        a.onMultimetro?.let {
            OpcionAgregar(
                Icons.Filled.ElectricalServices,
                "Multimeter readings",
                "What you measure on each wire",
                false,
                it,
            )
        },
        OpcionAgregar(
            Icons.AutoMirrored.Filled.ShowChart,
            "Quick capture",
            "Up to 6 PIDs at the maximum rate, saved as CSV",
            true,
            a.onCaptura,
        ),
        OpcionAgregar(
            Icons.Filled.MonitorHeart,
            "Health check",
            "Compare with the baseline check",
            true,
            a.onChequeo,
        ),
        OpcionAgregar(
            Icons.Filled.Sensors,
            "Sensor snapshot",
            "A snapshot of the current readings",
            true,
            a.onInstantanea,
        ),
        OpcionAgregar(
            Icons.AutoMirrored.Filled.Notes,
            "Nota",
            "What you saw, measured or changed",
            false,
            a.onPedirNota,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HojaAgregar(conectado: Boolean, acciones: AccionesSesion) {
    ModalBottomSheet(
        onDismissRequest = { acciones.onHojaAgregar(false) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = RevScopeColors.SurfaceHigh,
    ) {
        HojaAgregarContent(OpcionesAgregar.de(acciones), conectado)
    }
}

@Composable
fun HojaAgregarContent(opciones: List<OpcionAgregar>, conectado: Boolean) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp),
    ) {
        Text(
            "Add to session",
            color = RevScopeColors.TextPrimary,
            style = RevScopeType.title,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        opciones.forEach { FilaOpcion(it, conectado) }
    }
}

@Composable
private fun FilaOpcion(opcion: OpcionAgregar, conectado: Boolean) {
    val habilitada = conectado || !opcion.requiereAdaptador
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = habilitada, onClick = opcion.onElegir)
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Icon(
            opcion.icono,
            contentDescription = null,
            tint = if (habilitada) RevScopeColors.TextPrimary else RevScopeColors.TextSecondary,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(opcion.titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.label)
            if (habilitada) {
                Text(opcion.descripcion, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
            } else {
                RequiereAdaptador()
            }
        }
    }
}

@Composable
private fun RequiereAdaptador() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.LinkOff,
            contentDescription = null,
            tint = RevScopeColors.Warning,
            modifier = Modifier.size(with(LocalDensity.current) { 14.sp.toDp() }),
        )
        Text("Requires adapter", color = RevScopeColors.Warning, style = RevScopeType.bodySmall)
    }
}

@Composable
internal fun DialogosSesion(estado: SesionTallerEstado, acciones: AccionesSesion) {
    when (estado.ui.dialogo) {
        DialogoSesion.CERRAR -> DialogoCerrar(acciones)
        DialogoSesion.ELIMINAR -> DialogoEliminar(estado, acciones)
        DialogoSesion.NOTA -> DialogoNota(estado.ui.nota, acciones)
        null -> Unit
    }
}

@Composable
private fun DialogoCerrar(acciones: AccionesSesion) {
    AlertDialog(
        onDismissRequest = acciones.onCancelarDialogo,
        containerColor = RevScopeColors.SurfaceHigh,
        title = { Text("Close this session?", color = RevScopeColors.TextPrimary, style = RevScopeType.title) },
        text = {
            Text(
                "New readings will no longer be added. The timeline will be kept and you can still view it.",
                color = RevScopeColors.TextPrimary,
                style = RevScopeType.body,
            )
        },
        confirmButton = { BotonDialogo("Close session", acciones.onConfirmarCierre, principal = true) },
        dismissButton = { BotonDialogo("Cancel", acciones.onCancelarDialogo) },
    )
}

@Composable
private fun DialogoEliminar(estado: SesionTallerEstado, acciones: AccionesSesion) {
    ConfirmarDestructivoDialog(
        titulo = "Delete this session?",
        textoConfirmar = "Delete session",
        onConfirmar = acciones.onConfirmarEliminacion,
        onCancelar = acciones.onCancelarDialogo,
        mensaje = "“${estado.titulo}” will be deleted from this phone and cannot be recovered.",
        avisos = AvisosEliminacion.de(estado.loQueSeBorra),
    )
}

internal object AvisosEliminacion {
    fun de(borra: LoQueSeBorra): List<String> = listOfNotNull(
        "Symptoms, notes, the comparison and ${eventos(borra.eventos)} from the timeline will be deleted.",
        borra.adjuntos.takeIf { it > 0 }?.let(::adjuntos),
        "Health checks and saved trips will be kept; ECU fault codes will be unaffected.",
    )

    private fun eventos(n: Int) = if (n == 1) "the event" else "$n events"

    private fun adjuntos(n: Int) =
        if (n == 1) "1 CSV capture file saved in the session will be deleted."
        else "$n CSV capture files saved in the session will be deleted."
}

@Composable
private fun DialogoNota(nota: String, acciones: AccionesSesion) {
    AlertDialog(
        onDismissRequest = acciones.onCancelarDialogo,
        containerColor = RevScopeColors.SurfaceHigh,
        title = { Text("Add a note", color = RevScopeColors.TextPrimary, style = RevScopeType.title) },
        text = {
            OutlinedTextField(
                value = nota,
                onValueChange = acciones.onCambiarNota,
                label = { Text("Nota", style = RevScopeType.body) },
                supportingText = { Text("What you saw, measured or changed.", style = RevScopeType.bodySmall) },
                minLines = 3,
                textStyle = RevScopeType.body,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = RevScopeColors.TextPrimary,
                    unfocusedTextColor = RevScopeColors.TextPrimary,
                    focusedBorderColor = RevScopeColors.Accent,
                    unfocusedBorderColor = RevScopeColors.TextMuted,
                    focusedLabelColor = RevScopeColors.Accent,
                    unfocusedLabelColor = RevScopeColors.TextSecondary,
                    cursorColor = RevScopeColors.Accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { BotonDialogo("Save note", acciones.onGuardarNota, principal = true, habilitado = nota.isNotBlank()) },
        dismissButton = { BotonDialogo("Cancel", acciones.onCancelarDialogo) },
    )
}

@Composable
private fun BotonDialogo(texto: String, onClick: () -> Unit, principal: Boolean = false, habilitado: Boolean = true) {
    TextButton(onClick = onClick, enabled = habilitado, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(
            texto,
            color = when {
                !habilitado -> RevScopeColors.TextSecondary
                principal -> RevScopeColors.Accent
                else -> RevScopeColors.TextPrimary
            },
            style = RevScopeType.label,
        )
    }
}
