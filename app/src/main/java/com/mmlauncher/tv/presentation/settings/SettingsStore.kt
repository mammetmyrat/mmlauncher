package com.mmlauncher.tv.presentation.settings

import com.mmlauncher.tv.R
import com.mmlauncher.tv.core.MviStore
import com.mmlauncher.tv.core.Scheduler
import com.mmlauncher.tv.domain.model.SystemScreen
import com.mmlauncher.tv.domain.model.UpdateCheck
import com.mmlauncher.tv.domain.usecase.*
import com.mmlauncher.tv.presentation.common.Hints
import com.mmlauncher.tv.presentation.common.msg

class SettingsStore(
    scheduler: Scheduler,
    private val load: LoadLauncherSettings,
    private val changeLanguage: ChangeLanguage,
    private val changeWallpaper: ChangeWallpaper,
    private val autostart: ConfigureAutostart,
    private val setAutoUpdates: SetAutoUpdates,
    private val checkUpdatesNow: CheckUpdatesNow,
    private val clearCache: ClearLauncherCache,
    private val listApps: ListInstalledApps,
    private val openSystemScreen: OpenSystemScreen,
    private val setHomeViaAdb: SetDefaultHomeViaAdb,
    private val openHomeSettings: OpenHomeSettings,
    private val replaceStock: ReplaceStockLauncher,
    private val restoreStock: RestoreStockLauncher,
) : MviStore<SettingsState, SettingsIntent, SettingsEffect>(SettingsState(), scheduler) {

    override fun handle(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.Load -> reload()
            is SettingsIntent.SelectLanguage -> if (changeLanguage(intent.language)) emit(SettingsEffect.Recreate)
            is SettingsIntent.SelectWallpaper -> { changeWallpaper(intent.wallpaper); reload() }
            is SettingsIntent.WallpaperPicked -> io({ changeWallpaper.fromUri(intent.uri) }) { ok ->
                if (ok) reload() else emit(SettingsEffect.Toast(msg(R.string.wallpaper_failed)))
            }
            SettingsIntent.ChooseAutostartApp -> io({ listApps() }) { apps ->
                if (apps.isNotEmpty()) emit(SettingsEffect.ShowAppPicker(apps))
            }
            is SettingsIntent.AutostartAppChosen -> { autostart.choose(intent.packageName); reload() }
            SettingsIntent.AutostartOff -> { autostart.off(); reload() }
            is SettingsIntent.SetAutostartDelay -> { autostart.setDelay(intent.seconds); reload() }
            SettingsIntent.ToggleAutoUpdates -> {
                setAutoUpdates(!(state.settings?.autoUpdates ?: false)); reload()
            }
            SettingsIntent.CheckNow -> checkNow()
            SettingsIntent.ClearCache -> io({ clearCache() }) {
                emit(SettingsEffect.CacheCleared)
                emit(SettingsEffect.Toast(msg(R.string.cache_cleared)))
            }
            SettingsIntent.OpenStorageScreen -> openScreen(SystemScreen.STORAGE)
            SettingsIntent.OpenAppsScreen -> openScreen(SystemScreen.APPS)
            SettingsIntent.Refresh -> reload()
            SettingsIntent.ReplaceStockRequested ->
                emit(SettingsEffect.ConfirmReplaceStock(state.settings?.stockLaunchers.orEmpty()))
            SettingsIntent.ReplaceStockConfirmed -> {
                emit(SettingsEffect.Toast(msg(R.string.adb_working)))
                network({ replaceStock() }) { isHome ->
                    reload()
                    emit(SettingsEffect.Toast(
                        if (isHome) msg(R.string.home_replaced) else msg(R.string.home_replace_failed, Hints.ENABLE_TCPIP)
                    ))
                }
            }
            SettingsIntent.RestoreStock -> {
                emit(SettingsEffect.Toast(msg(R.string.adb_working)))
                network({ restoreStock() }) { ok ->
                    reload()
                    emit(SettingsEffect.Toast(if (ok) msg(R.string.home_restored) else msg(R.string.adb_unavailable, Hints.ENABLE_TCPIP)))
                }
            }
            SettingsIntent.OpenHomeSettings ->
                if (!openHomeSettings()) emit(SettingsEffect.Toast(msg(R.string.cant_open_screen)))
            SettingsIntent.SetHomeViaAdb -> {
                emit(SettingsEffect.Toast(msg(R.string.adb_working)))
                network({ setHomeViaAdb() }) { ok ->
                    reload()
                    emit(SettingsEffect.Toast(if (ok) msg(R.string.dev_changed) else msg(R.string.adb_unavailable, Hints.ENABLE_TCPIP)))
                }
            }
        }
    }

    private fun reload() = setState { copy(settings = load()) }

    private fun openScreen(screen: String) {
        if (!openSystemScreen(screen)) emit(SettingsEffect.Toast(msg(R.string.cant_open_screen)))
    }

    /** A manual check is an explicit user action, so it does not depend on the auto-check consent. */
    private fun checkNow() {
        emit(SettingsEffect.Toast(msg(R.string.upd_checking)))
        network({ checkUpdatesNow() }) { result ->
            val m = when (result) {
                is UpdateCheck.Available -> msg(R.string.upd_available, result.info.versionName)
                UpdateCheck.UpToDate -> msg(R.string.upd_latest)
                UpdateCheck.Failed -> msg(R.string.upd_failed)
            }
            emit(SettingsEffect.Toast(m))
        }
    }
}
