package io.github.remdee13.pavlovtv.data

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * Per-app language. Stored in plain SharedPreferences because it has to be read
 * synchronously in attachBaseContext, before any coroutine machinery exists.
 */
object AppLocale {
    /** "" means "follow the TV's language". */
    val supported = listOf("", "en", "ru", "es", "tr", "de", "fr", "pt")

    val nativeNames = mapOf(
        "en" to "English",
        "ru" to "Русский",
        "es" to "Español",
        "tr" to "Türkçe",
        "de" to "Deutsch",
        "fr" to "Français",
        "pt" to "Português",
    )

    private const val PREFS = "locale"
    private const val KEY = "tag"

    fun stored(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun store(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
    }

    /** Two-letter language actually used for UI, news and geocoding. */
    fun effectiveLanguage(context: Context): String {
        val tag = stored(context)
        val language = if (tag.isNotEmpty()) tag else Resources.getSystem().configuration.locales[0].language
        return if (language in nativeNames) language else "en"
    }

    fun effectiveLocale(context: Context): Locale {
        val tag = stored(context)
        return if (tag.isNotEmpty()) Locale.forLanguageTag(tag) else Resources.getSystem().configuration.locales[0]
    }

    fun wrap(base: Context): Context {
        val tag = stored(base)
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
