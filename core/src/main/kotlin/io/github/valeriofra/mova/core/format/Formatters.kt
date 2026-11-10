package io.github.valeriofra.mova.core.format

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Formatação de tamanhos e velocidades para exibição (padrão pt-BR). */
object Formatters {

    private val units = listOf("B", "KB", "MB", "GB", "TB")

    fun bytes(value: Long, locale: Locale = Locale.forLanguageTag("pt-BR")): String {
        if (value < 1024) return "$value B"
        var size = value.toDouble()
        var unit = 0
        while (size >= 1024 && unit < units.lastIndex) {
            size /= 1024
            unit++
        }
        val pattern = if (size >= 100) "0" else "0.0"
        val format = DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale))
        return "${format.format(size)} ${units[unit]}"
    }

    fun speed(bytesPerSecond: Long, locale: Locale = Locale.forLanguageTag("pt-BR")): String =
        "${bytes(bytesPerSecond, locale)}/s"

    /** Percentual inteiro (0–100) ou null quando o total é desconhecido. */
    fun percent(done: Long, total: Long?): Int? {
        if (total == null || total <= 0) return null
        return ((done.coerceAtMost(total) * 100) / total).toInt()
    }

    /** Tempo restante aproximado, ex.: "2 min" ou "35 s". */
    fun remaining(done: Long, total: Long?, bytesPerSecond: Long): String? {
        if (total == null || total <= 0 || bytesPerSecond <= 0) return null
        val seconds = (total - done).coerceAtLeast(0) / bytesPerSecond
        return when {
            seconds < 60 -> "$seconds s"
            seconds < 3600 -> "${seconds / 60} min"
            else -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
        }
    }
}
