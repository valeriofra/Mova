package io.github.valeriofra.mova.core.analysis

import io.github.valeriofra.mova.core.format.Formatters
import io.github.valeriofra.mova.core.model.MediaKind
import io.github.valeriofra.mova.core.model.Platform
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkAnalyzerTest {

    private val probedUrls = mutableListOf<String>()

    private fun analyzer(handler: (String) -> ProbeResult) = LinkAnalyzer(
        probe = { url ->
            synchronized(probedUrls) { probedUrls += url }
            handler(url)
        },
    )

    private fun ok(
        url: String,
        type: String?,
        length: Long? = 1000,
        ranges: Boolean = true,
        disposition: String? = null,
    ) = ProbeResult(url, 200, type, length, disposition, ranges, "\"etag\"", null)

    @Test
    fun socialPlatformsAreRecognizedButNeverProbedOrDownloaded() = runTest {
        val report = analyzer { error("não deveria acessar a rede") }
            .analyze("https://www.instagram.com/stories/usuario/123/")
        val result = report.results.single()
        assertTrue(result is LinkAnalysis.RecognizedWithoutDownload)
        assertEquals(Platform.INSTAGRAM, (result as LinkAnalysis.RecognizedWithoutDownload).link.platform)
        assertTrue(report.downloadableItems.isEmpty())
        assertTrue(probedUrls.isEmpty())
    }

    @Test
    fun directVideoBecomesDownloadableItem() = runTest {
        val report = analyzer { ok(it, "video/mp4", 50_000_000) }
            .analyze("https://exemplo.com/filmes/Video%2001.mp4")
        val item = report.downloadableItems.single()
        assertEquals("Video 01.mp4", item.fileName)
        assertEquals("Video 01", item.title)
        assertEquals(MediaKind.VIDEO, item.mediaKind)
        assertEquals(50_000_000L, item.sizeBytes)
        assertTrue(item.supportsResume)
        assertEquals(Platform.DIRECT, item.platform)
    }

    @Test
    fun genericBinaryUsesExtensionAndDisposition() = runTest {
        val report = analyzer {
            ok(it, "application/octet-stream", 1234, ranges = false, disposition = "attachment; filename=\"faixa.mp3\"")
        }.analyze("https://exemplo.com/download?id=42")
        val item = report.downloadableItems.single()
        assertEquals("faixa.mp3", item.fileName)
        assertEquals("audio/mpeg", item.mimeType)
        assertEquals(MediaKind.AUDIO, item.mediaKind)
        assertFalse(item.supportsResume)
    }

    @Test
    fun htmlPageIsReportedAsUnrecognized() = runTest {
        val report = analyzer { ok(it, "text/html; charset=utf-8") }.analyze("https://exemplo.com/artigo")
        assertTrue(report.results.single() is LinkAnalysis.UnrecognizedPage)
    }

    @Test
    fun streamingManifestIsNotOffered() = runTest {
        val report = analyzer { ok(it, "application/vnd.apple.mpegurl") }.analyze("https://exemplo.com/live.m3u8")
        assertTrue(report.results.single() is LinkAnalysis.UnsupportedFormat)
        assertTrue(report.downloadableItems.isEmpty())
    }

    @Test
    fun httpErrorsMapToClearStates() = runTest {
        fun code(c: Int) = ProbeResult("https://exemplo.com/a.mp4", c, null, null, null, false, null, null)
        assertTrue(analyzer { code(403) }.analyze("https://exemplo.com/a.mp4").results.single() is LinkAnalysis.AccessDenied)
        assertTrue(analyzer { code(404) }.analyze("https://exemplo.com/a.mp4").results.single() is LinkAnalysis.Unavailable)
        assertTrue(analyzer { code(503) }.analyze("https://exemplo.com/a.mp4").results.single() is LinkAnalysis.TemporaryError)
        assertTrue(
            analyzer { throw IOException("sem rede") }.analyze("https://exemplo.com/a.mp4").results.single()
                is LinkAnalysis.TemporaryError,
        )
    }

    @Test
    fun severalLinksProduceSelectableItems() = runTest {
        val report = analyzer { url ->
            when {
                url.endsWith(".mp4") -> ok(url, "video/mp4", 100)
                url.endsWith(".mp3") -> ok(url, "audio/mpeg", 50)
                else -> ok(url, "text/html")
            }
        }.analyze("https://a.com/1.mp4 https://a.com/2.mp3 https://a.com/pagina https://youtu.be/dQw4w9WgXcQ")
        assertEquals(4, report.results.size)
        assertEquals(2, report.downloadableItems.size)

        val options = DownloadOptions.forItems(report.downloadableItems)
        assertEquals(2, options.size)
        assertEquals(150L, (options[0] as DownloadOption.AllItems).totalBytes)
        assertTrue(options[1] is DownloadOption.SelectItems)
    }

    @Test
    fun emptyInputHasNoResults() = runTest {
        val report = analyzer { error("sem rede") }.analyze("texto sem link")
        assertTrue(report.isEmpty)
        assertTrue(DownloadOptions.forItems(report.downloadableItems).isEmpty())
    }

    @Test
    fun formatsSizes() {
        assertEquals("512 B", Formatters.bytes(512))
        assertEquals("48,2 MB", Formatters.bytes((48.2 * 1024 * 1024).toLong()))
        assertEquals(72, Formatters.percent(72, 100))
        assertEquals(null, Formatters.percent(10, null))
    }
}
