package com.flightradius.app.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.flightradius.app.ui.format.Words
import java.util.Locale

/** Languages offered in Settings; [tag] null = follow the phone. */
enum class AppLanguageOption(val tag: String?) {
    SYSTEM(null), ENGLISH("en"), SPANISH("es");

    companion object {
        /** Maps a BCP-47 tag ("es", "es-ES", …) to an option; unknown tags mean [SYSTEM]. */
        fun fromTag(tag: String?): AppLanguageOption {
            val language = tag?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { Locale.forLanguageTag(it).language } ?: return SYSTEM
            return entries.firstOrNull { it.tag == language } ?: SYSTEM
        }
    }
}

/**
 * Per-app language behind one API.
 * - API 33+: the framework `LocaleManager` (shared with Android's own
 *   per-app language page; it recreates activities itself).
 * - API 26–32: a SharedPreferences tag applied in `attachBaseContext`
 *   (read synchronously, so no DataStore), plus an activity recreate.
 */
object AppLanguage {
    private const val PREFS = "locale_prefs"
    private const val KEY_TAG = "tag"

    /** The Locale for a stored/selected tag; null = system. Pure. */
    fun localeFor(tag: String?): Locale? =
        tag?.takeIf { it.isNotBlank() }?.let { Locale.forLanguageTag(it) }

    fun current(context: Context): AppLanguageOption =
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.isEmpty) AppLanguageOption.SYSTEM
            else AppLanguageOption.fromTag(locales[0].toLanguageTag())
        } else {
            AppLanguageOption.fromTag(prefs(context).getString(KEY_TAG, null))
        }

    fun set(context: Context, option: AppLanguageOption) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                option.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
            // The framework recreates activities and dispatches a configuration
            // change; FlightRadiusApp.onConfigurationChanged refreshes Words.
        } else {
            val editor = prefs(context).edit()
            if (option.tag == null) editor.remove(KEY_TAG) else editor.putString(KEY_TAG, option.tag)
            editor.commit()
            val app = context.applicationContext
            Locale.setDefault(
                localeFor(option.tag) ?: Configuration(app.resources.configuration).locales[0])
            Words.resources = wrap(app).resources
            (context as? Activity)?.recreate()
        }
    }

    /**
     * API 26–32 only: a context whose resources use the chosen language.
     * Used from `attachBaseContext` of the Application and the activity.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val locale = localeFor(prefs(base).getString(KEY_TAG, null)) ?: return base
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
