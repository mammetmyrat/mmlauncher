package com.mmlauncher.tv.di

import android.content.Context
import com.mmlauncher.tv.core.AndroidScheduler
import com.mmlauncher.tv.core.Scheduler
import com.mmlauncher.tv.data.apps.PackageAppsRepository
import com.mmlauncher.tv.data.diagnostics.AdbDiagnosticsRepository
import com.mmlauncher.tv.data.files.FilesRepositoryImpl
import com.mmlauncher.tv.data.files.WallpaperStorage
import com.mmlauncher.tv.data.prefs.SharedPrefsRepository
import com.mmlauncher.tv.data.system.AndroidDeveloperRepository
import com.mmlauncher.tv.data.system.AndroidSystemRepository
import com.mmlauncher.tv.data.system.AndroidSystemScreens
import com.mmlauncher.tv.data.update.HttpUpdateRepository
import com.mmlauncher.tv.domain.usecase.*
import com.mmlauncher.tv.presentation.developer.DeveloperStore
import com.mmlauncher.tv.presentation.files.FilesStore
import com.mmlauncher.tv.presentation.home.HomeStore
import com.mmlauncher.tv.presentation.settings.SettingsStore
import com.mmlauncher.tv.presentation.usage.RamStore
import com.mmlauncher.tv.presentation.usage.StorageStore
import com.mmlauncher.tv.presentation.wizard.WizardStore

/**
 * Manual dependency injection (no Hilt/Dagger: fewer classes and less RAM on weak boxes).
 * One instance lives in [com.mmlauncher.tv.MMApp]; Activities ask it for their stores.
 */
class AppContainer(context: Context) {

    private val app = context.applicationContext

    val scheduler: Scheduler = AndroidScheduler()

    // ---- data ---------------------------------------------------------------------------------
    val preferences = SharedPrefsRepository(app)
    private val apps = PackageAppsRepository(app)
    private val system = AndroidSystemRepository(app)
    private val screens = AndroidSystemScreens(app)
    private val developer = AndroidDeveloperRepository(app)
    private val diagnostics = AdbDiagnosticsRepository(app)
    private val updates = HttpUpdateRepository(app, preferences, preferences.sp)
    private val files = FilesRepositoryImpl(app)
    val wallpaperStorage = WallpaperStorage(app)

    // ---- use cases ----------------------------------------------------------------------------
    private val loadHomeApps = LoadHomeApps(apps, preferences)
    private val favorites = ManageFavorites(preferences)
    private val loadSettings = LoadLauncherSettings(preferences, apps, system)
    private val changeWallpaper = ChangeWallpaper(preferences, wallpaperStorage)
    private val openSystemScreen = OpenSystemScreen(screens)
    private val devRepoForUseCases = developer

    // ---- stores (one per Activity instance) -----------------------------------------------------
    fun homeStore() = HomeStore(
        scheduler, loadHomeApps, LoadHomeSources(system, updates), loadSettings, ReadRam(system),
        LaunchApp(apps), favorites, ManageHidden(preferences), BuildAppMenu(favorites, apps),
        ResolveAutostart(preferences, system), AutoCheckUpdates(preferences, updates, system),
        InstallUpdate(updates), openSystemScreen, OpenAppInfo(screens), UninstallApp(screens),
        OpenTvInput(screens), IsWizardDone(preferences),
    )

    fun settingsStore() = SettingsStore(
        scheduler, loadSettings, ChangeLanguage(preferences), changeWallpaper,
        ConfigureAutostart(preferences, system), SetAutoUpdates(preferences), CheckUpdatesNow(updates),
        ClearLauncherCache(system), ListInstalledApps(apps), openSystemScreen,
    )

    fun wizardStore() = WizardStore(
        scheduler, loadSettings, ChangeLanguage(preferences), changeWallpaper, SetAutoUpdates(preferences),
        IsDefaultHome(system), OpenHomeSettings(screens), FinishWizard(preferences),
    )

    fun developerStore() = DeveloperStore(
        scheduler, GetDeveloperStatus(devRepoForUseCases), ToggleDeveloperMode(devRepoForUseCases),
        ToggleUsbDebugging(devRepoForUseCases), GrantWriteSecureViaAdb(diagnostics, devRepoForUseCases),
        diagnostics, openSystemScreen,
    )

    fun ramStore() = RamStore(
        scheduler, GetRamUsage(system, diagnostics), BoostMemory(system), StopApp(diagnostics), app.packageName,
    )

    fun storageStore() = StorageStore(scheduler, GetStorageUsage(system, diagnostics), OpenAppInfo(screens), openSystemScreen)

    fun filesStore() = FilesStore(
        scheduler, HasFileAccess(files), ListDirectory(files), OpenFile(files), IsImageFile(files),
        FileRoots(files), changeWallpaper,
    )
}
