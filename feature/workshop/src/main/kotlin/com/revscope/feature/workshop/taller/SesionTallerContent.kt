package com.revscope.feature.workshop.taller

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.designsystem.BarraConVolver
import com.revscope.core.designsystem.EmptyState
import com.revscope.core.designsystem.FilaEtiquetaValor
import com.revscope.core.designsystem.NivelBadge
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.conCifrasTabulares
import com.revscope.core.obd.taller.sesion.FilaComparacion
import com.revscope.core.obd.taller.sesion.Veredicto

data class AccionesSesion(
    val onVolver: () -> Unit = {},
    val onMenu: (Boolean) -> Unit = {},
    val onGenerarInforme: (() -> Unit)? = null,
    val onPedirCierre: () -> Unit = {},
    val onPedirEliminacion: () -> Unit = {},
    val onAlternarComparacion: () -> Unit = {},
    val onAlternarEvento: (Long) -> Unit = {},
    val onHojaAgregar: (Boolean) -> Unit = {},
    val onLeerCodigos: () -> Unit = {},
    val onCaptura: () -> Unit = {},
    val onChequeo: () -> Unit = {},
    val onInstantanea: () -> Unit = {},
    val onPedirNota: () -> Unit = {},
    val onPruebaGuiada: (() -> Unit)? = null,
    val onMultimetro: (() -> Unit)? = null,
    val onCambiarNota: (String) -> Unit = {},
    val onGuardarNota: () -> Unit = {},
    val onConfirmarCierre: () -> Unit = {},
    val onConfirmarEliminacion: () -> Unit = {},
    val onCancelarDialogo: () -> Unit = {},
)

@Composable
fun SesionTallerContent(estado: SesionTallerEstado, acciones: AccionesSesion) {
    if (estado.cargando || estado.eliminada) {
        CargandoSesion(acciones.onVolver)
        return
    }
    Column(Modifier.fillMaxSize().background(RevScopeColors.Background)) {
        BarraConVolver(titulo = estado.titulo, subtitulo = estado.subtitulo, onVolver = acciones.onVolver) {
            MenuSesion(estado, acciones)
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            item(key = "resumen") { TarjetaResumen(estado) }
            item(key = "comparacion") { TarjetaComparar(estado, acciones) }
            item(key = "sugeridas") { TarjetaSugeridas(estado.sugeridas) }
            lineaDeTiempo(estado, acciones.onAlternarEvento)
        }
        BarraInferiorSesion(estado.agregar) { acciones.onHojaAgregar(true) }
    }
    if (estado.ui.hojaAgregar) HojaAgregar(estado.adaptadorConectado, acciones)
    DialogosSesion(estado, acciones)
}

@Composable
private fun CargandoSesion(onVolver: () -> Unit) {
    Column(Modifier.fillMaxSize().background(RevScopeColors.Background)) {
        BarraConVolver(titulo = "Workshop session", onVolver = onVolver)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = RevScopeColors.Accent)
        }
    }
}

@Composable
private fun MenuSesion(estado: SesionTallerEstado, acciones: AccionesSesion) {
    Box {
        IconButton(onClick = { acciones.onMenu(true) }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More session options", tint = RevScopeColors.TextPrimary)
        }
        DropdownMenu(expanded = estado.ui.menu, onDismissRequest = { acciones.onMenu(false) }) {
            acciones.onGenerarInforme?.let { OpcionMenu("Generate report", it) }
            if (estado.abierta) OpcionMenu("Close session", acciones.onPedirCierre)
            OpcionMenu("Delete session", acciones.onPedirEliminacion, peligrosa = true)
        }
    }
}

@Composable
private fun OpcionMenu(texto: String, onClick: () -> Unit, peligrosa: Boolean = false) {
    DropdownMenuItem(
        text = { Text(texto, color = if (peligrosa) RevScopeColors.Danger else RevScopeColors.TextPrimary, style = RevScopeType.body) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaResumen(estado: SesionTallerEstado) {
    TarjetaTaller {
        EstadoSesionFila(estado.abierta, estado.estadoTexto)
        if (estado.sintomas.isNotEmpty()) {
            TituloBloque("Symptoms")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                estado.sintomas.forEach { PastillaTexto(it) }
            }
        }
        if (estado.sintomasTexto.isNotBlank()) {
            Text("«${estado.sintomasTexto}»", color = RevScopeColors.TextPrimary, style = RevScopeType.body)
        }
        if (estado.notas.isNotBlank()) {
            TituloBloque("Notes")
            Text(estado.notas, color = RevScopeColors.TextPrimary, style = RevScopeType.body)
        }
        estado.odometro?.let { FilaEtiquetaValor("Odometer when opened", it) }
        TituloBloque("Current fault codes")
        if (estado.codigos.isEmpty()) {
            Text("No fault codes have been read in this session.", color = RevScopeColors.TextSecondary, style = RevScopeType.body)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                estado.codigos.forEach { PastillaCodigo(it) }
            }
        }
    }
}

@Composable
private fun EstadoSesionFila(abierta: Boolean, texto: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (abierta) Icons.Filled.FiberManualRecord else Icons.Filled.Lock,
            contentDescription = null,
            tint = if (abierta) RevScopeColors.Success else RevScopeColors.TextSecondary,
            modifier = Modifier.size(with(LocalDensity.current) { 16.sp.toDp() }),
        )
        Text(texto, color = RevScopeColors.TextPrimary, style = RevScopeType.label.conCifrasTabulares())
    }
}

