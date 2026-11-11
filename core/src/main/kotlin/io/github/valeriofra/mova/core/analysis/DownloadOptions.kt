package io.github.valeriofra.mova.core.analysis

import io.github.valeriofra.mova.core.file.MimeTypes
import io.github.valeriofra.mova.core.model.MediaSource

/** O que baixar quando a análise encontra um ou vários itens. */
sealed interface DownloadOption {
    val key: String

    /** Um único item. */
    data class SingleFile(val item: MediaItem) : DownloadOption {
        override val key: String = "single:${item.key}"

        /** Ex.: "MP4" (links diretos). */
        val formatLabel: String?
            get() = (MimeTypes.extensionOf(item.fileName) ?: item.mimeType?.let(MimeTypes::extensionForMime))
                ?.uppercase()
    }

    /** Todos os itens encontrados. */
    data class AllItems(val items: List<MediaItem>) : DownloadOption {
        override val key: String = "all"
        val totalBytes: Long? = items.map { it.sizeBytes }.takeIf { sizes -> sizes.all { it != null } }
            ?.sumOf { it ?: 0L }
    }

    /** Escolher manualmente quais itens baixar. */
    data class SelectItems(val items: List<MediaItem>) : DownloadOption {
        override val key: String = "select"
    }
}

/**
 * Monta somente as opções aplicáveis ao que foi encontrado. Nada é oferecido
 * por suposição: sem itens, não há opções.
 */
object DownloadOptions {

    fun forItems(items: List<MediaItem>): List<DownloadOption> = when {
        items.isEmpty() -> emptyList()
        items.size == 1 -> listOf(DownloadOption.SingleFile(items.single()))
        else -> listOf(DownloadOption.AllItems(items), DownloadOption.SelectItems(items))
    }

    /** Formatos possíveis para os itens (vídeo em cada resolução, áudio, ou original). */
    fun formatsFor(items: List<MediaItem>): List<FormatChoice> {
        if (items.isEmpty()) return emptyList()
        if (items.any { it.source != MediaSource.EXTRACTOR }) return listOf(FormatChoice.Original)
        val anyVideo = items.any { it.hasVideo }
        val video = if (anyVideo) videoResolutions(items).map { FormatChoice.Video(it) } else emptyList()
        return video + AudioFormat.entries.map { FormatChoice.Audio(it) }
    }

    /** Resoluções até 1080p, da maior para a menor. */
    fun videoResolutions(items: List<MediaItem>): List<Int> {
        val known = if (items.size == 1) items.single().resolutions else emptyList()
        val capped = known.filter { it in 144..FormatChoice.MAX_RESOLUTION }.distinct().sortedDescending()
        return capped.ifEmpty { FormatChoice.STANDARD_RESOLUTIONS }
    }

    /** Escolha padrão: vídeo na melhor qualidade até 1080p; áudio se não houver vídeo. */
    fun defaultFormat(items: List<MediaItem>): FormatChoice =
        formatsFor(items).firstOrNull() ?: FormatChoice.Original

    /** Tamanho estimado de um item no formato escolhido, quando conhecido. */
    fun estimatedSize(item: MediaItem, choice: FormatChoice): Long? = when (choice) {
        FormatChoice.Original -> item.sizeBytes
        is FormatChoice.Audio -> item.audioSizeBytes
        is FormatChoice.Video -> item.sizeByResolution.filterKeys { it <= choice.maxResolution }
            .maxByOrNull { it.key }?.value ?: item.sizeBytes
    }
}
