package io.github.valeriofra.mova.core.extractor

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebPageVideoTest {

    private fun evaluate(json: String) = JsonPrimitive(json).toString() // como o evaluateJavascript devolve

    @Test
    fun prefersPlayerMp4OverPageData() {
        val result = WebPageVideo.parse(
            evaluate(
                """{"urls":[
                  {"url":"https://cdn.exemplo/preview.mp4","kind":"page"},
                  {"url":"https://cdn.exemplo/stream.m3u8","kind":"meta"},
                  {"url":"https://cdn.exemplo/video.mp4?t=1","kind":"video"}
                ],"title":"Vídeo do Kwai","thumbnail":"https://cdn.exemplo/capa.jpg","page":"https://www.kwai.com/@u/video/1"}""",
            ),
            fallbackTitle = "Kwai",
        )!!
        assertEquals(true, result.fromPlayer)
        assertEquals("https://cdn.exemplo/video.mp4?t=1", result.result.media.directUrl)
        assertEquals("Vídeo do Kwai", result.result.media.title)
        assertEquals("https://cdn.exemplo/capa.jpg", result.result.media.thumbnailUrl)
    }

    @Test
    fun usesPageDataWhenThereIsNoPlayer() {
        val result = WebPageVideo.parse(
            evaluate("""{"urls":[{"url":"https://cdn.exemplo/a.json","kind":"page"},{"url":"https://cdn.exemplo/b.mp4","kind":"page"}],"title":""}"""),
            fallbackTitle = "Kwai",
        )!!
        assertEquals(false, result.fromPlayer)
        assertEquals("https://cdn.exemplo/b.mp4", result.result.media.directUrl)
        assertEquals("Kwai", result.result.media.title)
    }

    @Test
    fun nothingFoundReturnsNull() {
        assertNull(WebPageVideo.parse(evaluate("""{"urls":[],"title":"x"}"""), "x"))
        assertNull(WebPageVideo.parse("null", "x"))
        assertNull(WebPageVideo.parse(null, "x"))
    }
}
