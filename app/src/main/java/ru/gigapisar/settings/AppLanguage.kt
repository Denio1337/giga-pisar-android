package ru.gigapisar.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Interface language: the phone's own by default, or Russian or English picked in the
 * settings. Kept in plain SharedPreferences, not DataStore: it is needed synchronously
 * while an activity or the service is being created.
 */
object AppLanguage {
    const val SYSTEM = "system"
    const val RUSSIAN = "ru"
    const val ENGLISH = "en"

    private const val PREFS = "giga_pisar_language"
    private const val KEY = "language"

    fun get(context: Context): String =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, SYSTEM) ?: SYSTEM

    fun set(
        context: Context,
        language: String,
    ) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, language)
            .apply()
    }

    /** The context to take strings from: [base] itself for the system language, else one in the chosen language. */
    fun wrap(base: Context): Context {
        val language = get(base)
        if (language == SYSTEM) return base
        val locale = Locale.forLanguageTag(language)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /** Whether the interface is Russian right now: texts built in code (Brain errors) follow it too. */
    fun isRussian(context: Context): Boolean =
        when (get(context)) {
            RUSSIAN -> true
            ENGLISH -> false
            else -> Locale.getDefault().language == "ru"
        }
}
