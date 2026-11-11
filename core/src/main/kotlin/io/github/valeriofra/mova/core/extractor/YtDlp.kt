package io.github.valeriofra.mova.core.extractor

import io.github.valeriofra.mova.core.analysis.AudioFormat
import io.github.valeriofra.mova.core.analysis.FormatChoice
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** Regras para conversar com o yt-dlp: argumentos, saída JSON, progresso e erros. */
object YtDlp {

    /** Limite de itens listados em playlists/perfis muito grandes. */
    const val PLAYLIST_LIMIT = 200

    // ------------------------------------------------------------------
    // Argumentos
    // ------------------------------------------------------------------

    /** Argumentos para obter as informações (sem baixar). */
    fun analyzeArgs(allowPlaylist: Boolean): List<String> = buildList {
        add("--dump-single-json")
        add("--flat-playlist")
        add("--no-warnings")
        add("--playlist-end"); add(PLAYLIST_LIMIT.toString())
        add(if (allowPlaylist) "--yes-playlist" else "--no-playlist")
    }

    /**
     * Argumentos para baixar no formato escolhido para dentro de [outputDir].
     * [playlistIndex] seleciona um item de uma coleção acessada pelo link dela.
     */
    fun downloadArgs(
        choice: FormatChoice,
        outputDir: String,
        playlistIndex: Int?,
        fileBaseName: String? = null,
    ): List<String> = buildList {
        add("--no-warnings")
        add("--no-mtime")
        add("--continue")
        add("--retries"); add("5")
        add("--fragment-retries"); add("10")
        add("--concurrent-fragments"); add("4")
        // Nome do arquivo: o título já mostrado ao usuário (ou o do mecanismo).
        val base = fileBaseName?.takeIf { it.isNotBlank() }?.replace("%", "%%")?.take(90)
        add("-o"); add(if (base != null) "$outputDir/$base.%(ext)s" else "$outputDir/%(title).90B.%(ext)s")
        if (playlistIndex != null) {
            add("--yes-playlist")
            add("--playlist-items"); add(playlistIndex.toString())
        } else {
            add("--no-playlist")
        }
        when (choice) {
            is FormatChoice.Video -> {
                add("-f"); add("bv*+ba/b")
                // Melhor resolução até o limite; prefere H.264/AAC em MP4 (toca em qualquer aparelho).
                add("-S"); add("res:${choice.maxResolution},vcodec:h264,acodec:aac,ext:mp4:m4a")
                add("--merge-output-format"); add("mp4")
                add("--embed-metadata")
            }
            is FormatChoice.Audio -> {
                add("-f"); add(if (choice.format == AudioFormat.M4A) "ba[ext=m4a]/ba/b" else "ba/b")
                add("-x")
                add("--audio-format"); add(choice.format.extension)
                add("--audio-quality"); add("0")
                add("--embed-metadata")
            }
            FormatChoice.Original -> {
                add("-f"); add("b/bv*+ba")
            }
        }
    }

    // ------------------------------------------------------------------
    // Saída JSON (--dump-single-json)
    // ------------------------------------------------------------------

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(output: String): ExtractionResult {
        val root = try {
            // A saída pode conter linhas extras antes do JSON.
            val start = output.indexOf('{')
            json.parseToJsonElement(if (start > 0) output.substring(start) else output).jsonObject
        } catch (e: Exception) {
            return ExtractionResult.Failure(ExtractionFailure.UNKNOWN, "Resposta inválida do mecanismo")
        }
        val type = root.string("_type")
        val entries = root["entries"]?.takeIf { it is JsonArray }?.jsonArray
        if (type == "playlist" || type == "multi_video" || entries != null) {
            val list = entries.orEmpty().mapIndexedNotNull { i, element ->
                (element as? JsonObject)?.let { parseEntry(it, i + 1) }
            }
            // Coleção com um único item completo: trata como item único.
            val singleFull = entries?.singleOrNull() as? JsonObject
            if (singleFull != null && singleFull["formats"] is JsonArray) {
                return ExtractionResult.Single(parseMedia(singleFull))
            }
            if (list.isEmpty()) {
                return ExtractionResult.Failure(ExtractionFailure.UNAVAILABLE, "Nenhum item disponível")
            }
            return ExtractionResult.Collection(
                title = root.string("title"),
                webpageUrl = root.string("webpage_url"),
                uploader = root.string("uploader") ?: root.string("channel"),
                entries = list,
            )
        }
        return ExtractionResult.Single(parseMedia(root))
    }

