package com.mmlauncher.tv.domain.usecase

import com.mmlauncher.tv.domain.model.*
import com.mmlauncher.tv.domain.repository.*

/** Launcher settings as one snapshot. */
data class LauncherSettings(
    val language: Language,
    val wallpaper: Wallpaper,
    val autostartPackage: String,
    val autostartLabel: String?,
    val autostartDelaySeconds: Int,
    val autoUpdates: Boolean,
    val versionName: String,
    val isDefaultHome: Boolean,
    val currentHome: String?,
    val stockLaunchers: List<String>,
    val hasDisabledLaunchers: Boolean,
)

class LoadLauncherSettings(
    private val prefs: PreferencesRepository,
    private val apps: AppsRepository,
    private val system: SystemRepository,
) {
    operator fun invoke() = LauncherSettings(
        language = prefs.language,
        wallpaper = prefs.wallpaper,
        autostartPackage = prefs.autostartPackage,
        autostartLabel = prefs.autostartPackage.takeIf { it.isNotEmpty() }?.let { apps.label(it) },
        autostartDelaySeconds = prefs.autostartDelaySeconds,
        autoUpdates = prefs.autoUpdates,
        versionName = system.versionName(),
        isDefaultHome = system.isDefaultHome(),
        currentHome = system.currentHomeLabel(),
        stockLaunchers = system.stockHomePackages(),
        hasDisabledLaunchers = prefs.disabledLaunchers.isNotEmpty(),
    )
}

class ChangeLanguage(private val prefs: PreferencesRepository) {
    /** Returns true if the language actually changed (the screen must be recreated). */
    operator fun invoke(language: Language): Boolean {
        if (prefs.language == language) return false
        prefs.language = language
        return true
    }
}

class ChangeWallpaper(private val prefs: PreferencesRepository, private val wallpapers: WallpaperRepository) {
    operator fun invoke(wallpaper: Wallpaper) { prefs.wallpaper = wallpaper }

    /** Copies an image into private storage and selects it. */
    fun fromUri(uri: String): Boolean = wallpapers.saveFromUri(uri).also { if (it) prefs.wallpaper = Wallpaper.CUSTOM }
    fun fromFile(path: String): Boolean = wallpapers.saveFromFile(path).also { if (it) prefs.wallpaper = Wallpaper.CUSTOM }
}

class ConfigureAutostart(private val prefs: PreferencesRepository, private val system: SystemRepository) {
    fun choose(pkg: String) {
        prefs.autostartPackage = pkg
        // Do not fire right now: the current boot counts as already handled.
        prefs.autostartDoneBoot = system.bootId()
    }
    fun off() { prefs.autostartPackage = "" }
    fun setDelay(seconds: Int) { prefs.autostartDelaySeconds = seconds.coerceIn(0, 10) }
}

class SetAutoUpdates(private val prefs: PreferencesRepository) {
    operator fun invoke(on: Boolean) { prefs.autoUpdates = on }
}

class CheckUpdatesNow(private val updates: UpdateRepository) {
    /** A manual check is an explicit user action, so it does not need the auto-check consent. */
    operator fun invoke(): UpdateCheck = updates.check()
}

class ClearLauncherCache(private val system: SystemRepository) {
    operator fun invoke() = system.clearCaches()
}

class ListInstalledApps(private val apps: AppsRepository) {
    operator fun invoke(): List<AppInfo> = apps.installed()
}

class FinishWizard(private val prefs: PreferencesRepository) {
    operator fun invoke() { prefs.wizardDone = true }
}

class IsDefaultHome(private val system: SystemRepository) {
    operator fun invoke() = system.isDefaultHome()
}

class OpenHomeSettings(private val screens: SystemScreens) {
    operator fun invoke() = screens.openHomeSettings()
}

class IsWizardDone(private val prefs: PreferencesRepository) {
    operator fun invoke() = prefs.wizardDone
}

/**
 * Google TV and many TV firmwares keep their own launcher on the Home button no matter what the user
 * chooses. The reliable way is to switch that launcher off (reversible with [RestoreStockLauncher]).
 */
class ReplaceStockLauncher(
    private val diag: DiagnosticsRepository,
    private val system: SystemRepository,
    private val prefs: PreferencesRepository,
) {
    /** True if this launcher answers Home afterwards. */
    operator fun invoke(): Boolean {
        val stock = system.stockHomePackages()
        if (stock.isEmpty()) return system.isDefaultHome()
        val done = diag.setLaunchersEnabled(stock, false)
        if (done) prefs.disabledLaunchers = prefs.disabledLaunchers + stock
        return system.isDefaultHome()
    }
}

class RestoreStockLauncher(private val diag: DiagnosticsRepository, private val prefs: PreferencesRepository) {
    /** True if the launchers were switched back on. */
    operator fun invoke(): Boolean {
        val disabled = prefs.disabledLaunchers.toList()
        if (disabled.isEmpty()) return false
        val ok = diag.setLaunchersEnabled(disabled, true)
        if (ok) prefs.disabledLaunchers = emptySet()
        return ok
    }
}
