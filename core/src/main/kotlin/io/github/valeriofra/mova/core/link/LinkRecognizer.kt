package io.github.valeriofra.mova.core.link

import io.github.valeriofra.mova.core.file.MimeTypes
import io.github.valeriofra.mova.core.model.ContentKind
import io.github.valeriofra.mova.core.model.MediaKind
import io.github.valeriofra.mova.core.model.Platform

/** Resultado do reconhecimento de um link, feito apenas a partir da URL. */
data class RecognizedLink(
    /** URL normalizada. */
    val url: String,
    val platform: Platform,
    val kind: ContentKind,
    /** Identificador do conteúdo na plataforma, quando aparece na URL. */
    val contentId: String? = null,
    /** Identificador de coleção associada (ex.: playlist de um vídeo do YouTube). */
    val collectionId: String? = null,
    /** Para links diretos: tipo de mídia deduzido da extensão. */
    val mediaKind: MediaKind? = null,
    val host: String,
)

/**
 * Reconhece automaticamente a origem de um link, sem pedir que o usuário
 * escolha a plataforma. Reconhecer NÃO significa que o download é possível:
 * isso é decidido pelas capacidades de cada provider.
 */
class LinkRecognizer(
    private val rules: List<PlatformRule> = PlatformRules.all,
) {
    fun recognize(url: String): RecognizedLink? {
        val parsed = ParsedUrl.parse(url) ?: return null
        return recognize(parsed)
    }

    fun recognize(url: ParsedUrl): RecognizedLink {
        for (rule in rules) {
            if (rule.matchesHost(url)) {
                val match = rule.classify(url)
                return RecognizedLink(
                    url = url.normalized,
                    platform = rule.platform,
                    kind = match.kind,
                    contentId = match.contentId,
                    collectionId = match.collectionId,
                    host = url.host,
                )
            }
        }
        val extension = url.lastSegment?.let(MimeTypes::extensionOf)
        val mediaKind = extension?.let(MimeTypes::mediaKindForExtension)
        return if (mediaKind != null) {
            RecognizedLink(
                url = url.normalized,
                platform = Platform.DIRECT,
                kind = ContentKind.FILE,
                mediaKind = mediaKind,
                host = url.host,
            )
        } else {
            RecognizedLink(
                url = url.normalized,
                platform = Platform.WEB,
                kind = ContentKind.PAGE,
                host = url.host,
            )
        }
    }
}

data class LinkMatch(
    val kind: ContentKind,
    val contentId: String? = null,
    val collectionId: String? = null,
)

/** Regra de reconhecimento de uma plataforma. */
interface PlatformRule {
    val platform: Platform
    fun matchesHost(url: ParsedUrl): Boolean
    fun classify(url: ParsedUrl): LinkMatch
}
