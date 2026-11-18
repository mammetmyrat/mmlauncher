package com.mmlauncher.tv.presentation.wizard

import com.mmlauncher.tv.domain.model.Language
import com.mmlauncher.tv.domain.model.Wallpaper
import com.mmlauncher.tv.presentation.common.Msg

data class WizardState(
    val page: Int = 0,
    val language: Language = Language.SYSTEM,
    val wallpaper: Wallpaper = Wallpaper.NONE,
    val autoUpdates: Boolean = false,
    val isDefaultHome: Boolean = false,
) {
    val isLastPage get() = page == LAST_PAGE
    companion object { const val LAST_PAGE = 3 }
}

sealed interface WizardIntent {
    /** [page] restores the position after the Activity was recreated (language change). */
    data class Load(val page: Int) : WizardIntent
    object Refresh : WizardIntent
    object Next : WizardIntent
    object Back : WizardIntent
    data class SelectLanguage(val language: Language) : WizardIntent
    data class SelectWallpaper(val wallpaper: Wallpaper) : WizardIntent
    data class SetAutoUpdates(val on: Boolean) : WizardIntent
    object OpenHomeSettings : WizardIntent
    object SetHomeViaAdb : WizardIntent
}

sealed interface WizardEffect {
    object Recreate : WizardEffect
    object Finish : WizardEffect
    data class Toast(val msg: Msg) : WizardEffect
}
