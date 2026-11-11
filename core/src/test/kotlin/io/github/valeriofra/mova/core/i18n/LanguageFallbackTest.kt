package io.github.valeriofra.mova.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageFallbackTest {

    @Test
    fun deviceLanguageWithTranslationWins() {
        // Aparelho em inglês no Brasil: a pessoa escolheu o inglês.
        assertNull(LanguageFallback.fallbackFor(listOf("en-US"), "BR"))
        assertNull(LanguageFallback.fallbackFor(listOf("pt-PT"), "BR"))
        // Algum idioma da lista tem tradução: o Android já escolhe esse.
        assertNull(LanguageFallback.fallbackFor(listOf("de-DE", "es-ES"), "BR"))
    }

    @Test
    fun untranslatedDeviceLanguageFallsBackToCountry() {
        assertEquals("pt", LanguageFallback.fallbackFor(listOf("de-DE"), "BR"))
        assertEquals("pt", LanguageFallback.fallbackFor(listOf("zh-CN"), "ao"))
        assertEquals("es", LanguageFallback.fallbackFor(listOf("ca-ES"), "ES"))
        assertEquals("es", LanguageFallback.fallbackFor(listOf("gn"), "PY"))
        assertEquals("fr", LanguageFallback.fallbackFor(listOf("ar-MA"), "MA"))
        assertEquals("fr", LanguageFallback.fallbackFor(listOf("wo_SN"), "SN"))
    }

    @Test
    fun otherCountriesKeepEnglish() {
        assertNull(LanguageFallback.fallbackFor(listOf("ja-JP"), "JP"))
        assertNull(LanguageFallback.fallbackFor(listOf("nl-BE"), "BE"))
        assertNull(LanguageFallback.fallbackFor(listOf("de-DE"), null))
        assertNull(LanguageFallback.fallbackFor(listOf("de-DE"), ""))
        assertNull(LanguageFallback.languageForCountry("BRA"))
    }
}
