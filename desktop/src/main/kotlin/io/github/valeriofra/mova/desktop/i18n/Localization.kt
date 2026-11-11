package io.github.valeriofra.mova.desktop.i18n

import io.github.valeriofra.mova.core.i18n.LanguageFallback
import java.util.Locale

/**
 * Idioma da interface. Automático: o do sistema, se houver tradução; senão o
 * do país do usuário (ex.: Windows em alemão no Brasil → português); por
 * último, o inglês. A escolha manual em Ajustes tem prioridade.
 */
object Localization {

    /** Idiomas traduzidos, na ordem exibida em Ajustes, com o nome no próprio idioma. */
    val LANGUAGES = listOf("pt" to "Português", "en" to "English", "es" to "Español", "fr" to "Français")

    /** Idioma do sistema ao abrir o app (antes de qualquer troca). */
    private val systemLocale: Locale = Locale.getDefault()

    fun resolve(choice: String?, system: Locale = systemLocale): Locale {
        if (choice != null && LANGUAGES.any { it.first == choice }) {
            return if (choice == "pt") Locale.forLanguageTag("pt-BR") else Locale.forLanguageTag(choice)
        }
        val fallback = LanguageFallback.fallbackFor(listOf(system.toLanguageTag()), system.country) ?: return system
        return Locale.Builder().setLanguage(fallback).setRegion(system.country).build()
    }

    /** Aplica o idioma ao app (textos e formatação de números). */
    fun apply(choice: String?): Locale = resolve(choice).also { Locale.setDefault(it) }

    /** Código do idioma em uso, entre os traduzidos ("en" quando nenhum se aplica). */
    fun effectiveLanguage(locale: Locale = Locale.getDefault()): String =
        LANGUAGES.firstOrNull { it.first == locale.language }?.first ?: "en"
}
