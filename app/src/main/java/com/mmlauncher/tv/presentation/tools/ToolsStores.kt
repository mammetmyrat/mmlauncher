package com.mmlauncher.tv.presentation.tools

import com.mmlauncher.tv.R
import com.mmlauncher.tv.core.MviStore
import com.mmlauncher.tv.core.Scheduler
import com.mmlauncher.tv.domain.model.*
import com.mmlauncher.tv.domain.usecase.*
import com.mmlauncher.tv.presentation.common.Hints
import com.mmlauncher.tv.presentation.common.Msg
import com.mmlauncher.tv.presentation.common.msg

// ============================ device info ============================

data class DeviceInfoState(val report: DeviceReport? = null)

sealed interface DeviceInfoIntent {
    object Load : DeviceInfoIntent
    data class RowClicked(val key: InfoKey) : DeviceInfoIntent
}

sealed interface DeviceInfoEffect {
    data class Toast(val msg: Msg) : DeviceInfoEffect
    /** Developer mode could not be switched on from here: show the screen that explains the options. */
    object OpenDeveloperScreen : DeviceInfoEffect
}

class DeviceInfoStore(
    scheduler: Scheduler,
    private val getReport: GetDeviceReport,
    private val unlock: UnlockDeveloperMode,
) : MviStore<DeviceInfoState, DeviceInfoIntent, DeviceInfoEffect>(DeviceInfoState(), scheduler) {

    private var taps = 0
    private var cancelReset: (() -> Unit)? = null

    override fun handle(intent: DeviceInfoIntent) {
        when (intent) {
            DeviceInfoIntent.Load -> io({ getReport() }) { setState { copy(report = it) } }
            is DeviceInfoIntent.RowClicked -> if (intent.key == InfoKey.BUILD) tapBuild()
        }
    }

    /** Like stock Android: 7 quick taps on "Build". Taps further apart than 2.5 s start over. */
    private fun tapBuild() {
        cancelReset?.invoke()
        taps++
        if (taps >= TAPS_NEEDED) { taps = 0; unlockNow(); return }
        if (taps >= 3) emit(DeviceInfoEffect.Toast(msg(R.string.dev_taps_left, TAPS_NEEDED - taps)))
        cancelReset = delayed(2500) { taps = 0 }
    }

    private fun unlockNow() = network({ unlock() }) { result ->
        when (result) {
            UnlockResult.ENABLED -> emit(DeviceInfoEffect.Toast(msg(R.string.dev_unlocked)))
            UnlockResult.ALREADY_ON -> emit(DeviceInfoEffect.Toast(msg(R.string.dev_already_on)))
            UnlockResult.NEEDS_PERMISSION -> {
                emit(DeviceInfoEffect.Toast(msg(R.string.dev_unlock_need)))
                emit(DeviceInfoEffect.OpenDeveloperScreen)
            }
        }
    }

    private companion object { const val TAPS_NEEDED = 7 }
}

// ============================ network & ADB ============================

data class NetworkState(val snapshot: NetworkSnapshot? = null)

sealed interface NetworkIntent { object Refresh : NetworkIntent }

class NetworkStore(scheduler: Scheduler, private val getSnapshot: GetNetworkSnapshot) :
    MviStore<NetworkState, NetworkIntent, Unit>(NetworkState(), scheduler) {

    override fun handle(intent: NetworkIntent) {
        when (intent) {
            NetworkIntent.Refresh -> io({ getSnapshot() }) { setState { copy(snapshot = it) } }
        }
    }
}

// ============================ app manager ============================

data class AppManagerState(
    val apps: List<ManagedApp> = emptyList(),
    val showSystem: Boolean = false,
    val loading: Boolean = true,
)

sealed interface AppManagerIntent {
    object Load : AppManagerIntent
    object ToggleSystem : AppManagerIntent
    data class AppClicked(val app: ManagedApp) : AppManagerIntent
    data class Perform(val app: ManagedApp, val action: AppAction, val confirmed: Boolean = false) : AppManagerIntent
}

sealed interface AppManagerEffect {
    data class ShowMenu(val app: ManagedApp, val actions: List<AppAction>) : AppManagerEffect
    data class AskConfirm(val app: ManagedApp, val action: AppAction) : AppManagerEffect
    data class Toast(val msg: Msg) : AppManagerEffect
}

