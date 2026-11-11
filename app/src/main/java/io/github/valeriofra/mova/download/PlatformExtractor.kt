package io.github.valeriofra.mova.download

import io.github.valeriofra.mova.core.extractor.ExtractionFailure
import io.github.valeriofra.mova.core.extractor.ExtractionResult
import io.github.valeriofra.mova.core.extractor.MediaExtractor
import io.github.valeriofra.mova.core.link.LinkRecognizer
import io.github.valeriofra.mova.core.model.ContentKind
import io.github.valeriofra.mova.core.model.Platform
import kotlinx.coroutines.CancellationException

/**
 * Escolhe o melhor caminho por plataforma. Instagram (post/Reel): primeiro a
 * página pública (sem login, no WebView); se não der, o yt-dlp. Demais: yt-dlp.
 */
class PlatformExtractor(
    private val ytDlp: YtDlpEngine,
    private val instagram: InstagramWebExtractor,
) : MediaExtractor {

    private val recognizer = LinkRecognizer()

    override suspend fun extract(url: String, allowPlaylist: Boolean): ExtractionResult {
        val link = recognizer.recognize(url)
        val code = link?.contentId
        val isInstagramPost = link?.platform == Platform.INSTAGRAM && code != null &&
            link.kind in setOf(ContentKind.POST, ContentKind.SHORT_VIDEO, ContentKind.VIDEO)
        if (!isInstagramPost) return ytDlp.extract(url, allowPlaylist)

        val public = try {
            instagram.extract(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            ExtractionResult.Failure(ExtractionFailure.UNKNOWN, e.toString())
        }
        if (public !is ExtractionResult.Failure) return public

        val viaYtDlp = ytDlp.extract(url, allowPlaylist)
        if (viaYtDlp !is ExtractionResult.Failure) return viaYtDlp
        // Mantém o motivo do yt-dlp e junta as duas mensagens para diagnóstico.
        val reason = if (viaYtDlp.reason == ExtractionFailure.UNKNOWN) public.reason else viaYtDlp.reason
        return ExtractionResult.Failure(
            reason,
            listOfNotNull(
                public.message?.let { "Página pública: $it" },
                viaYtDlp.message?.let { "yt-dlp ${ytDlp.version().orEmpty()}: $it" },
            ).joinToString("\n"),
        )
    }
}
