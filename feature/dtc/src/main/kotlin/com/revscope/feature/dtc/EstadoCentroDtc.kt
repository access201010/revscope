package com.revscope.feature.dtc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.revscope.core.designsystem.FilaEtiquetaValor
import com.revscope.core.designsystem.NivelBadge
import com.revscope.core.designsystem.NivelEstado
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType

@Composable
internal fun TarjetaMil(mil: MilUi) {
    val color = if (mil.encendida) RevScopeColors.Danger else RevScopeColors.Success
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.SurfaceHigh, RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Icon(
            if (mil.encendida) Icons.Filled.Warning else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp),
        )
        Text(mil.texto, color = RevScopeColors.TextPrimary, style = RevScopeType.label, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun FranjaSesion(detalle: DetalleDtc, onGuardar: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.Surface, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        when (val sesion = detalle.sesion) {
            is SesionDtcUi.Abierta -> LineaIcono(
                Icons.Filled.FolderOpen,
                textoSesionAbierta(sesion.titulo, detalle.lecturaEnSesion),
            )
            SesionDtcUi.Ninguna -> SinSesion(detalle.guardandoEnSesion, onGuardar)
        }
    }
}

private fun textoSesionAbierta(titulo: String, lecturaEnSesion: Boolean): String =
    if (lecturaEnSesion) "Reading logged in session “$titulo”, including the freeze frame and guide checkboxes."
    else "Session “$titulo” is open: guide checkboxes will be saved to it."

@Composable
private fun SinSesion(guardando: Boolean, onGuardar: () -> Unit) {
    LineaIcono(
        Icons.Outlined.Info,
        "No diagnostic session is open: this reading and its checkboxes will not be saved.",
    )
    OutlinedButton(
        onClick = onGuardar,
        enabled = !guardando,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(
            if (guardando) "Saving…" else "Save this reading to a new session",
            color = RevScopeColors.TextPrimary,
            style = RevScopeType.label,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LineaIcono(icono: ImageVector, texto: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(icono, contentDescription = null, tint = RevScopeColors.TextSecondary, modifier = Modifier.size(18.dp))
        Text(texto, color = RevScopeColors.TextSecondary, style = RevScopeType.body, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun FreezeFrameCard(freezeFrame: FreezeFrameUi, enSesion: Boolean) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.SurfaceHigh, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = RevScopeColors.Accent, modifier = Modifier.size(18.dp))
            Text(
                tituloFreezeFrame(freezeFrame.causante),
                color = RevScopeColors.TextPrimary,
                style = RevScopeType.label,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Engine data from when the ECU stored the fault.",
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.bodySmall,
        )
        freezeFrame.items.forEach { FilaEtiquetaValor(it.label, it.value) }
        Text(
            if (enSesion) "Saved to the session: it will be kept if you clear the codes."
            else "This will be lost when codes are cleared unless you save it to a session.",
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.bodySmall,
        )
    }
}

fun tituloFreezeFrame(causante: String?): String =
    causante?.let { "Freeze frame · triggered by $it" } ?: "Freeze frame"

@Composable
internal fun MotivoBorradoDeshabilitado(motivo: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = RevScopeColors.Warning, modifier = Modifier.size(18.dp))
        Text(
            "Code clearing disabled: $motivo",
            color = RevScopeColors.TextPrimary,
            style = RevScopeType.bodySmall,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
internal fun LoadingContent(label: String) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator(color = RevScopeColors.Accent)
        Text(
            label,
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
internal fun StatusContent(message: String, nivel: NivelEstado) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.Surface, RoundedCornerShape(12.dp))
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        NivelBadge(nivel)
        Text(message, color = RevScopeColors.TextPrimary, style = RevScopeType.body)
    }
}
