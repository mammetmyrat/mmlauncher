package com.mmlauncher.tv.presentation.wizard

import com.mmlauncher.tv.R
import com.mmlauncher.tv.core.MviStore
import com.mmlauncher.tv.core.Scheduler
import com.mmlauncher.tv.domain.usecase.*
import com.mmlauncher.tv.presentation.common.Hints
import com.mmlauncher.tv.presentation.common.msg

class WizardStore(
    scheduler: Scheduler,
    private val loadSettings: LoadLauncherSettings,
    private val changeLanguage: ChangeLanguage,
    private val changeWallpaper: ChangeWallpaper,
    private val setAutoUpdates: SetAutoUpdates,
    private val isDefaultHome: IsDefaultHome,
    private val openHomeSettings: OpenHomeSettings,
    private val finishWizard: FinishWizard,
    private val setHomeViaAdb: SetDefaultHomeViaAdb,
) : MviStore<WizardState, WizardIntent, WizardEffect>(WizardState(), scheduler) {

    override fun handle(intent: WizardIntent) {
        when (intent) {
            is WizardIntent.Load -> { load(intent.page) }
            WizardIntent.Refresh -> refresh()
            WizardIntent.Next ->
                if (state.isLastPage) finish() else { setState { copy(page = page + 1) }; refresh() }
            WizardIntent.Back ->
                if (state.page > 0) setState { copy(page = page - 1) } else finish()
            is WizardIntent.SelectLanguage -> if (changeLanguage(intent.language)) emit(WizardEffect.Recreate)
            is WizardIntent.SelectWallpaper -> { changeWallpaper(intent.wallpaper); refresh() }
            is WizardIntent.SetAutoUpdates -> {
                setAutoUpdates(intent.on); refresh()
                setState { copy(page = page + 1) } // the question is answered: go on
            }
            WizardIntent.SetHomeViaAdb -> {
                emit(WizardEffect.Toast(msg(R.string.adb_working)))
                network({ setHomeViaAdb() }) { ok ->
                    refresh()
                    emit(WizardEffect.Toast(if (ok) msg(R.string.dev_changed) else msg(R.string.adb_unavailable, Hints.ENABLE_TCPIP)))
                }
            }
            WizardIntent.OpenHomeSettings ->
                if (!openHomeSettings()) emit(WizardEffect.Toast(msg(R.string.cant_open_screen)))
        }
    }

    private fun load(page: Int) {
        val s = loadSettings()
        setState { copy(page = page.coerceIn(0, WizardState.LAST_PAGE), language = s.language, wallpaper = s.wallpaper, autoUpdates = s.autoUpdates, isDefaultHome = isDefaultHome()) }
    }

    private fun refresh() {
        val s = loadSettings()
        val home = isDefaultHome()
        setState { copy(language = s.language, wallpaper = s.wallpaper, autoUpdates = s.autoUpdates, isDefaultHome = home) }
    }

    /** Any exit counts as done, so the wizard does not nag on the next start. */
    private fun finish() {
        finishWizard()
        emit(WizardEffect.Finish)
    }
}
