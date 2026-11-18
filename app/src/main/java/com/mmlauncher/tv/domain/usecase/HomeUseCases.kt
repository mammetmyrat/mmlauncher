package com.mmlauncher.tv.domain.usecase

import com.mmlauncher.tv.domain.model.*
import com.mmlauncher.tv.domain.repository.*

/** Apps for the two rows: everything not hidden, favorites in the user's order, and the hidden ones. */
data class HomeApps(val visible: List<AppInfo>, val favorites: List<AppInfo>, val hidden: List<AppInfo>)

class LoadHomeApps(private val apps: AppsRepository, private val prefs: PreferencesRepository) {
    /** Queries the package manager (slow); use [from] to re-split an already loaded list after a preference change. */
    fun installed(): List<AppInfo> = apps.installed()

    operator fun invoke(): HomeApps = from(apps.installed())

    fun from(installed: List<AppInfo>): HomeApps {
        val hidden = prefs.hidden
        val all = listOf(AppInfo.FILES) + installed // the built-in Files card comes first
        val visible = all.filter { it.packageName !in hidden }
        val byPackage = visible.associateBy { it.packageName }
        return HomeApps(
            visible = visible,
            favorites = prefs.favorites.mapNotNull { byPackage[it] },
            hidden = all.filter { it.packageName in hidden },
        )
    }
}

class LaunchApp(private val apps: AppsRepository) {
    operator fun invoke(app: AppInfo): Boolean = apps.launch(app)
}

class ManageFavorites(private val prefs: PreferencesRepository) {
    fun add(pkg: String) { if (pkg !in prefs.favorites) prefs.favorites = prefs.favorites + pkg }
    fun remove(pkg: String) { prefs.favorites = prefs.favorites - pkg }

    /** Moves [pkg] by [delta] positions inside the favorites list; false if it cannot move. */
    fun move(pkg: String, delta: Int): Boolean {
        val list = prefs.favorites.toMutableList()
        val from = list.indexOf(pkg)
        val to = from + delta
        if (from < 0 || to !in list.indices) return false
        list[from] = list[to].also { list[to] = list[from] }
        prefs.favorites = list
        return true
    }

    fun isFavorite(pkg: String) = pkg in prefs.favorites
    fun indexOf(pkg: String) = prefs.favorites.indexOf(pkg)
    fun size() = prefs.favorites.size
}

class ManageHidden(private val prefs: PreferencesRepository) {
    fun hide(pkg: String) { prefs.hidden = prefs.hidden + pkg }
    fun unhide(pkg: String) { prefs.hidden = prefs.hidden - pkg }
}

/** What the long-press menu of an app card offers. */
data class AppMenu(
    val app: AppInfo,
    val isFavorite: Boolean,
    val canMoveLeft: Boolean,
    val canMoveRight: Boolean,
    val canUninstall: Boolean,
    val isBuiltIn: Boolean,
)

class BuildAppMenu(private val favorites: ManageFavorites, private val apps: AppsRepository) {
    operator fun invoke(app: AppInfo): AppMenu {
        val idx = favorites.indexOf(app.packageName)
        return AppMenu(
            app = app,
            isFavorite = idx >= 0,
            canMoveLeft = idx > 0,
            canMoveRight = idx >= 0 && idx < favorites.size() - 1,
            canUninstall = app.builtIn == null && apps.canUninstall(app.packageName),
            isBuiltIn = app.builtIn != null,
        )
    }
}

/**
 * FR-17..19: autostart fires once per device boot. Returns the plan only when an app is configured,
 * installed and this boot has not been handled yet; marks the boot as handled right away.
 */
class ResolveAutostart(private val prefs: PreferencesRepository, private val system: SystemRepository) {
    fun isDue(): Boolean = prefs.autostartPackage.isNotEmpty() && prefs.autostartDoneBoot != system.bootId()

    operator fun invoke(installed: List<AppInfo>): AutostartPlan? {
        if (!isDue()) return null
        val app = installed.firstOrNull { it.packageName == prefs.autostartPackage } ?: return null
        prefs.autostartDoneBoot = system.bootId()
        return AutostartPlan(app, prefs.autostartDelaySeconds)
    }
}

class LoadHomeSources(
    private val system: SystemRepository,
    private val updates: UpdateRepository,
) {
    operator fun invoke(): List<HomeSource> = buildList {
        updates.stored()?.let { add(HomeSource.UpdateAvailable(it)) }
        system.tvInputs().forEach { add(HomeSource.Input(it)) }
        system.removableVolumes().forEach { add(HomeSource.Usb(it)) }
    }
}

class ReadRam(private val system: SystemRepository) {
    operator fun invoke(): RamInfo = system.ram()
}

/** Consent-based update check: at most once a day, only if the user allowed it. */
class AutoCheckUpdates(
    private val prefs: PreferencesRepository,
    private val updates: UpdateRepository,
    private val system: SystemRepository,
) {
    /** Returns true if a check actually ran (so the sources row may have changed). */
    operator fun invoke(): Boolean {
        if (!prefs.autoUpdates) return false
        if (system.now() - prefs.lastUpdateCheck < DAY_MS) return false
        updates.check()
        return true
    }
    private companion object { const val DAY_MS = 24L * 60 * 60 * 1000 }
}

class InstallUpdate(private val updates: UpdateRepository) {
    operator fun invoke(update: UpdateInfo): UpdateInstall = updates.downloadAndInstall(update)
}

class OpenSystemScreen(private val screens: SystemScreens) {
    operator fun invoke(screen: String): Boolean = screens.open(screen)
}
