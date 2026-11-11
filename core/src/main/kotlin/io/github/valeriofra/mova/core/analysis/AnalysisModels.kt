package io.github.valeriofra.mova.core.analysis

import io.github.valeriofra.mova.core.link.RecognizedLink
import io.github.valeriofra.mova.core.model.MediaKind
import io.github.valeriofra.mova.core.model.MediaSource
import io.github.valeriofra.mova.core.model.Platform

/** Um item que pode ser baixado. */
data class MediaItem(
    /** URL efetiva: arquivo (link direto) ou página do conteúdo (extração). */
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
    val source: MediaSource = MediaSource.DIRECT,
    val durationSeconds: Long? = null,
    val thumbnailUrl: String? = null,
    val uploader: String? = null,
    /** Resoluções de vídeo disponíveis (menor lado: 1080, 720...). Vazio = desconhecido. */
    val resolutions: List<Int> = emptyList(),
    val hasVideo: Boolean = mediaKind == MediaKind.VIDEO,
    /** Tamanho estimado por resolução (vídeo + áudio). */
    val sizeByResolution: Map<Int, Long> = emptyMap(),
    val audioSizeBytes: Long? = null,
    /** Posição dentro de uma playlist/publicação, quando o item só é acessível por ela. */
    val playlistIndex: Int? = null,
) {
    /** Identificador único na tela de seleção. */
    val key: String get() = if (playlistIndex != null) "$url#$playlistIndex" else url
}

/** Motivo pelo qual um conteúdo existe, mas não pode ser obtido. */
enum class RestrictionReason { PRIVATE, LOGIN_REQUIRED, DRM, GEO_BLOCKED }

/** Resultado da análise de um único link. */
sealed interface LinkAnalysis {
    val input: String

    /** Conteúdo disponível para download (um item ou uma coleção). */
    data class Ready(
        override val input: String,
        val link: RecognizedLink,
        val items: List<MediaItem>,
        /** Título da playlist/coleção, quando o link reúne vários itens. */
        val collectionTitle: String? = null,
    ) : LinkAnalysis

    /** Plataforma reconhecida, mas sem mecanismo de download disponível. */
    data class RecognizedWithoutDownload(
        override val input: String,
        val link: RecognizedLink,
    ) : LinkAnalysis

    /** O mecanismo ainda não sabe obter conteúdo deste link. */
    data class NotSupported(
        override val input: String,
        val link: RecognizedLink,
    ) : LinkAnalysis

    /** O link aponta para uma página comum, sem mídia reconhecível. */
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

    /** Conteúdo inexistente ou removido. */
    data class Unavailable(
        override val input: String,
        val link: RecognizedLink,
        val httpCode: Int?,
    ) : LinkAnalysis

    /** A origem recusou o acesso (401/403/451). O MOVA não contorna isso. */
    data class AccessDenied(
        override val input: String,
        val link: RecognizedLink,
        val httpCode: Int,
    ) : LinkAnalysis

    /** Conteúdo privado, que exige login, protegido por DRM ou bloqueado na região. */
    data class Restricted(
        override val input: String,
        val link: RecognizedLink,
        val reason: RestrictionReason,
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
