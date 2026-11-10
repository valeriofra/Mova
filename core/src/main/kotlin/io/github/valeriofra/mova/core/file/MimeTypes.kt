package io.github.valeriofra.mova.core.file

import io.github.valeriofra.mova.core.model.MediaKind

/** Tabela própria de tipos MIME, para não depender de APIs Android. */
object MimeTypes {

    private val EXTENSION_TO_MIME: Map<String, String> = mapOf(
        // Vídeo
        "mp4" to "video/mp4",
        "m4v" to "video/x-m4v",
        "webm" to "video/webm",
        "mkv" to "video/x-matroska",
        "mov" to "video/quicktime",
        "3gp" to "video/3gpp",
        "avi" to "video/x-msvideo",
        "ts" to "video/mp2t",
        // Áudio
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "aac" to "audio/aac",
        "ogg" to "audio/ogg",
        "oga" to "audio/ogg",
        "opus" to "audio/opus",
        "wav" to "audio/wav",
        "flac" to "audio/flac",
        "amr" to "audio/amr",
        // Imagem
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "heic" to "image/heic",
        "avif" to "image/avif",
        // Documentos e outros
        "pdf" to "application/pdf",
        "zip" to "application/zip",
        "txt" to "text/plain",
        "srt" to "application/x-subrip",
        "vtt" to "text/vtt",
        "epub" to "application/epub+zip",
    )

    private val MIME_TO_EXTENSION: Map<String, String> = buildMap {
        EXTENSION_TO_MIME.forEach { (ext, mime) -> putIfAbsent(mime, ext) }
        put("video/mp4", "mp4")
        put("audio/mpeg", "mp3")
        put("audio/mp3", "mp3")
        put("audio/x-m4a", "m4a")
        put("audio/x-wav", "wav")
        put("audio/wave", "wav")
        put("audio/x-flac", "flac")
        put("image/jpeg", "jpg")
        put("image/jpg", "jpg")
    }

    /** Formatos de streaming (playlists HLS/DASH): baixar o arquivo em si não traz a mídia. */
    private val STREAMING_MIME = setOf(
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl",
        "audio/mpegurl",
        "audio/x-mpegurl",
        "application/dash+xml",
    )
    private val STREAMING_EXTENSIONS = setOf("m3u8", "mpd")

    private val GENERIC_BINARY = setOf(
        "application/octet-stream",
        "binary/octet-stream",
        "application/binary",
        "application/force-download",
        "application/download",
        "application/x-download",
    )

    private val HTML_MIME = setOf("text/html", "application/xhtml+xml")

    /** Extensão (minúscula, sem ponto) de um nome de arquivo ou segmento de URL. */
    fun extensionOf(name: String): String? {
        val clean = name.substringBefore('?').substringBefore('#')
        val dot = clean.lastIndexOf('.')
        if (dot <= 0 || dot == clean.length - 1) return null
        val ext = clean.substring(dot + 1).lowercase()
        return ext.takeIf { it.length in 1..5 && it.all { c -> c.isLetterOrDigit() } }
    }

    fun mimeForExtension(extension: String): String? = EXTENSION_TO_MIME[extension.lowercase()]

    fun extensionForMime(mime: String): String? = MIME_TO_EXTENSION[normalize(mime)]

    /** "Video/MP4; charset=..." -> "video/mp4". */
    fun normalize(mime: String?): String? =
        mime?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it.contains('/') }

    fun isGenericBinary(mime: String?): Boolean = normalize(mime)?.let { it in GENERIC_BINARY } ?: true

    fun isHtml(mime: String?): Boolean = normalize(mime) in HTML_MIME

    fun isStreamingManifest(mime: String?, extension: String?): Boolean =
        normalize(mime) in STREAMING_MIME || extension?.lowercase() in STREAMING_EXTENSIONS

    fun mediaKindForExtension(extension: String): MediaKind? {
        val ext = extension.lowercase()
        if (ext in STREAMING_EXTENSIONS) return MediaKind.OTHER
        return mimeForExtension(ext)?.let(::mediaKindForMime)
    }

    fun mediaKindForMime(mime: String?): MediaKind {
        val m = normalize(mime) ?: return MediaKind.OTHER
        return when {
            m.startsWith("video/") -> MediaKind.VIDEO
            m.startsWith("audio/") -> MediaKind.AUDIO
            m.startsWith("image/") -> MediaKind.IMAGE
            m == "application/pdf" || m == "application/epub+zip" || m.startsWith("text/") -> MediaKind.DOCUMENT
            else -> MediaKind.OTHER
        }
    }

    /**
     * Decide o MIME efetivo: usa o informado pelo servidor, exceto quando ele é
     * genérico (octet-stream), caso em que a extensão do nome é usada.
     */
    fun resolve(serverMime: String?, fileName: String?): String? {
        val normalized = normalize(serverMime)
        if (normalized != null && !isGenericBinary(normalized)) return normalized
        val fromExt = fileName?.let(::extensionOf)?.let(::mimeForExtension)
        return fromExt ?: normalized
    }
}
