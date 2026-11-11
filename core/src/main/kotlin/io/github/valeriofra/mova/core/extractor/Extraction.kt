package io.github.valeriofra.mova.core.extractor

import java.io.IOException

/** Um vídeo (ou áudio) encontrado pelo mecanismo de extração. */
data class ExtractedMedia(
    val id: String?,
    val title: String,
    val webpageUrl: String?,
    val extractor: String?,
    val uploader: String?,
    val durationSeconds: Long?,
    val thumbnailUrl: String?,
    /** Resoluções disponíveis (menor lado do vídeo), da maior para a menor. */
    val resolutions: List<Int>,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    /** Estimativa de tamanho (vídeo + melhor áudio) por resolução. */
    val sizeByResolution: Map<Int, Long>,
    val audioSizeBytes: Long?,
)

/** Um item de playlist, coleção ou publicação com vários itens. */
data class ExtractedEntry(
    /** URL própria do item, quando a plataforma informa. */
    val url: String?,
    val title: String?,
    val durationSeconds: Long?,
    val thumbnailUrl: String?,
    /** Posição na coleção (1, 2, 3...). */
    val index: Int,
)

/** Motivos de falha reportados pelo mecanismo. */
enum class ExtractionFailure {
    UNSUPPORTED,
    PRIVATE,
    LOGIN_REQUIRED,
    DRM,
    GEO_BLOCKED,
    UNAVAILABLE,
    RATE_LIMITED,
    NETWORK,
    UNKNOWN,
}

sealed interface ExtractionResult {
    data class Single(val media: ExtractedMedia) : ExtractionResult

    data class Collection(
        val title: String?,
        val webpageUrl: String?,
        val uploader: String?,
        val entries: List<ExtractedEntry>,
    ) : ExtractionResult

    data class Failure(val reason: ExtractionFailure, val message: String?) : ExtractionResult
}

/**
 * Mecanismo que obtém informações de páginas de plataformas (no Android,
 * o yt-dlp executado no aparelho). Mantido como interface para que a
 * versão Windows possa usar o mesmo contrato.
 */
fun interface MediaExtractor {
    @Throws(IOException::class)
    suspend fun extract(url: String, allowPlaylist: Boolean): ExtractionResult
}
