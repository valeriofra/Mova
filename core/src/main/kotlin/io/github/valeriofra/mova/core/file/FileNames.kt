package io.github.valeriofra.mova.core.file

import java.net.URLDecoder

/** Regras para gerar nomes de arquivo seguros e legíveis. */
object FileNames {

    private const val MAX_LENGTH = 120
    private val ILLEGAL = Regex("""[\\/:*?"<>|\u0000-\u001F\u007F]""")
    private val SPACES = Regex("""\s+""")

    /**
     * Lê o nome sugerido em um cabeçalho Content-Disposition. Dá preferência a
     * `filename*` (RFC 5987, com codificação UTF-8) sobre `filename`.
     */
    fun fromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val extended = Regex("""(?i)filename\*\s*=\s*([^']*)'[^']*'([^;]+)""").find(header)
        if (extended != null) {
            val charset = extended.groupValues[1].ifBlank { "UTF-8" }
            val value = extended.groupValues[2].trim().trim('"')
            val decoded = try {
                URLDecoder.decode(value.replace("+", "%2B"), charset)
            } catch (_: Exception) {
                null
            }
            if (!decoded.isNullOrBlank()) return decoded
        }
        val quoted = Regex("""(?i)filename\s*=\s*"([^"]*)"""").find(header)?.groupValues?.get(1)
        if (!quoted.isNullOrBlank()) return quoted
        val bare = Regex("""(?i)filename\s*=\s*([^;]+)""").find(header)?.groupValues?.get(1)?.trim()
        return bare?.takeIf { it.isNotBlank() }
    }

    /** Remove caracteres inválidos e limita o tamanho, preservando a extensão. */
    fun sanitize(name: String): String {
        var clean = name.replace(ILLEGAL, "_").replace(SPACES, " ").trim().trim('.', ' ')
        if (clean.isEmpty()) return ""
        if (clean.length > MAX_LENGTH) {
            val ext = MimeTypes.extensionOf(clean)
            clean = if (ext != null) {
                val base = clean.substring(0, clean.length - ext.length - 1)
                base.take(MAX_LENGTH - ext.length - 1).trimEnd('.', ' ') + "." + ext
            } else {
                clean.take(MAX_LENGTH).trimEnd('.', ' ')
            }
        }
        return clean
    }

    /**
     * Escolhe o nome final do arquivo, na ordem: Content-Disposition, último
     * segmento da URL, nome genérico. Garante uma extensão coerente com o MIME.
     */
    fun choose(
        contentDisposition: String?,
        urlLastSegment: String?,
        mime: String?,
        fallbackBase: String,
    ): String {
        val candidates = listOfNotNull(
            fromContentDisposition(contentDisposition),
            urlLastSegment,
        )
        val base = candidates.map(::sanitize).firstOrNull { it.isNotEmpty() } ?: sanitize(fallbackBase)
        return ensureExtension(base.ifEmpty { "mova" }, mime)
    }

    /** Acrescenta a extensão do MIME quando o nome não tem nenhuma reconhecida. */
    fun ensureExtension(name: String, mime: String?): String {
        val currentExt = MimeTypes.extensionOf(name)
        if (currentExt != null && MimeTypes.mimeForExtension(currentExt) != null) return name
        val ext = mime?.let(MimeTypes::extensionForMime) ?: return name
        return "$name.$ext"
    }

    /** Nome sem a extensão, usado como título. */
    fun titleOf(fileName: String): String {
        val ext = MimeTypes.extensionOf(fileName) ?: return fileName
        return fileName.removeSuffix(".$ext")
    }
}
