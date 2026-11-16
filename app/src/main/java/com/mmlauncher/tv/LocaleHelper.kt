package com.mmlauncher.tv

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** FR-29: in-app language, independent of the system locale (many boxes have no Turkmen locale). */
object LocaleHelper {

    fun wrap(base: Context): Context {
        val lang = Prefs(base).language
        if (lang == "system") return base
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale) // so TextClock date names follow the chosen language too
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
