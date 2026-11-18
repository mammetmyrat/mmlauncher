package com.mmlauncher.tv.presentation.home

import com.mmlauncher.tv.domain.model.*
import com.mmlauncher.tv.domain.usecase.AppMenu
import com.mmlauncher.tv.presentation.common.Msg

data class Countdown(val app: AppInfo, val secondsLeft: Int)

data class HomeState(
    val loaded: Boolean = false,
    val language: Language = Language.SYSTEM,
    val wallpaper: Wallpaper = Wallpaper.NONE,
    val apps: List<AppInfo> = emptyList(),
    val favorites: List<AppInfo> = emptyList(),
    val hiddenApps: List<AppInfo> = emptyList(),
    val ram: RamInfo? = null,
    val sources: List<HomeSource> = emptyList(),
    val countdown: Countdown? = null,
)

enum class QuickAction { DEVELOPER, SYSTEM_SETTINGS, RAM, STORAGE, TOOLS, LAUNCHER_SETTINGS }

enum class Screen { DEVELOPER, RAM, STORAGE, TOOLS, SETTINGS, FILES, WIZARD }

sealed interface HomeIntent {
    /** [freshStart] is false when the Activity is recreated (rotation, language change). */
    data class Create(val freshStart: Boolean) : HomeIntent
    object Start : HomeIntent
    object Stop : HomeIntent
    object PackagesChanged : HomeIntent
    object VolumesChanged : HomeIntent
    object HomePressed : HomeIntent
    object CancelCountdown : HomeIntent
    data class Launch(val app: AppInfo) : HomeIntent
    data class MenuRequested(val app: AppInfo) : HomeIntent
    data class AddFavorite(val packageName: String) : HomeIntent
    data class RemoveFavorite(val packageName: String) : HomeIntent
    data class MoveFavorite(val packageName: String, val delta: Int) : HomeIntent
    data class Hide(val packageName: String) : HomeIntent
    data class Unhide(val packageName: String) : HomeIntent
    data class ShowAppInfo(val packageName: String) : HomeIntent
    data class Uninstall(val packageName: String) : HomeIntent
    data class Quick(val action: QuickAction) : HomeIntent
    data class SourceClicked(val source: HomeSource) : HomeIntent
    data class InstallUpdate(val info: UpdateInfo) : HomeIntent
}

sealed interface HomeEffect {
    data class Navigate(val screen: Screen, val path: String? = null) : HomeEffect
    data class Toast(val msg: Msg) : HomeEffect
    /** [packageName] null means "first card". */
    data class FocusApp(val packageName: String?, val inFavorites: Boolean) : HomeEffect
    data class ShowAppMenu(val menu: AppMenu) : HomeEffect
    data class ShowUpdateDialog(val info: UpdateInfo) : HomeEffect
}