class AppManagerStore(
    scheduler: Scheduler,
    private val listApps: ListManagedApps,
    private val actionsFor: AppActionsFor,
    private val perform: PerformAppAction,
) : MviStore<AppManagerState, AppManagerIntent, AppManagerEffect>(AppManagerState(), scheduler) {

    override fun handle(intent: AppManagerIntent) {
        when (intent) {
            AppManagerIntent.Load -> load()
            AppManagerIntent.ToggleSystem -> { setState { copy(showSystem = !showSystem, loading = true) }; load() }
            is AppManagerIntent.AppClicked -> emit(AppManagerEffect.ShowMenu(intent.app, actionsFor(intent.app)))
            is AppManagerIntent.Perform -> run(intent)
        }
    }

    private fun load() {
        val system = state.showSystem
        io({ listApps(system) }) { apps -> setState { copy(apps = apps, loading = false) } }
    }

    private fun run(i: AppManagerIntent.Perform) {
        if (i.action.needsConfirmation() && !i.confirmed) { emit(AppManagerEffect.AskConfirm(i.app, i.action)); return }
        network({ perform(i.action, i.app.packageName) }) { outcome ->
            when (outcome) {
                ActionOutcome.Done -> {
                    // Opening a screen needs no message; maintenance actions confirm and refresh the list.
                    if (i.action != AppAction.OPEN_INFO && i.action != AppAction.UNINSTALL) {
                        emit(AppManagerEffect.Toast(msg(R.string.act_done))); load()
                    }
                }
                is ActionOutcome.Saved -> emit(AppManagerEffect.Toast(msg(R.string.act_saved, outcome.path)))
                is ActionOutcome.NeedsAdb -> emit(AppManagerEffect.Toast(
                    if (outcome.reason == AdbFailure.WAITING_FOR_APPROVAL) msg(R.string.adb_denied)
                    else msg(R.string.adb_unavailable, Hints.ENABLE_TCPIP)
                ))
                ActionOutcome.Failed -> emit(AppManagerEffect.Toast(
                    if (i.action == AppAction.OPEN_INFO || i.action == AppAction.UNINSTALL) msg(R.string.cant_open_screen)
                    else msg(R.string.act_failed)
                ))
            }
        }
    }
}

// ============================ logs ============================

data class LogsState(
    val lines: List<String> = emptyList(),   // already filtered by [level]
    val level: LogLevel = LogLevel.VERBOSE,
    val viaAdb: Boolean = false,
    val failure: AdbFailure? = null,
    val loading: Boolean = true,
)

sealed interface LogsIntent {
    object Load : LogsIntent
    data class SetLevel(val level: LogLevel) : LogsIntent
    object Save : LogsIntent
    object Clear : LogsIntent
}

sealed interface LogsEffect { data class Toast(val msg: Msg) : LogsEffect }

class LogsStore(
    scheduler: Scheduler,
    private val readLogs: ReadLogs,
    private val filterLogs: FilterLogs,
    private val saveLogs: SaveLogs,
    private val clearLogs: ClearLogs,
) : MviStore<LogsState, LogsIntent, LogsEffect>(LogsState(), scheduler) {

    private var raw: List<String> = emptyList()
    private var loading = false

    override fun handle(intent: LogsIntent) {
        when (intent) {
            LogsIntent.Load -> load()
            is LogsIntent.SetLevel -> setState { copy(level = intent.level, lines = filterLogs(raw, intent.level)) }
            LogsIntent.Save -> io({ saveLogs(state.lines) }) { path ->
                emit(LogsEffect.Toast(if (path != null) msg(R.string.act_saved, path) else msg(R.string.act_failed)))
            }
            LogsIntent.Clear -> network({ clearLogs() }) { ok ->
                if (ok) { emit(LogsEffect.Toast(msg(R.string.logs_cleared))); load() }
                else emit(LogsEffect.Toast(msg(R.string.adb_unavailable, Hints.ENABLE_TCPIP)))
            }
        }
    }

    /** May wait for the user to approve the local ADB key, hence the network thread and no pile-up. */
    private fun load() {
        if (loading) return
        loading = true
        network({ readLogs() }) { dump ->
            loading = false
            raw = dump.lines
            setState { copy(lines = filterLogs(raw, level), viaAdb = dump.viaAdb, failure = dump.failure, loading = false) }
        }
    }
}
