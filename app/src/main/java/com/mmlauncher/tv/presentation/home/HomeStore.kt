package com.mmlauncher.tv.presentation.home

import com.mmlauncher.tv.R
import com.mmlauncher.tv.core.MviStore
import com.mmlauncher.tv.core.Scheduler
import com.mmlauncher.tv.domain.model.*
import com.mmlauncher.tv.domain.usecase.*
import com.mmlauncher.tv.presentation.common.msg

class HomeStore(
    scheduler: Scheduler,
    private val loadApps: LoadHomeApps,
    private val loadSources: LoadHomeSources,
    private val loadSettings: LoadLauncherSettings,
    private val readRam: ReadRam,
    private val launchApp: LaunchApp,
    private val favorites: ManageFavorites,
    private val hidden: ManageHidden,
    private val buildMenu: BuildAppMenu,
    private val resolveAutostart: ResolveAutostart,
    private val autoCheckUpdates: AutoCheckUpdates,
    private val installUpdate: InstallUpdate,
    private val openSystemScreen: OpenSystemScreen,
    private val openAppInfo: OpenAppInfo,
    private val uninstallApp: UninstallApp,
    private val openTvInput: OpenTvInput,
    private val isWizardDone: IsWizardDone,
) : MviStore<HomeState, HomeIntent, HomeEffect>(HomeState(), scheduler) {

    private var installed: List<AppInfo> = emptyList()
    private var pendingAutostart = false
    private var updateCheckRunning = false
    private var cancelRamTimer: (() -> Unit)? = null
    private var cancelCountdownTimer: (() -> Unit)? = null

    override fun handle(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.Create -> create(intent.freshStart)
            HomeIntent.Start -> start()
            HomeIntent.Stop -> stop()
            HomeIntent.PackagesChanged -> reloadInstalled()
            HomeIntent.VolumesChanged -> reloadSources()
            HomeIntent.HomePressed -> emit(HomeEffect.FocusApp(null, false))
            HomeIntent.CancelCountdown -> clearCountdown()
            is HomeIntent.Launch -> launch(intent.app)
            is HomeIntent.MenuRequested -> emit(HomeEffect.ShowAppMenu(buildMenu(intent.app)))
            is HomeIntent.AddFavorite -> { favorites.add(intent.packageName); resplit(); emit(HomeEffect.FocusApp(intent.packageName, true)) }
            is HomeIntent.RemoveFavorite -> { favorites.remove(intent.packageName); resplit(); emit(HomeEffect.FocusApp(intent.packageName, false)) }
            is HomeIntent.MoveFavorite -> if (favorites.move(intent.packageName, intent.delta)) {
                resplit(); emit(HomeEffect.FocusApp(intent.packageName, true))
            }
            is HomeIntent.Hide -> { hidden.hide(intent.packageName); resplit(); emit(HomeEffect.FocusApp(null, false)) }
            is HomeIntent.Unhide -> { hidden.unhide(intent.packageName); resplit(); emit(HomeEffect.FocusApp(intent.packageName, false)) }
            is HomeIntent.ShowAppInfo -> if (!openAppInfo(intent.packageName)) toast(R.string.cant_open_screen)
            is HomeIntent.Uninstall -> if (!uninstallApp(intent.packageName)) toast(R.string.cant_open_screen)
            is HomeIntent.Quick -> quick(intent.action)
            is HomeIntent.SourceClicked -> source(intent.source)
            is HomeIntent.InstallUpdate -> install(intent.info)
        }
    }

    private fun toast(res: Int, vararg args: Any) = emit(HomeEffect.Toast(msg(res, *args)))

    // ---- lifecycle --------------------------------------------------------------------------

    private fun create(freshStart: Boolean) {
        val firstRun = !isWizardDone() && freshStart
        if (firstRun) emit(HomeEffect.Navigate(Screen.WIZARD))
        // FR-19: only on a fresh start of the launcher, and only once per device boot.
        pendingAutostart = !firstRun && freshStart && resolveAutostart.isDue()
    }

    private fun start() {
        reloadInstalled()
        reloadSources()
        refreshSettings()
        scheduleRamTick()
        maybeCheckUpdates()
    }

    private fun stop() {
        cancelRamTimer?.invoke(); cancelRamTimer = null
        clearCountdown()
    }

    // ---- data -------------------------------------------------------------------------------

    private fun reloadInstalled() = io({ loadApps.installed() }) { list ->
        installed = list
        resplit(loaded = true)
        if (pendingAutostart) {
            pendingAutostart = false
            resolveAutostart(installed)?.let { startCountdown(it) }
        }
    }

    /** Re-splits the cached app list after a preference change (no package-manager query). */
    private fun resplit(loaded: Boolean = state.loaded) {
        val home = loadApps.from(installed)
        setState { copy(loaded = loaded, apps = home.visible, favorites = home.favorites, hiddenApps = home.hidden) }
    }

    private fun reloadSources() = io({ loadSources() }) { sources -> setState { copy(sources = sources) } }

    private fun refreshSettings() = io({ loadSettings() }) { s ->
        setState { copy(language = s.language, wallpaper = s.wallpaper) }
    }

    private fun scheduleRamTick() {
        cancelRamTimer?.invoke()
        val ram = readRam()
        setState { copy(ram = ram) }
        cancelRamTimer = delayed(RAM_TICK_MS) { scheduleRamTick() }
    }

    private fun maybeCheckUpdates() {
        if (updateCheckRunning) return // onStart can fire twice in a row; one request is enough
        updateCheckRunning = true
        network({ autoCheckUpdates() }) { ran ->
            updateCheckRunning = false
            if (ran) reloadSources()
        }
    }

    // ---- actions ----------------------------------------------------------------------------

    private fun launch(app: AppInfo) {
        when (app.builtIn) {
            BuiltInApp.FILES -> emit(HomeEffect.Navigate(Screen.FILES))
            null -> if (!launchApp(app)) toast(R.string.cant_open_app)
        }
    }

    private fun startCountdown(plan: AutostartPlan) {
        if (plan.delaySeconds <= 0) { launch(plan.app); return }
        setState { copy(countdown = Countdown(plan.app, plan.delaySeconds)) }
        scheduleCountdownTick()
    }

    private fun scheduleCountdownTick() {
        cancelCountdownTimer = delayed(1000) {
            val c = state.countdown ?: return@delayed
            if (c.secondsLeft <= 1) {
                clearCountdown()
                launch(c.app)
            } else {
                setState { copy(countdown = c.copy(secondsLeft = c.secondsLeft - 1)) }
                scheduleCountdownTick()
            }
        }
    }

    private fun clearCountdown() {
        cancelCountdownTimer?.invoke(); cancelCountdownTimer = null
        if (state.countdown != null) setState { copy(countdown = null) }
    }

    private fun quick(action: QuickAction) {
        when (action) {
            QuickAction.DEVELOPER -> emit(HomeEffect.Navigate(Screen.DEVELOPER))
            QuickAction.RAM -> emit(HomeEffect.Navigate(Screen.RAM))
            QuickAction.STORAGE -> emit(HomeEffect.Navigate(Screen.STORAGE))
            QuickAction.TOOLS -> emit(HomeEffect.Navigate(Screen.TOOLS))
            QuickAction.LAUNCHER_SETTINGS -> emit(HomeEffect.Navigate(Screen.SETTINGS))
            QuickAction.SYSTEM_SETTINGS -> if (!openSystemScreen(SystemScreen.ALL)) toast(R.string.cant_open_screen)
        }
    }

    private fun source(source: HomeSource) {
        when (source) {
            is HomeSource.UpdateAvailable -> emit(HomeEffect.ShowUpdateDialog(source.info))
            is HomeSource.Input -> if (!openTvInput(source.input.id)) toast(R.string.cant_open_screen)
            is HomeSource.Usb -> emit(HomeEffect.Navigate(Screen.FILES, source.volume.path))
        }
    }

    private fun install(info: UpdateInfo) {
        toast(R.string.upd_downloading)
        network({ installUpdate(info) }) { result ->
            when (result) {
                UpdateInstall.STARTED -> Unit
                UpdateInstall.NEEDS_PERMISSION -> toast(R.string.upd_allow)
                UpdateInstall.DOWNLOAD_FAILED -> toast(R.string.upd_dl_failed)
            }
        }
    }

    private companion object { const val RAM_TICK_MS = 4000L }
}
