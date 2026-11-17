package com.mmlauncher.tv.presentation.common

import android.content.Context
import android.content.res.Configuration
import com.mmlauncher.tv.appContainer
import com.mmlauncher.tv.domain.model.Language
import java.util.Locale

/** FR-29: in-app language, independent of the system locale (many boxes have no Turkmen locale). */
object LocaleHelper {
    fun wrap(base: Context): Context {
        val lang = base.appContainer.preferences.language
        if (lang == Language.SYSTEM) return base
        val locale = Locale.forLanguageTag(lang.code)
        Locale.setDefault(locale) // so TextClock date names follow the chosen language too
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
