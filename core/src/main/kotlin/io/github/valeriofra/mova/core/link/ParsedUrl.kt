package io.github.valeriofra.mova.core.link

import java.net.URLDecoder

/**
 * URL decomposta de forma tolerante. Não usa java.net.URI porque links
 * colados por usuários frequentemente têm caracteres que o URI rejeita.
 */
data class ParsedUrl(
    val original: String,
    val scheme: String,
    /** Host em minúsculas, sem ponto final. Ex.: "m.youtube.com". */
    val host: String,
    val port: Int?,
    /** Segmentos do caminho, já decodificados e sem vazios. */
    val segments: List<String>,
    val rawQuery: String?,
) {
    /** Host sem prefixos comuns (www., m., mobile., web.). */
    val baseHost: String = host.removeCommonPrefixes()

    val query: Map<String, String> by lazy { parseQuery(rawQuery) }

    fun param(name: String): String? = query[name]?.takeIf { it.isNotBlank() }

    fun segment(index: Int): String? = segments.getOrNull(index)

    /** Verdadeiro se o host é [domain] ou um subdomínio dele. */
    fun isHost(domain: String): Boolean = host == domain || host.endsWith(".$domain")

    /** Último segmento do caminho (normalmente o nome do arquivo). */
    val lastSegment: String? get() = segments.lastOrNull()

    /** URL normalizada (esquema e host em minúsculas). */
    val normalized: String
        get() = buildString {
            append(scheme).append("://").append(host)
            if (port != null) append(':').append(port)
            append(pathPart(original))
        }

    companion object {
        private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://")

        fun parse(input: String): ParsedUrl? {
            val text = input.trim()
            if (text.isEmpty() || text.any { it.isWhitespace() }) return null
            val withScheme = if (SCHEME.containsMatchIn(text)) text else "https://$text"
            val scheme = SCHEME.find(withScheme)!!.groupValues[1].lowercase()
            if (scheme != "http" && scheme != "https") return null

            val afterScheme = withScheme.substring(scheme.length + 3)
            val authorityEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
                .let { if (it < 0) afterScheme.length else it }
            var authority = afterScheme.substring(0, authorityEnd)
            authority = authority.substringAfterLast('@')
            if (authority.isEmpty()) return null

            val hostPart: String
            val port: Int?
            val colon = authority.lastIndexOf(':')
            if (colon > 0 && !authority.endsWith("]")) {
                hostPart = authority.substring(0, colon)
                port = authority.substring(colon + 1).toIntOrNull() ?: return null
            } else {
                hostPart = authority
                port = null
            }
            val host = hostPart.lowercase().trimEnd('.')
            if (!isValidHost(host)) return null

            val rest = afterScheme.substring(authorityEnd)
            val withoutFragment = rest.substringBefore('#')
            val path = withoutFragment.substringBefore('?')
            val rawQuery = if (withoutFragment.contains('?')) withoutFragment.substringAfter('?') else null
            val segments = path.split('/').filter { it.isNotEmpty() }.map { decode(it) }

            return ParsedUrl(
                original = withScheme,
                scheme = scheme,
                host = host,
                port = port,
                segments = segments,
                rawQuery = rawQuery,
            )
        }

        private fun isValidHost(host: String): Boolean {
            if (host.isEmpty() || host.length > 253) return false
            if (host == "localhost") return true
            if (!host.contains('.')) return false
            return host.split('.').all { label ->
                label.isNotEmpty() && label.length <= 63 &&
                    label.all { it.isLetterOrDigit() || it == '-' } &&
                    !label.startsWith('-') && !label.endsWith('-')
            }
        }

        private fun pathPart(url: String): String {
            val afterScheme = url.substringAfter("://")
            val idx = afterScheme.indexOfFirst { it == '/' || it == '?' }
            return if (idx < 0) "" else afterScheme.substring(idx).substringBefore('#')
        }

        internal fun decode(value: String): String =
            try {
                URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
            } catch (_: IllegalArgumentException) {
                value
            }

        private fun parseQuery(raw: String?): Map<String, String> {
            if (raw.isNullOrEmpty()) return emptyMap()
            val result = LinkedHashMap<String, String>()
            raw.split('&').forEach { pair ->
                if (pair.isEmpty()) return@forEach
                val key = decode(pair.substringBefore('='))
                val value = if (pair.contains('=')) decodeQueryValue(pair.substringAfter('=')) else ""
                result.putIfAbsent(key, value)
            }
            return result
        }

        private fun decodeQueryValue(value: String): String =
            try {
                URLDecoder.decode(value, "UTF-8")
            } catch (_: IllegalArgumentException) {
                value
            }

        private val COMMON_PREFIXES = listOf("www.", "m.", "mobile.", "web.")

        private fun String.removeCommonPrefixes(): String {
            var h = this
            var changed = true
            while (changed) {
                changed = false
                for (prefix in COMMON_PREFIXES) {
                    if (h.startsWith(prefix) && h.count { it == '.' } > 1) {
                        h = h.removePrefix(prefix)
                        changed = true
                    }
                }
            }
            return h
        }
    }
}
