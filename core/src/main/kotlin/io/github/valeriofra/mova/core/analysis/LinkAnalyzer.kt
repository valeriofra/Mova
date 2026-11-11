package io.github.valeriofra.mova.core.analysis

import io.github.valeriofra.mova.core.extractor.ExtractedMedia
import io.github.valeriofra.mova.core.extractor.ExtractionFailure
import io.github.valeriofra.mova.core.extractor.ExtractionResult
import io.github.valeriofra.mova.core.extractor.MediaExtractor
import io.github.valeriofra.mova.core.file.FileNames
import io.github.valeriofra.mova.core.file.MimeTypes
import io.github.valeriofra.mova.core.link.LinkRecognizer
import io.github.valeriofra.mova.core.link.ParsedUrl
import io.github.valeriofra.mova.core.link.RecognizedLink
import io.github.valeriofra.mova.core.link.UrlExtractor
import io.github.valeriofra.mova.core.model.ContentKind
import io.github.valeriofra.mova.core.model.MediaKind
import io.github.valeriofra.mova.core.model.MediaSource
import io.github.valeriofra.mova.core.model.Platform
import io.github.valeriofra.mova.core.provider.ProviderRegistry
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Informações de um arquivo remoto obtidas sem baixá-lo por inteiro. */
data class ProbeResult(
    val finalUrl: String,
    val httpCode: Int,
    val contentType: String?,
    val contentLength: Long?,
    val contentDisposition: String?,
    val acceptsRanges: Boolean,
    val etag: String?,
    val lastModified: String?,
)

/** Consulta um arquivo remoto. Implementado na plataforma (ex.: OkHttp no Android). */
fun interface RemoteFileProbe {
    @Throws(IOException::class)
    suspend fun probe(url: String): ProbeResult
}

/**
 * Analisa o texto informado: encontra os links, reconhece a origem de cada um
 * e verifica quais ações são realmente possíveis.
 */