@Composable
internal fun TituloBloque(texto: String) {
    Text(texto, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun TarjetaComparar(estado: SesionTallerEstado, acciones: AccionesSesion) {
    val comparacion = estado.comparacion
    TarjetaTaller {
        when (comparacion) {
            TarjetaComparacion.SinBase -> SinBase()
            is TarjetaComparacion.SinChequeoAhora -> SinChequeoAhora(comparacion.fechaBase, estado, acciones.onChequeo)
            is TarjetaComparacion.Filas -> FilasComparacion(comparacion, estado.ui.comparacionExpandida, acciones.onAlternarComparacion)
        }
    }
}

@Composable
private fun SinBase() {
    Text("Comparison", color = RevScopeColors.TextPrimary, style = RevScopeType.title)
    Text(
        "This session has no previous health check to use as a baseline, so there is nothing to compare it with.",
        color = RevScopeColors.TextSecondary,
        style = RevScopeType.body,
    )
}

@Composable
private fun SinChequeoAhora(fechaBase: String, estado: SesionTallerEstado, onChequeo: () -> Unit) {
    Text("Comparison with the health check from $fechaBase", color = RevScopeColors.TextPrimary, style = RevScopeType.title)
    Text(
        "Run a health check in this session to see what has changed since then.",
        color = RevScopeColors.TextSecondary,
        style = RevScopeType.body,
    )
    if (estado.agregar == DisponibilidadAgregar.Disponible) {
        OutlinedButton(onClick = onChequeo, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Run health check now", color = RevScopeColors.TextPrimary, style = RevScopeType.label)
        }
    }
}

@Composable
private fun FilasComparacion(comparacion: TarjetaComparacion.Filas, expandida: Boolean, onAlternar: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = if (expandida) "Hide comparison" else "Show comparison", onClick = onAlternar)
            .semantics { stateDescription = if (expandida) "Expanded" else "Collapsed" },
    ) {
        Text(
            "Comparison with the health check from ${comparacion.fechaBase}",
            color = RevScopeColors.TextPrimary,
            style = RevScopeType.title,
            modifier = Modifier.weight(1f),
        )
        Icon(if (expandida) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = RevScopeColors.TextPrimary)
    }
    if (!expandida) return
    comparacion.filas.forEach { FilaComparada(it) }
    Text(
        "A baseline is a single check: differences warrant attention but do not confirm a fault.",
        color = RevScopeColors.TextSecondary,
        style = RevScopeType.bodySmall,
    )
}

@Composable
private fun FilaComparada(fila: FilaComparacion) {
    Surface(color = RevScopeColors.SurfaceHigh, shape = RoundedCornerShape(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(fila.metrica.etiqueta, color = RevScopeColors.TextPrimary, style = RevScopeType.label, modifier = Modifier.weight(1f, fill = false))
                if (fila.veredicto != Veredicto.INFO) NivelBadge(fila.veredicto.nivel(), texto = fila.veredicto.texto())
            }
            Text(
                "Baseline: ${fila.base} · Now: ${fila.ahora}",
                color = RevScopeColors.TextPrimary,
                style = RevScopeType.body.conCifrasTabulares(),
            )
            Text(fila.cambio, color = RevScopeColors.TextSecondary, style = RevScopeType.body)
            fila.referencia?.let { Text("Threshold $it", color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall) }
        }
    }
}

@Composable
private fun TarjetaSugeridas(sugeridas: List<PruebaSugeridaUi>) {
    TarjetaTaller {
        Text("Suggested tests", color = RevScopeColors.TextPrimary, style = RevScopeType.title)
        if (sugeridas.isEmpty()) {
            Text(
                "No suggestions yet. Read the fault codes or add symptoms.",
                color = RevScopeColors.TextSecondary,
                style = RevScopeType.body,
            )
            return@TarjetaTaller
        }
        sugeridas.forEach { sugerida ->
            Column(Modifier.fillMaxWidth()) {
                Text(sugerida.titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.label)
                Text(sugerida.motivo, color = RevScopeColors.TextSecondary, style = RevScopeType.body)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = RevScopeColors.TextSecondary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
            Text(
                "These are suggestions based on fault codes and symptoms, not a diagnosis.",
                color = RevScopeColors.TextSecondary,
                style = RevScopeType.bodySmall,
            )
        }
    }
}

private fun LazyListScope.lineaDeTiempo(estado: SesionTallerEstado, onAlternar: (Long) -> Unit) {
    item(key = "linea-titulo") {
        EncabezadoSeccion("Timeline · ${if (estado.eventos.size == 1) "1 event" else "${estado.eventos.size} events"}")
    }
    if (estado.eventos.isEmpty()) {
        item(key = "linea-vacia") {
            EmptyState("Nothing has been logged yet. Read the fault codes, run a check or add a note to get started.")
        }
        return
    }
    itemsIndexed(estado.eventos, key = { _, evento -> "evento-${evento.id}" }) { indice, evento ->
        EventoLinea(
            evento = evento,
            ultimo = indice == estado.eventos.lastIndex,
            abierto = evento.id in estado.ui.eventosAbiertos,
            onAlternar = { onAlternar(evento.id) },
        )
    }
}

@Composable
private fun BarraInferiorSesion(agregar: DisponibilidadAgregar, onAgregar: () -> Unit) {
    Surface(color = RevScopeColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding().padding(16.dp)) {
            when (agregar) {
                DisponibilidadAgregar.Disponible -> BotonPrincipalTaller("Add to session", onClick = onAgregar)
                DisponibilidadAgregar.SesionCerrada -> AvisoBarra("Session closed: it can be viewed but no longer accepts readings.")
                is DisponibilidadAgregar.OtroVehiculo -> AvisoBarra(agregar.mensaje)
            }
        }
    }
}

@Composable
private fun AvisoBarra(texto: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = RevScopeColors.TextSecondary, modifier = Modifier.size(20.dp))
        Text(texto, color = RevScopeColors.TextPrimary, style = RevScopeType.body)
    }
}