    private fun parseEntry(entry: JsonObject, index: Int): ExtractedEntry {
        val url = listOf(entry.string("url"), entry.string("webpage_url"))
            .firstOrNull { it != null && (it.startsWith("http://") || it.startsWith("https://")) }
        return ExtractedEntry(
            url = url,
            title = entry.string("title"),
            durationSeconds = entry.number("duration")?.toLong(),
            thumbnailUrl = thumbnailOf(entry),
            index = entry.number("playlist_index")?.toInt() ?: index,
        )
    }

    internal fun parseMedia(media: JsonObject): ExtractedMedia {
        val formats = media["formats"]?.takeIf { it is JsonArray }?.jsonArray
            ?.mapNotNull { it as? JsonObject }
            .orEmpty()

        data class Fmt(val res: Int?, val hasVideo: Boolean, val hasAudio: Boolean, val size: Long?)

        fun codecPresent(value: String?) = value != null && value != "none"
        val parsed = formats.map { f ->
            val width = f.number("width")?.toInt()
            val height = f.number("height")?.toInt()
            val res = when {
                width != null && height != null -> minOf(width, height)
                else -> height
            }
            val vcodec = f.string("vcodec")
            val acodec = f.string("acodec")
            // Alguns extratores não informam codecs; nesse caso, deduz pela resolução.
            val hasVideo = codecPresent(vcodec) || (vcodec == null && res != null)
            val hasAudio = codecPresent(acodec) || (acodec == null && res == null)
            Fmt(res, hasVideo, hasAudio, f.number("filesize")?.toLong() ?: f.number("filesize_approx")?.toLong())
        }

        val topWidth = media.number("width")?.toInt()
        val topHeight = media.number("height")?.toInt()
        val topRes = if (topWidth != null && topHeight != null) minOf(topWidth, topHeight) else topHeight

        val videoFormats = parsed.filter { it.hasVideo && it.res != null }
        val resolutions = (videoFormats.mapNotNull { it.res } + listOfNotNull(topRes))
            .filter { it > 0 }
            .distinct()
            .sortedDescending()

        val audioOnly = parsed.filter { it.hasAudio && !it.hasVideo }
        val bestAudioSize = audioOnly.mapNotNull { it.size }.maxOrNull()
        val sizeByResolution = resolutions.associateWith { res ->
            val videoSize = videoFormats.filter { it.res == res }.mapNotNull { it.size }.maxOrNull()
            when {
                videoSize == null -> null
                videoFormats.any { it.res == res && it.hasAudio } -> videoSize
                else -> videoSize + (bestAudioSize ?: 0)
            }
        }.filterValues { it != null }.mapValues { it.value!! }

        val vcodecTop = media.string("vcodec")
        val hasVideo = resolutions.isNotEmpty() || codecPresent(vcodecTop)
        val hasAudio = parsed.any { it.hasAudio } || codecPresent(media.string("acodec")) || formats.isEmpty()

        return ExtractedMedia(
            id = media.string("id"),
            title = media.string("title") ?: media.string("fulltitle") ?: media.string("id") ?: "video",
            webpageUrl = media.string("webpage_url") ?: media.string("original_url"),
            extractor = media.string("extractor_key") ?: media.string("extractor"),
            uploader = media.string("uploader") ?: media.string("channel") ?: media.string("creator"),
            durationSeconds = media.number("duration")?.toLong(),
            thumbnailUrl = thumbnailOf(media),
            resolutions = resolutions,
            hasVideo = hasVideo,
            hasAudio = hasAudio,
            sizeByResolution = sizeByResolution,
            audioSizeBytes = bestAudioSize ?: if (!hasVideo) media.number("filesize")?.toLong() else null,
        )
    }

