package com.revscope.feature.workshop.taller.modelo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.OrigenBanda
import com.revscope.core.obd.taller.sesion.TallerRepository
import com.revscope.core.obd.taller.sesion.VehiculoTaller
import com.revscope.feature.workshop.taller.EntornoTaller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ReferenciasViewModel @Inject constructor(
    private val repositorio: TallerRepository,
    entorno: EntornoTaller,
) : ViewModel() {

    private val local = MutableStateFlow(ReferenciasUi())
    val estado: StateFlow<ReferenciasUi> = local.asStateFlow()
    private var vehiculoActual: VehiculoTaller? = null
    private var versionCarga = 0L

    init {
        viewModelScope.launch {
            entorno.vehiculo.collectLatest { vehiculo ->
                vehiculoActual = vehiculo
                cargar(vehiculo)
            }
        }
    }

    fun reintentar() = viewModelScope.launch { cargar(vehiculoActual) }

    fun editar(clave: String) {
        val banda = local.value.bandas.firstOrNull { it.clave == clave } ?: return
        local.update {
            it.copy(dialogo = EdicionBandaUi(banda, banda.min.formatoEditable(), banda.max.formatoEditable()))
        }
    }

    fun cambiarMinimo(texto: String) = cambiarDialogo { copy(minimo = texto.take(MAX_VALOR), error = null) }

    fun cambiarMaximo(texto: String) = cambiarDialogo { copy(maximo = texto.take(MAX_VALOR), error = null) }

    fun cerrarDialogo() = local.update { it.copy(dialogo = null) }

    fun guardar() {
        val claveModelo = vehiculoActual?.claveModelo ?: return avisar("Select a reference model in Profiles")
        val dialogo = local.value.dialogo ?: return
        val limites = limites(dialogo) ?: return
        val banda = dialogo.banda.copy(min = limites.first, max = limites.second, origen = OrigenBanda.USUARIO, fuente = "")
        viewModelScope.launch {
            ejecutarGuardado("Reference value saved") { repositorio.guardarBandaUsuario(claveModelo, banda) }
        }
    }

    fun restablecer(clave: String) {
        val claveModelo = vehiculoActual?.claveModelo ?: return avisar("Select a reference model in Profiles")
        viewModelScope.launch {
            ejecutarGuardado("Reference value reset to typical") {
                check(repositorio.restablecerBanda(claveModelo, clave)) { "Reference value does not exist" }
            }
        }
    }

    fun mensajeMostrado() = local.update { it.copy(mensaje = null) }

    private suspend fun cargar(vehiculo: VehiculoTaller?, conservarMensaje: Boolean = false) {
        val version = ++versionCarga
        val mensaje = local.value.mensaje.takeIf { conservarMensaje }
        local.value = ReferenciasUi(vehiculo = vehiculo?.nombre, cargando = true, mensaje = mensaje)
        if (vehiculo == null) {
            local.value = local.value.copy(cargando = false)
            return
        }
        try {
            val conocimiento = vehiculo.claveModelo?.let { repositorio.conocimiento(it) }
            val bandas = repositorio.bandasResueltas(vehiculo.claveModelo, vehiculo.tipo).values.sortedBy { it.clave }
            if (version != versionCarga) return
            local.value = local.value.copy(modelo = conocimiento?.nombre, bandas = bandas, cargando = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (version != versionCarga) return
            Timber.w(e, "Referencias: no se pudieron cargar")
            local.value = local.value.copy(cargando = false, error = "Could not load reference values")
        }
    }

    private suspend fun ejecutarGuardado(mensaje: String, accion: suspend () -> Unit) {
        try {
            accion()
            local.update { it.copy(dialogo = null, mensaje = mensaje) }
            cargar(vehiculoActual, conservarMensaje = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Referencias: no se pudo guardar")
            avisar("Could not save reference value")
        }
    }

    private fun limites(dialogo: EdicionBandaUi): Pair<Double?, Double?>? {
        val min = dialogo.minimo.decimalONull()
        val max = dialogo.maximo.decimalONull()
        val error = when {
            dialogo.minimo.isNotBlank() && min == null -> "Minimum must be a valid number"
            dialogo.maximo.isNotBlank() && max == null -> "Maximum must be a valid number"
            min == null && max == null -> "Enter at least one limit"
            min != null && max != null && min > max -> "Minimum cannot be greater than maximum"
            else -> null
        }
        if (error == null) return min to max
        cambiarDialogo { copy(error = error) }
        return null
    }

    private fun cambiarDialogo(cambio: EdicionBandaUi.() -> EdicionBandaUi) =
        local.update { estado -> estado.copy(dialogo = estado.dialogo?.cambio()) }

    private fun avisar(mensaje: String) = local.update { it.copy(mensaje = mensaje) }

    private fun String.decimalONull(): Double? =
        trim().takeIf(String::isNotEmpty)?.replace(',', '.')?.toDoubleOrNull()?.takeIf(Double::isFinite)

    private fun Double?.formatoEditable(): String = this?.toString().orEmpty()

    companion object {
        private const val MAX_VALOR = 16
    }
}
