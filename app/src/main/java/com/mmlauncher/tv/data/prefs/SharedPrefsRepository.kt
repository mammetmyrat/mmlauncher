package com.mmlauncher.tv.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.mmlauncher.tv.domain.model.Language
import com.mmlauncher.tv.domain.model.Wallpaper
import com.mmlauncher.tv.domain.repository.PreferencesRepository

/** SharedPreferences (lighter than DataStore/Room). Key names are kept so existing installs keep their data. */
class SharedPrefsRepository(context: Context) : PreferencesRepository {

    val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("mm_launcher", Context.MODE_PRIVATE)

    private fun list(key: String) = sp.getString(key, "").orEmpty().split('\n').filter { it.isNotEmpty() }

    override var favorites: List<String>
        get() = list("favorites")
        set(v) { sp.edit().putString("favorites", v.joinToString("\n")).apply() }

    override var hidden: Set<String>
        get() = list("hidden").toSet()
        set(v) { sp.edit().putString("hidden", v.joinToString("\n")).apply() }

    override var language: Language
        get() = Language.fromCode(sp.getString("language", "system").orEmpty())
        set(v) { sp.edit().putString("language", v.code).apply() }

    override var wallpaper: Wallpaper
        get() = Wallpaper.fromKey(sp.getString("wallpaper", "none").orEmpty())
        set(v) { sp.edit().putString("wallpaper", v.key).apply() }

    override var autostartPackage: String
        get() = sp.getString("as_pkg", "").orEmpty()
        set(v) { sp.edit().putString("as_pkg", v).apply() }

    override var autostartDelaySeconds: Int
        get() = sp.getInt("as_delay", 3)
        set(v) { sp.edit().putInt("as_delay", v.coerceIn(0, 10)).apply() }

    override var autostartDoneBoot: Long
        get() = sp.getLong("as_boot", -1L)
        set(v) { sp.edit().putLong("as_boot", v).apply() }

    override var wizardDone: Boolean
        get() = sp.getBoolean("wizard_done", false)
        set(v) { sp.edit().putBoolean("wizard_done", v).apply() }

    override var autoUpdates: Boolean
        get() = sp.getBoolean("auto_updates", false)
        set(v) { sp.edit().putBoolean("auto_updates", v).apply() }

    override var disabledLaunchers: Set<String>
        get() = list("disabled_launchers").toSet()
        set(v) { sp.edit().putString("disabled_launchers", v.joinToString("\n")).apply() }

    override var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0L)
        set(v) { sp.edit().putLong("last_update_check", v).apply() }
}
