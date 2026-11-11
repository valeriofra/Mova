package io.github.valeriofra.mova.core.extractor

import io.github.valeriofra.mova.core.analysis.FormatChoice
import io.github.valeriofra.mova.core.analysis.LinkAnalysis
import io.github.valeriofra.mova.core.analysis.LinkAnalyzer
import io.github.valeriofra.mova.core.analysis.RemoteFileProbe
import io.github.valeriofra.mova.core.model.MediaSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramPublicTest {

    @Test
    fun convertsShortcodeLikeYtDlp() {
        assertEquals("1", InstagramPublic.shortcodeToMediaId("B"))
        assertEquals("64", InstagramPublic.shortcodeToMediaId("BA"))
        assertEquals("3904848280871237901", InstagramPublic.shortcodeToMediaId("DYwztKbH_kN"))
        // Códigos longos (posts privados) têm 28 caracteres extras no fim.
        assertEquals("3904848280871237901", InstagramPublic.shortcodeToMediaId("DYwztKbH_kN" + "x".repeat(28)))
    }

    private val reelGraphql = """
        {"data":{"xig_polaris_media":{"if_not_gated_logged_out":{
          "code":"DYwztKbH_kN","media_type":2,"video_duration":31.4,
          "video_versions":[
            {"type":101,"width":1080,"height":1920,"url":"https://scontent.cdninstagram.com/v/hd.mp4?x=1"},
            {"type":102,"width":720,"height":1280,"url":"https://scontent.cdninstagram.com/v/sd.mp4?x=1"}
          ],
          "image_versions2":{"candidates":[{"width":1080,"height":1920,"url":"https://scontent.cdninstagram.com/v/thumb.jpg"}]},
          "caption":{"text":"Pôr do sol na praia 🌅\nmais texto"},
          "user":{"username":"perfil.exemplo"}
        }}}}
    """.trimIndent()

    @Test
    fun parsesPublicReelFromGraphqlResponse() {
        val media = (InstagramPublic.parse(listOf(reelGraphql), "DYwztKbH_kN") as ExtractionResult.Single).media
        assertEquals("Pôr do sol na praia 🌅", media.title)
        assertEquals("perfil.exemplo", media.uploader)
        assertEquals(31L, media.durationSeconds)
        assertEquals(listOf(1080), media.resolutions)
        assertEquals("https://scontent.cdninstagram.com/v/hd.mp4?x=1", media.directUrl)
        assertEquals("https://scontent.cdninstagram.com/v/thumb.jpg", media.thumbnailUrl)
    }

    @Test
    fun findsMediaInsidePageScriptsAndSkipsOtherBlocks() {
        val other = """{"require":[["ScheduledServerJS","handle",null,[{"x":1}]]]}"""
        val page = """{"require":[["RelayPrefetchedStreamCache","next",[],["k",{"__bbox":{"result":$reelGraphql}}]]]}"""
        val result = InstagramPublic.parse(listOf("not json", other, page), "DYwztKbH_kN")
        assertTrue(result is ExtractionResult.Single)
    }

    @Test
    fun carouselWithSeveralVideosBecomesCollection() {
        val carousel = """
            {"code":"C1","media_type":8,"caption":{"text":"Viagem"},"user":{"username":"u"},
             "carousel_media":[
               {"media_type":2,"video_versions":[{"width":720,"height":1280,"url":"https://cdn/1.mp4"}]},
               {"media_type":1,"image_versions2":{"candidates":[{"url":"https://cdn/foto.jpg"}]}},
               {"media_type":2,"video_versions":[{"width":720,"height":720,"url":"https://cdn/3.mp4"}]}
             ]}
        """.trimIndent()
        val result = InstagramPublic.parse(listOf(carousel), "C1") as ExtractionResult.Collection
        assertEquals(listOf("https://cdn/1.mp4", "https://cdn/3.mp4"), result.entries.map { it.url })
        assertEquals(listOf(1, 3), result.entries.map { it.index })
        assertEquals("Viagem (3)", result.entries[1].title)
    }

    @Test
    fun responseWithoutMediaMeansLoginRequired() {
        val gated = """{"data":{"xig_polaris_media":{"if_not_gated_logged_out":null}}}"""
        val result = InstagramPublic.parse(listOf(gated), "X") as ExtractionResult.Failure
        assertEquals(ExtractionFailure.LOGIN_REQUIRED, result.reason)
    }

    @Test
    fun analyzerUsesDirectUrlFromPublicPage() = runTest {
        val media = (InstagramPublic.parse(listOf(reelGraphql), "DYwztKbH_kN") as ExtractionResult.Single).media
        val analyzer = LinkAnalyzer(
            probe = RemoteFileProbe { error("sem HTTP") },
            extractor = { _, _ -> ExtractionResult.Single(media) },
        )
        val item = analyzer.analyze("https://www.instagram.com/reel/DYwztKbH_kN/?vrfl=abc==").downloadableItems.single()
        assertEquals(MediaSource.EXTRACTOR, item.source)
        assertEquals("https://scontent.cdninstagram.com/v/hd.mp4?x=1", item.url)
        assertEquals("Pôr do sol na praia 🌅.mp4", item.fileName)
    }

    @Test
    fun failureMessageIsKeptForDiagnosis() = runTest {
        val analyzer = LinkAnalyzer(
            probe = RemoteFileProbe { error("sem HTTP") },
            extractor = { _, _ -> ExtractionResult.Failure(ExtractionFailure.LOGIN_REQUIRED, "rate-limit reached or login required") },
        )
        val result = analyzer.analyze("https://www.instagram.com/reel/ABC/").results.single() as LinkAnalysis.Restricted
        assertEquals("rate-limit reached or login required", result.message)
    }

    @Test
    fun downloadUsesTitleAsFileName() {
        val args = YtDlp.downloadArgs(FormatChoice.Video(1080), "/tmp/d", null, fileBaseName = "Pôr do sol 100%")
        assertEquals("/tmp/d/Pôr do sol 100%%.%(ext)s", args[args.indexOf("-o") + 1])
    }

    @Test
    fun bridgePayloadFromPageIsParsed() {
        // Formato enviado pelo script da página: JSON.stringify(["<json do script>", ...])
        val scripts = listOf("""{"require":[["RelayPrefetchedStreamCache","next",[],["k",{"__bbox":{"result":$reelGraphql}}]]]}""")
        val payload = kotlinx.serialization.json.JsonArray(scripts.map { kotlinx.serialization.json.JsonPrimitive(it) }).toString()
        assertTrue(InstagramBridge.parse(InstagramBridge.KIND_PAGE, payload, "DYwztKbH_kN") is ExtractionResult.Single)
        assertTrue(InstagramBridge.parse(InstagramBridge.KIND_GRAPHQL, reelGraphql, "DYwztKbH_kN") is ExtractionResult.Single)
        val error = InstagramBridge.parse(InstagramBridge.KIND_ERROR, "TypeError", "X") as ExtractionResult.Failure
        assertEquals(ExtractionFailure.LOGIN_REQUIRED, error.reason)
    }

    @Test
    fun scriptOnlyAcceptsNumericMediaId() {
        assertTrue(InstagramBridge.script("3904848280871237901").contains("3904848280871237901"))
        try {
            InstagramBridge.script("1'); alert(1); ('")
            throw AssertionError("deveria recusar")
        } catch (_: IllegalArgumentException) {
        }
    }
}
