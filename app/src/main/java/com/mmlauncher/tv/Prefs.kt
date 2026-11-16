package com.mmlauncher.tv

import android.content.Context

/** Launcher settings in plain SharedPreferences (lighter than DataStore/Room). */
class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("mm_launcher", Context.MODE_PRIVATE)

    /** Ordered list of favorite package names. */
    var favorites: List<String>
        get() = sp.getString(KEY_FAV, "").orEmpty().split('\n').filter { it.isNotEmpty() }
        set(value) { sp.edit().putString(KEY_FAV, value.joinToString("\n")).apply() }

    /** Package names hidden from the lists. */
    var hidden: Set<String>
        get() = sp.getString(KEY_HIDDEN, "").orEmpty().split('\n').filter { it.isNotEmpty() }.toSet()
        set(value) { sp.edit().putString(KEY_HIDDEN, value.joinToString("\n")).apply() }

    /** "system", "ru", "tk" or "en". The launcher language is independent of the system one. */
    var language: String
        get() = sp.getString("language", "system").orEmpty()
        set(value) { sp.edit().putString("language", value).apply() }

    /** "none", "night", "ocean", "violet", "sunset" or "custom". */
    var wallpaper: String
        get() = sp.getString("wallpaper", "none").orEmpty()
        set(value) { sp.edit().putString("wallpaper", value).apply() }

    /** Package to launch after boot; empty = autostart off. */
    var autostartPkg: String
        get() = sp.getString("as_pkg", "").orEmpty()
        set(value) { sp.edit().putString("as_pkg", value).apply() }

    /** Seconds to wait (and allow cancelling) before autostart: 0..10. */
    var autostartDelay: Int
        get() = sp.getInt("as_delay", 3)
        set(value) { sp.edit().putInt("as_delay", value.coerceIn(0, 10)).apply() }

    /** Boot id during which autostart already ran (or was set up). */
    var autostartBoot: Long
        get() = sp.getLong("as_boot", -1L)
        set(value) { sp.edit().putLong("as_boot", value).apply() }

    /** First-run wizard finished (or skipped). */
    var wizardDone: Boolean
        get() = sp.getBoolean("wizard_done", false)
        set(value) { sp.edit().putBoolean("wizard_done", value).apply() }

    /** User consent: check for updates automatically (at most once a day). Off by default. */
    var autoUpdates: Boolean
        get() = sp.getBoolean("auto_updates", false)
        set(value) { sp.edit().putBoolean("auto_updates", value).apply() }

    var updateUrl: String
        get() = sp.getString("update_url", Config.DEFAULT_UPDATE_URL).orEmpty()
        set(value) { sp.edit().putString("update_url", value).apply() }

    var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0L)
        set(value) { sp.edit().putLong("last_update_check", value).apply() }

    /** Version of the downloaded settings-chains config (FR-10). */
    var chainsVersion: Int
        get() = sp.getInt("chains_version", 0)
        set(value) { sp.edit().putInt("chains_version", value).apply() }

    /** Raw update.json entry of a newer app version found earlier; empty if none. */
    var availableUpdate: String
        get() = sp.getString("available_update", "").orEmpty()
        set(value) { sp.edit().putString("available_update", value).apply() }

    private companion object {
        const val KEY_FAV = "favorites"
        const val KEY_HIDDEN = "hidden"
    }
}
