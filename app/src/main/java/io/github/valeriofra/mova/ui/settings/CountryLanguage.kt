package io.github.valeriofra.mova.ui.settings

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.telephony.TelephonyManager
import io.github.valeriofra.mova.core.i18n.LanguageFallback
import java.util.Locale

/**
 * Aplica o idioma do país quando o do aparelho não tem tradução no MOVA
 * (ver [LanguageFallback]). O país vem do chip, da rede móvel ou da região
 * configurada no aparelho, nessa ordem.
 */
object CountryLanguage {

    /** O país do chip/rede quase nunca muda durante o uso; evita consultá-lo a cada texto. */
    @Volatile
    private var phoneCountry: String? = null

    /** Contexto com o idioma do país acrescentado à lista, ou o próprio [context] se não for preciso. */
    fun localize(context: Context): Context {
        val config = context.resources.configuration
        val locales = config.locales
        val current = (0 until locales.size()).map { locales[it] }
        val country = countryOf(context, current.firstOrNull())
        val language = LanguageFallback.fallbackFor(current.map { it.toLanguageTag() }, country) ?: return context
        val fallback = Locale.Builder().setLanguage(language).setRegion(country).build()
        val updated = Configuration(config).apply { setLocales(LocaleList(*(current + fallback).toTypedArray())) }
        return context.createConfigurationContext(updated)
    }

    private fun countryOf(context: Context, deviceLocale: Locale?): String? {
        val fromPhone = phoneCountry ?: readPhoneCountry(context)?.also { phoneCountry = it }
        return fromPhone ?: deviceLocale?.country?.takeIf { it.length == 2 }?.uppercase(Locale.ROOT)
    }

    private fun readPhoneCountry(context: Context): String? {
        val telephony = runCatching { context.getSystemService(TelephonyManager::class.java) }.getOrNull() ?: return null
        return listOf(
            runCatching { telephony.simCountryIso }.getOrNull(),
            runCatching { telephony.networkCountryIso }.getOrNull(),
        ).firstOrNull { it != null && it.length == 2 }?.uppercase(Locale.ROOT)
    }
}
