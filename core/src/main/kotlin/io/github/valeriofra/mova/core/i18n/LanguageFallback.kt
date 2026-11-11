package io.github.valeriofra.mova.core.i18n

import java.util.Locale

/**
 * Idioma padrão pelo país do usuário.
 *
 * O MOVA segue o idioma do aparelho. Quando nenhum dos idiomas do aparelho tem
 * tradução, usa o idioma do país (ex.: aparelho em alemão no Brasil → português)
 * e só então o inglês.
 */
object LanguageFallback {

    /** Idiomas com tradução no app (o inglês é o padrão). */
    val SUPPORTED: Set<String> = setOf("en", "pt", "es", "fr")

    private val PORTUGUESE = setOf("BR", "PT", "AO", "MZ", "CV", "GW", "ST", "TL", "MO")

    private val SPANISH = setOf(
        "ES", "MX", "AR", "CO", "CL", "PE", "VE", "EC", "GT", "CU", "BO", "DO", "HN",
        "PY", "SV", "NI", "CR", "PA", "UY", "PR", "GQ",
    )

    /**
     * Países onde o francês é língua oficial ou de uso corrente. Bélgica, Suíça e
     * Canadá ficam de fora: são multilíngues e o francês não é o idioma da maioria.
     */
    private val FRENCH = setOf(
        "FR", "LU", "MC", "SN", "CI", "ML", "BF", "NE", "GN", "BJ", "TG", "CM", "CD", "CG",
        "GA", "CF", "TD", "MG", "HT", "BI", "DJ", "KM", "RE", "GP", "MQ", "GF", "YT", "PF",
        "NC", "PM", "WF", "BL", "MF", "MA", "DZ", "TN",
    )

    /** Idioma do app falado no país (código ISO de 2 letras), ou null. */
    fun languageForCountry(country: String?): String? {
        val code = country?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.length == 2 } ?: return null
        return when (code) {
            in PORTUGUESE -> "pt"
            in SPANISH -> "es"
            in FRENCH -> "fr"
            else -> null
        }
    }

    /**
     * Idioma a acrescentar no fim da lista do aparelho, ou null quando não é
     * preciso (algum idioma do aparelho já tem tradução, ou o país não ajuda).
     */
    fun fallbackFor(deviceLanguages: List<String>, country: String?): String? {
        val hasTranslation = deviceLanguages.any { languageCode(it) in SUPPORTED }
        return if (hasTranslation) null else languageForCountry(country)
    }

    private fun languageCode(tag: String): String =
        tag.trim().lowercase(Locale.ROOT).substringBefore('-').substringBefore('_')
}
