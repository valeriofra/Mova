package io.github.valeriofra.mova.core.analysis

import io.github.valeriofra.mova.core.file.MimeTypes

/** Uma opção de download apresentada ao usuário depois da análise. */
sealed interface DownloadOption {
    val key: String

    /** Um único arquivo, na qualidade original da fonte. */
    data class SingleFile(val item: MediaItem) : DownloadOption {
        override val key: String = "single:${item.url}"

        /** Ex.: "MP4". */
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
}
