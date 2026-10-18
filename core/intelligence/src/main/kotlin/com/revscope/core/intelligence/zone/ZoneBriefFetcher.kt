package com.revscope.core.intelligence.zone

import com.revscope.core.intelligence.provider.AiProvider
import com.revscope.core.intelligence.provider.AiRequest
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MAX_TOKENS = 320
private const val NO_INFO_MARKER = "NADA"

/**
 * Brief de conducción de zona por IA (con web_search): al llegar a un lugar nuevo —
 * en el país o en el extranjero — busca precio de combustible, peajes, restricciones
 * de circulación y, si estás fuera de tu país, qué necesita un conductor extranjero.
 * Reutiliza la misma abstracción [AiProvider] que el resto de features de IA.
 *
 * @param providerProvider lambda que devuelve el proveedor activo — :core:intelligence
 *                         no ve :core:data (mismo motivo que DtcExplainer/LocalInfoFetcher).
 */
class ZoneBriefFetcher(private val providerProvider: suspend () -> AiProvider?) {

    /** Cuerpo del brief (viñetas) o null ante nada útil / sin web search / error. */
    suspend fun fetch(municipio: String, departamento: String?, pais: String?, homeCountry: String): String? {
        val provider = providerProvider() ?: return null
        if (!provider.supportsWebSearch) return null
        return callProvider(provider, municipio, departamento, pais, homeCountry)
    }

    private suspend fun callProvider(
        provider: AiProvider,
        municipio: String,
        departamento: String?,
        pais: String?,
        homeCountry: String,
    ): String? = try {
        provider.complete(
            AiRequest(
                user = buildPrompt(municipio, departamento, pais, homeCountry),
                maxTokens = MAX_TOKENS,
                needsWebSearch = true,
            )
        ).fold(
            onSuccess = { parseBrief(it) },
            onFailure = { e -> Timber.w(e, "ZoneBriefFetcher: fallo API para $municipio"); null },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "ZoneBriefFetcher: fallo API para $municipio")
        null
    }

    private fun buildPrompt(municipio: String, departamento: String?, pais: String?, homeCountry: String): String {
        val lugar = listOfNotNull(municipio, departamento, pais).joinToString(", ")
        val paisActual = pais?.trim().orEmpty()
        val extranjero = paisActual.isNotBlank() && !paisActual.equals(homeCountry, ignoreCase = true)
        val docLine = if (extranjero) {
            "- 📄 Qué necesita un conductor extranjero para manejar aquí (documentos, lado de la vía, normas o límites notables)."
        } else {
            "- 📄 Cualquier requisito o norma de tránsito notable de esta zona."
        }
        val header = if (extranjero) {
            "Soy de $homeCountry y acabo de llegar conduciendo a $lugar (soy conductor extranjero aquí)."
        } else {
            "Acabo de llegar conduciendo a $lugar."
        }
        return buildString {
            append("Today is ${today()}. $header ")
            appendLine("Give me a SHORT, practical driving brief for this area in British English, as one-line bullets. Include ONLY current, relevant information you can find:")
            appendLine("- Current local fuel prices, with the local currency.")
            appendLine("- Tolls on the area's main roads, with approximate prices if available.")
            appendLine("- Driving restrictions in force today (including pico y placa in Colombia, low-emission zones, and similar local schemes).")
            appendLine(docLine)
            append("Maximum 5 bullets, each no longer than 15 words, with no introduction. If you find nothing useful and current, reply exactly: $NO_INFO_MARKER")
        }
    }

    private fun today(): String =
        LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK))

    private fun parseBrief(rawText: String): String? {
        val text = rawText.trim()
        if (text.isEmpty()) return null
        val normalized = text.uppercase().replace(Regex("[.,!?;:]"), "").trim()
        if (normalized == NO_INFO_MARKER || normalized.startsWith("$NO_INFO_MARKER ")) return null
        return text
    }
}
