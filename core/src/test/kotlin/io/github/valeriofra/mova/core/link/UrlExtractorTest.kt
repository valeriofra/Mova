package io.github.valeriofra.mova.core.link

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlExtractorTest {

    @Test
    fun extractsUrlFromSharedSentence() {
        val text = "Olha esse vídeo: https://youtu.be/dQw4w9WgXcQ?si=xyz. Muito bom!"
        assertEquals(listOf("https://youtu.be/dQw4w9WgXcQ?si=xyz"), UrlExtractor.extract(text))
    }

    @Test
    fun extractsSeveralUrlsWithoutDuplicates() {
        val text = """
            https://exemplo.com/a.mp4
            https://exemplo.com/b.mp3, https://exemplo.com/a.mp4
            (https://exemplo.com/c.jpg)
        """.trimIndent()
        assertEquals(
            listOf("https://exemplo.com/a.mp4", "https://exemplo.com/b.mp3", "https://exemplo.com/c.jpg"),
            UrlExtractor.extract(text),
        )
    }

    @Test
    fun keepsBalancedParentheses() {
        val url = "https://pt.wikipedia.org/wiki/Teste_(desambiguação)"
        assertEquals(listOf(url), UrlExtractor.extract("veja $url"))
    }

    @Test
    fun acceptsBareDomainWhenItIsTheWholeInput() {
        assertEquals(listOf("https://youtube.com/watch?v=dQw4w9WgXcQ"), UrlExtractor.extract("  youtube.com/watch?v=dQw4w9WgXcQ "))
    }

    @Test
    fun ignoresPlainText() {
        assertEquals(emptyList<String>(), UrlExtractor.extract("nenhum link aqui"))
        assertEquals(emptyList<String>(), UrlExtractor.extract("   "))
    }
}
