package com.mmlauncher.tv.presentation.settings

import com.mmlauncher.tv.domain.model.AppInfo
import com.mmlauncher.tv.domain.model.Language
import com.mmlauncher.tv.domain.model.Wallpaper
import com.mmlauncher.tv.domain.usecase.LauncherSettings
import com.mmlauncher.tv.presentation.common.Msg

data class SettingsState(val settings: LauncherSettings? = null)

sealed interface SettingsIntent {
    object Load : SettingsIntent
    data class SelectLanguage(val language: Language) : SettingsIntent
    data class SelectWallpaper(val wallpaper: Wallpaper) : SettingsIntent
    data class WallpaperPicked(val uri: String) : SettingsIntent
    object ChooseAutostartApp : SettingsIntent
    data class AutostartAppChosen(val packageName: String) : SettingsIntent
    object AutostartOff : SettingsIntent
    data class SetAutostartDelay(val seconds: Int) : SettingsIntent
    object ToggleAutoUpdates : SettingsIntent
    object CheckNow : SettingsIntent
    object ClearCache : SettingsIntent
    object OpenStorageScreen : SettingsIntent
    object OpenAppsScreen : SettingsIntent
}

sealed interface SettingsEffect {
    data class Toast(val msg: Msg) : SettingsEffect
    /** Language changed: rebuild the screen so the new locale applies. */
    object Recreate : SettingsEffect
    data class ShowAppPicker(val apps: List<AppInfo>) : SettingsEffect
    object CacheCleared : SettingsEffect
}