    private fun thumbnailOf(obj: JsonObject): String? {
        obj.string("thumbnail")?.let { return it }
        val thumbs = obj["thumbnails"]?.takeIf { it is JsonArray }?.jsonArray ?: return null
        return thumbs.mapNotNull { (it as? JsonObject)?.string("url") }.lastOrNull()
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.number(key: String): Double? {
        val element: JsonElement = this[key] ?: return null
        val primitive = element as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.doubleOrNull ?: primitive.longOrNull?.toDouble()
    }

    // ------------------------------------------------------------------
    // Progresso
    // ------------------------------------------------------------------

    data class Progress(val percent: Float, val totalBytes: Long?, val bytesPerSecond: Long?)

    private val PROGRESS = Regex(
        """\[download]\s+([\d.]+)%\s+of\s+~?\s*([\d.]+)\s*([KMGT]?i?B)(?:\s+at\s+([\d.]+)\s*([KMGT]?i?B)/s)?""",
    )

    /** Interpreta uma linha como "[download]  45.3% of ~ 12.34MiB at 1.23MiB/s ETA 00:10". */
    fun parseProgress(line: String): Progress? {
        val m = PROGRESS.find(line) ?: return null
        val percent = m.groupValues[1].toFloatOrNull() ?: return null
        val total = toBytes(m.groupValues[2], m.groupValues[3])
        val speed = m.groupValues[4].takeIf { it.isNotEmpty() }?.let { toBytes(it, m.groupValues[5]) }
        return Progress(percent.coerceIn(0f, 100f), total, speed)
    }

    private fun toBytes(value: String, unit: String): Long? {
        val number = value.toDoubleOrNull() ?: return null
        val factor = when (unit.uppercase().replace("I", "")) {
            "B" -> 1.0
            "KB" -> 1024.0
            "MB" -> 1024.0 * 1024
            "GB" -> 1024.0 * 1024 * 1024
            "TB" -> 1024.0 * 1024 * 1024 * 1024
            else -> return null
        }
        return (number * factor).toLong()
    }

    // ------------------------------------------------------------------
    // Erros
    // ------------------------------------------------------------------

    /** Classifica a mensagem de erro do yt-dlp em um motivo compreensível. */
    fun classifyError(message: String?): ExtractionFailure {
        val m = message?.lowercase() ?: return ExtractionFailure.UNKNOWN
        return when {
            "unsupported url" in m -> ExtractionFailure.UNSUPPORTED
            "drm" in m -> ExtractionFailure.DRM
            "private video" in m || "this video is private" in m || "is private" in m ||
                "private account" in m -> ExtractionFailure.PRIVATE
            "login required" in m || "log in" in m || "sign in" in m || "--cookies" in m ||
                "cookies" in m || "confirm your age" in m || "age-restricted" in m ||
                "inappropriate for some users" in m -> ExtractionFailure.LOGIN_REQUIRED
            "not available in your country" in m || "geo restrict" in m || "geo-restrict" in m ->
                ExtractionFailure.GEO_BLOCKED
            "http error 429" in m || "too many requests" in m || "rate-limit" in m || "rate limit" in m ->
                ExtractionFailure.RATE_LIMITED
            "video unavailable" in m || "has been removed" in m || "does not exist" in m ||
                "http error 404" in m || "not found" in m || "no video formats found" in m ||
                "there is no video in this post" in m -> ExtractionFailure.UNAVAILABLE
            "unable to download webpage" in m || "timed out" in m || "name or service not known" in m ||
                "temporary failure in name resolution" in m || "network is unreachable" in m ||
                "connection" in m || "ssl" in m -> ExtractionFailure.NETWORK
            else -> ExtractionFailure.UNKNOWN
        }
    }
}
