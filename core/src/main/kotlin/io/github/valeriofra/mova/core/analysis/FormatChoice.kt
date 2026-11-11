package io.github.valeriofra.mova.core.analysis

/** Formato de áudio para extração. */
enum class AudioFormat(val extension: String, val mimeType: String, val label: String) {
    MP3("mp3", "audio/mpeg", "MP3"),
    M4A("m4a", "audio/mp4", "M4A"),
}

/** O que o usuário escolheu baixar de um conteúdo. */
sealed interface FormatChoice {
    /** O arquivo como está na origem (links diretos). */
    data object Original : FormatChoice

    /** Vídeo com áudio, na maior resolução até [maxResolution] (menor lado, ex.: 1080). */
    data class Video(val maxResolution: Int) : FormatChoice

    /** Somente o áudio, convertido para [format]. */
    data class Audio(val format: AudioFormat) : FormatChoice

    /** Representação em texto, para guardar no banco. */
    fun encode(): String = when (this) {
        Original -> "original"
        is Video -> "video:$maxResolution"
        is Audio -> "audio:${format.extension}"
    }

    companion object {
        /** Qualidade máxima prevista no planejamento. */
        const val MAX_RESOLUTION = 1080

        /** Resoluções oferecidas quando a origem não informa as disponíveis. */
        val STANDARD_RESOLUTIONS = listOf(1080, 720, 480, 360)

        fun decode(value: String?): FormatChoice {
            if (value.isNullOrBlank()) return Original
            val (type, arg) = value.split(':', limit = 2).let { it[0] to it.getOrNull(1) }
            return when (type) {
                "video" -> Video(arg?.toIntOrNull()?.coerceAtMost(MAX_RESOLUTION) ?: MAX_RESOLUTION)
                "audio" -> Audio(AudioFormat.entries.firstOrNull { it.extension == arg } ?: AudioFormat.MP3)
                else -> Original
            }
        }
    }
}
