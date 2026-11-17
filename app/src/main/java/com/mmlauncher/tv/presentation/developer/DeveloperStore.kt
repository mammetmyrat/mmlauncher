package com.mmlauncher.tv.presentation.developer

import com.mmlauncher.tv.R
import com.mmlauncher.tv.core.MviStore
import com.mmlauncher.tv.core.Scheduler
import com.mmlauncher.tv.domain.model.AdbFailure
import com.mmlauncher.tv.domain.model.DeveloperStatus
import com.mmlauncher.tv.domain.model.SystemScreen
import com.mmlauncher.tv.domain.repository.DiagnosticsRepository
import com.mmlauncher.tv.domain.usecase.*
import com.mmlauncher.tv.presentation.common.Hints
import com.mmlauncher.tv.presentation.common.Msg
import com.mmlauncher.tv.presentation.common.msg

data class DeveloperState(val status: DeveloperStatus? = null, val busy: Boolean = false)

sealed interface DeveloperIntent {
    object Load : DeveloperIntent
    object ToggleDeveloper : DeveloperIntent
    object ToggleUsb : DeveloperIntent
    object GrantViaAdb : DeveloperIntent
    data class OpenSystem(val screen: String) : DeveloperIntent
}

sealed interface DeveloperEffect {
    data class Toast(val msg: Msg) : DeveloperEffect
}

class DeveloperStore(
    scheduler: Scheduler,
    private val getStatus: GetDeveloperStatus,
    private val toggleDeveloper: ToggleDeveloperMode,
    private val toggleUsb: ToggleUsbDebugging,
    private val grant: GrantWriteSecureViaAdb,
    private val diagnostics: DiagnosticsRepository,
    private val openSystemScreen: OpenSystemScreen,
) : MviStore<DeveloperState, DeveloperIntent, DeveloperEffect>(DeveloperState(), scheduler) {

    override fun handle(intent: DeveloperIntent) {
        when (intent) {
            DeveloperIntent.Load -> refresh()
            DeveloperIntent.ToggleDeveloper -> apply(toggleDeveloper())
            DeveloperIntent.ToggleUsb -> apply(toggleUsb())
            DeveloperIntent.GrantViaAdb -> grantViaAdb()
            is DeveloperIntent.OpenSystem ->
                if (!openSystemScreen(intent.screen)) emit(DeveloperEffect.Toast(msg(R.string.cant_open_screen)))
        }
    }

    private fun refresh() = setState { copy(status = getStatus()) }

    private fun apply(ok: Boolean) {
        emit(DeveloperEffect.Toast(if (ok) msg(R.string.dev_changed) else msg(R.string.dev_denied)))
        refresh()
    }

    /** Asks the box's own adbd to grant WRITE_SECURE_SETTINGS (needs ADB over network). */
    private fun grantViaAdb() {
        emit(DeveloperEffect.Toast(msg(R.string.adb_working)))
        setState { copy(busy = true) }
        network({ grant() to diagnostics.lastFailure() }) { (granted, failure) ->
            setState { copy(busy = false) }
            emit(DeveloperEffect.Toast(when {
                granted -> msg(R.string.dev_changed)
                failure == AdbFailure.WAITING_FOR_APPROVAL -> msg(R.string.adb_denied)
                else -> msg(R.string.adb_unavailable, Hints.ENABLE_TCPIP)
            }))
            refresh()
        }
    }
}
