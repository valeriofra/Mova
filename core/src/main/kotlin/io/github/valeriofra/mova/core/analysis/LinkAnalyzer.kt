package io.github.valeriofra.mova.core.analysis

import io.github.valeriofra.mova.core.file.FileNames
import io.github.valeriofra.mova.core.file.MimeTypes
import io.github.valeriofra.mova.core.link.LinkRecognizer
import io.github.valeriofra.mova.core.link.ParsedUrl
import io.github.valeriofra.mova.core.link.RecognizedLink
import io.github.valeriofra.mova.core.link.UrlExtractor
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
        if (!capabilities.downloadsFiles) {
            return LinkAnalysis.RecognizedWithoutDownload(url, link)
        }

        val result = try {
            probe.probe(link.url)
        } catch (e: IOException) {
            return LinkAnalysis.TemporaryError(url, link, null, e.message)
        }
        return interpret(url, link, result)
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