class LinkAnalyzer(
    private val probe: RemoteFileProbe,
    /** Mecanismo de extração para páginas de plataformas. Sem ele, só links diretos são baixados. */
    private val extractor: MediaExtractor? = null,
    private val recognizer: LinkRecognizer = LinkRecognizer(),
    private val maxParallelProbes: Int = 4,
) {

    suspend fun analyze(input: String): AnalysisReport {
        val urls = UrlExtractor.extract(input)
        if (urls.isEmpty()) return AnalysisReport(input, emptyList())
        val semaphore = Semaphore(maxParallelProbes)
        val results = coroutineScope {
            urls.map { url -> async { semaphore.withPermit { analyzeUrl(url) } } }.awaitAll()
        }
        return AnalysisReport(input, results.filterNotNull())
    }

    private suspend fun analyzeUrl(url: String): LinkAnalysis? {
        val parsed = ParsedUrl.parse(url) ?: return null
        val link = recognizer.recognize(parsed)
        val capabilities = ProviderRegistry.of(link.platform)

        // Plataformas (YouTube, Instagram...): usa o mecanismo de extração.
        if (link.platform.isKnownPlatform) {
            if (!capabilities.downloadsFiles || extractor == null) {
                return LinkAnalysis.RecognizedWithoutDownload(url, link)
            }
            return extract(url, link, fallback = null)
        }

        // Links diretos e outros sites: consulta o arquivo por HTTP.
        val result = try {
            probe.probe(link.url)
        } catch (e: IOException) {
            return LinkAnalysis.TemporaryError(url, link, null, e.message)
        }
        val analysis = interpret(url, link, result)
        // Uma página comum pode ter um vídeo incorporado que o mecanismo reconhece.
        if (analysis is LinkAnalysis.UnrecognizedPage && extractor != null) {
            return extract(url, link, fallback = analysis)
        }
        return analysis
    }

    private suspend fun extract(input: String, link: RecognizedLink, fallback: LinkAnalysis?): LinkAnalysis {
        val result = try {
            extractor!!.extract(link.url, allowPlaylist = allowsPlaylist(link.kind))
        } catch (e: IOException) {
            return fallback ?: LinkAnalysis.TemporaryError(input, link, null, e.message)
        }
        return when (result) {
            is ExtractionResult.Single -> LinkAnalysis.Ready(input, link, listOf(itemFrom(link, result.media)))
            is ExtractionResult.Collection -> {
                val items = result.entries.map { entry ->
                    val ownUrl = entry.url
                    val title = entry.title?.takeIf { it.isNotBlank() } ?: "Item ${entry.index}"
                    extractorItem(
                        link = link,
                        url = ownUrl ?: result.webpageUrl ?: link.url,
                        title = title,
                        hasVideo = true,
                        durationSeconds = entry.durationSeconds,
                        thumbnailUrl = entry.thumbnailUrl,
                        uploader = result.uploader,
                        playlistIndex = if (ownUrl == null) entry.index else null,
                    )
                }
                LinkAnalysis.Ready(input, link, items, collectionTitle = result.title)
            }
            is ExtractionResult.Failure -> fallback ?: failureToAnalysis(input, link, result)
        }
    }

    private fun failureToAnalysis(input: String, link: RecognizedLink, failure: ExtractionResult.Failure): LinkAnalysis =
        when (failure.reason) {
            ExtractionFailure.UNSUPPORTED -> LinkAnalysis.NotSupported(input, link)
            ExtractionFailure.PRIVATE -> LinkAnalysis.Restricted(input, link, RestrictionReason.PRIVATE)
            ExtractionFailure.LOGIN_REQUIRED -> LinkAnalysis.Restricted(input, link, RestrictionReason.LOGIN_REQUIRED)
            ExtractionFailure.DRM -> LinkAnalysis.Restricted(input, link, RestrictionReason.DRM)
            ExtractionFailure.GEO_BLOCKED -> LinkAnalysis.Restricted(input, link, RestrictionReason.GEO_BLOCKED)
            ExtractionFailure.UNAVAILABLE -> LinkAnalysis.Unavailable(input, link, null)
            ExtractionFailure.RATE_LIMITED,
            ExtractionFailure.NETWORK,
            ExtractionFailure.UNKNOWN,
            -> LinkAnalysis.TemporaryError(input, link, null, failure.message)
        }

    private fun allowsPlaylist(kind: ContentKind): Boolean =
        kind != ContentKind.VIDEO && kind != ContentKind.SHORT_VIDEO && kind != ContentKind.LIVE

    private fun itemFrom(link: RecognizedLink, media: ExtractedMedia): MediaItem =
        extractorItem(
            link = link,
            url = media.webpageUrl ?: link.url,
            title = media.title,
            hasVideo = media.hasVideo,
            durationSeconds = media.durationSeconds,
            thumbnailUrl = media.thumbnailUrl,
            uploader = media.uploader,
            playlistIndex = null,
            resolutions = media.resolutions,
            sizeByResolution = media.sizeByResolution,
            audioSize = media.audioSizeBytes,
        )

    private fun extractorItem(
        link: RecognizedLink,
        url: String,
        title: String,
        hasVideo: Boolean,
        durationSeconds: Long?,
        thumbnailUrl: String?,
        uploader: String?,
        playlistIndex: Int?,
        resolutions: List<Int> = emptyList(),
        sizeByResolution: Map<Int, Long> = emptyMap(),
        audioSize: Long? = null,
    ): MediaItem {
        val cleanTitle = FileNames.sanitize(title).ifEmpty { "video" }
        val ext = if (hasVideo) "mp4" else "mp3"
        val platform = if (link.platform == Platform.DIRECT) Platform.WEB else link.platform
        val bestSize = sizeByResolution.filterKeys { it <= 1080 }.maxByOrNull { it.key }?.value
        return MediaItem(
            url = url,
            sourceUrl = link.url,
            platform = platform,
            fileName = "$cleanTitle.$ext",
            title = title,
            mimeType = MimeTypes.mimeForExtension(ext),
            mediaKind = if (hasVideo) MediaKind.VIDEO else MediaKind.AUDIO,
            sizeBytes = if (hasVideo) bestSize else audioSize,
            supportsResume = true,
            etag = null,
            lastModified = null,
            source = MediaSource.EXTRACTOR,
            durationSeconds = durationSeconds,
            thumbnailUrl = thumbnailUrl,
            uploader = uploader,
            resolutions = resolutions,
            hasVideo = hasVideo,
            sizeByResolution = sizeByResolution,
            audioSizeBytes = audioSize,
            playlistIndex = playlistIndex,
        )
    }

    internal fun interpret(input: String, link: RecognizedLink, result: ProbeResult): LinkAnalysis {
        val code = result.httpCode
        return when {
            code in 200..299 -> interpretSuccess(input, link, result)
            code == 401 || code == 403 || code == 407 || code == 451 ->
                LinkAnalysis.AccessDenied(input, link, code)
            code == 404 || code == 410 -> LinkAnalysis.Unavailable(input, link, code)
            else -> LinkAnalysis.TemporaryError(input, link, code, null)
        }
    }

    private fun interpretSuccess(input: String, link: RecognizedLink, result: ProbeResult): LinkAnalysis {
        val finalUrl = ParsedUrl.parse(result.finalUrl)
        val lastSegment = finalUrl?.lastSegment ?: ParsedUrl.parse(link.url)?.lastSegment
        val extension = lastSegment?.let(MimeTypes::extensionOf)

        if (MimeTypes.isStreamingManifest(result.contentType, extension)) {
            return LinkAnalysis.UnsupportedFormat(input, link, MimeTypes.normalize(result.contentType))
        }
        if (MimeTypes.isHtml(result.contentType)) {
            return LinkAnalysis.UnrecognizedPage(input, link)
        }
        // Servidor sem tipo definido e URL sem extensão conhecida: provavelmente uma página.
        val dispositionName = FileNames.fromContentDisposition(result.contentDisposition)
        if (MimeTypes.normalize(result.contentType) == null && extension == null && dispositionName == null) {
            return LinkAnalysis.UnrecognizedPage(input, link)
        }

        val provisionalName = dispositionName ?: lastSegment
        val mime = MimeTypes.resolve(result.contentType, provisionalName)
        val fileName = FileNames.choose(
            contentDisposition = result.contentDisposition,
            urlLastSegment = lastSegment,
            mime = mime,
            fallbackBase = "mova-${link.host}",
        )
        val item = MediaItem(
            url = result.finalUrl,
            sourceUrl = link.url,
            platform = link.platform,
            fileName = fileName,
            title = FileNames.titleOf(fileName),
            mimeType = mime,
            mediaKind = MimeTypes.mediaKindForMime(mime),
            sizeBytes = result.contentLength?.takeIf { it >= 0 },
            supportsResume = result.acceptsRanges && (result.contentLength ?: -1) > 0,
            etag = result.etag,
            lastModified = result.lastModified,
        )
        return LinkAnalysis.Ready(input, link, listOf(item))
    }
}
