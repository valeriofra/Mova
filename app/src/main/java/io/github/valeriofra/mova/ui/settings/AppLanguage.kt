package io.github.valeriofra.mova.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList

/** Idiomas do app. O nome aparece sempre no próprio idioma. */
enum class AppLanguage(val tag: String?, val nativeName: String?) {
    SYSTEM(null, null),
    PORTUGUESE("pt-BR", "Português"),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français");

    companion object {
        /** A escolha de idioma por app existe a partir do Android 13. */
        val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        fun current(context: Context): AppLanguage {
            if (!supported) return SYSTEM
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales == null || locales.isEmpty) return SYSTEM
            val language = locales[0].language
            return entries.firstOrNull { it.tag != null && it.tag.substringBefore('-') == language } ?: SYSTEM
        }

        /** Aplica o idioma (o Android recria a tela automaticamente). */
        fun apply(context: Context, language: AppLanguage) {
            if (!supported) return
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
        }
    }
}
