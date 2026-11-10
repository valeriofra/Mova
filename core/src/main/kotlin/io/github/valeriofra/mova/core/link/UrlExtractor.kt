package io.github.valeriofra.mova.core.link

/**
 * Extrai links de um texto colado ou compartilhado. Aplicativos costumam
 * compartilhar frases como "Veja isto: https://...", então o texto inteiro
 * nem sempre é um link.
 */
object UrlExtractor {

    private val HTTP_URL = Regex("""(?i)\bhttps?://[^\s<>"'`{}|\\^]+""")

    /** Domínio sem esquema, aceito apenas quando é o texto inteiro. */
    private val BARE_DOMAIN = Regex(
        """(?i)^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,24}(?::\d{1,5})?(?:[/?#]\S*)?$""",
    )

    private const val TRAILING_PUNCTUATION = ".,;:!?…»”’\"'"

    /** Retorna os links encontrados, sem duplicatas, na ordem em que aparecem. */
    fun extract(text: String, limit: Int = 50): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val found = HTTP_URL.findAll(trimmed).map { cleanup(it.value) }.toMutableList()
        if (found.isEmpty() && BARE_DOMAIN.matches(trimmed)) {
            found += "https://" + cleanup(trimmed)
        }
        return found
            .filter { ParsedUrl.parse(it) != null }
            .distinct()
            .take(limit)
    }

    private fun cleanup(raw: String): String {
        var url = raw
        while (url.isNotEmpty()) {
            val last = url.last()
            url = when {
                last in TRAILING_PUNCTUATION -> url.dropLast(1)
                last == ')' && url.count { it == '(' } < url.count { it == ')' } -> url.dropLast(1)
                last == ']' && url.count { it == '[' } < url.count { it == ']' } -> url.dropLast(1)
                else -> return url
            }
        }
        return url
    }
}
