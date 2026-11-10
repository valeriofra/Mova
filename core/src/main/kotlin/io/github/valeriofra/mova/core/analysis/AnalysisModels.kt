package io.github.valeriofra.mova.core.analysis

import io.github.valeriofra.mova.core.link.RecognizedLink
import io.github.valeriofra.mova.core.model.MediaKind
import io.github.valeriofra.mova.core.model.Platform

/** Um item que pode ser baixado. */
data class MediaItem(
    /** URL efetiva do arquivo (após redirecionamentos). */
    val url: String,
    /** Link original informado pelo usuário. */
    val sourceUrl: String,
    val platform: Platform,
    val fileName: String,
    val title: String,
    val mimeType: String?,
    val mediaKind: MediaKind,
    val sizeBytes: Long?,
    val supportsResume: Boolean,
    val etag: String?,
    val lastModified: String?,
)

/** Resultado da análise de um único link. */
sealed interface LinkAnalysis {
    val input: String

    /** Conteúdo disponível para download. */
    data class Ready(
        override val input: String,
        val link: RecognizedLink,
        val items: List<MediaItem>,
    ) : LinkAnalysis

    /** Plataforma reconhecida, mas sem forma autorizada de obter o conteúdo. */
    data class RecognizedWithoutDownload(
        override val input: String,
        val link: RecognizedLink,
    ) : LinkAnalysis

    /** O link aponta para uma página comum, não para um arquivo. */
    data class UnrecognizedPage(
        override val input: String,
        val link: RecognizedLink,
    ) : LinkAnalysis

    /** Formato de transmissão (HLS/DASH) ainda não suportado. */
    data class UnsupportedFormat(
        override val input: String,
        val link: RecognizedLink,
        val mimeType: String?,
    ) : LinkAnalysis

    /** Conteúdo inexistente ou removido (404/410). */
    data class Unavailable(
        override val input: String,
        val link: RecognizedLink,
        val httpCode: Int,
    ) : LinkAnalysis

    /** A origem recusou o acesso (401/403/451). O MOVA não contorna isso. */
    data class AccessDenied(
        override val input: String,
        val link: RecognizedLink,
        val httpCode: Int,
    ) : LinkAnalysis

    /** Falha de rede ou do servidor; pode funcionar mais tarde. */
    data class TemporaryError(
        override val input: String,
        val link: RecognizedLink,
        val httpCode: Int?,
        val message: String?,
    ) : LinkAnalysis
}

/** Resultado da análise de tudo o que foi colado/compartilhado. */
data class AnalysisReport(
    val input: String,
    val results: List<LinkAnalysis>,
) {
    /** Nenhum link válido encontrado no texto. */
    val isEmpty: Boolean get() = results.isEmpty()

    val downloadableItems: List<MediaItem>
        get() = results.filterIsInstance<LinkAnalysis.Ready>().flatMap { it.items }

    val hasMultipleLinks: Boolean get() = results.size > 1
}
