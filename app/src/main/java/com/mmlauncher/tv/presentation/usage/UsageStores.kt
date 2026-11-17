package com.mmlauncher.tv.presentation.usage

import com.mmlauncher.tv.R
import com.mmlauncher.tv.core.MviStore
import com.mmlauncher.tv.core.Scheduler
import com.mmlauncher.tv.domain.model.AdbFailure
import com.mmlauncher.tv.domain.usecase.*
import com.mmlauncher.tv.presentation.common.msg

private fun AdbFailure?.toHint(): UsageHint = when (this) {
    null -> UsageHint.NONE
    AdbFailure.NOT_LISTENING -> UsageHint.NEED_ADB
    AdbFailure.WAITING_FOR_APPROVAL -> UsageHint.WAITING_FOR_APPROVAL
    AdbFailure.UNEXPECTED -> UsageHint.UNEXPECTED
    AdbFailure.NO_DATA -> UsageHint.NO_DATA
}

private const val MAX_ROWS = 80

class RamStore(
    scheduler: Scheduler,
    private val getRamUsage: GetRamUsage,
    private val boostMemory: BoostMemory,
    private val stopApp: StopApp,
    private val selfPackage: String,
) : MviStore<RamScreenState, UsageIntent, UsageEffect>(RamScreenState(), scheduler) {

    private var refreshing = false

    override fun handle(intent: UsageIntent) {
        when (intent) {
            UsageIntent.Refresh -> refresh()
            UsageIntent.Boost -> io({ boostMemory() }) { freed ->
                emit(UsageEffect.Toast(if (freed > 0) msg(R.string.boost_freed, freed) else msg(R.string.boost_none)))
                refresh()
            }
            is UsageIntent.ItemClicked -> stop(intent.item)
            is UsageIntent.OpenSystem -> Unit
        }
    }

    /** Slow: may wait for the user to approve the local ADB key, hence the network thread and no pile-up. */
    private fun refresh() {
        if (refreshing) return
        refreshing = true
        network({ getRamUsage() }) { usage -> refreshing = false; show(usage) }
    }

    private fun show(usage: RamUsage) {
        val processes = usage.processes
        if (processes != null) {
            val max = processes.firstOrNull()?.pssKb?.coerceAtLeast(1) ?: 1
            val items = processes.take(MAX_ROWS).map {
                UsageItem(it.label, it.pssKb * 1024, it.pssKb.toFloat() / max, it.packageName)
            }
            setState { copy(loading = false, ram = usage.ram, items = items, hint = UsageHint.TAP_TO_STOP) }
        } else {
            val own = UsageItem("", usage.ownKb * 1024, 1f, null, isSelf = true)
            setState { copy(loading = false, ram = usage.ram, items = listOf(own), hint = usage.failure.toHint()) }
        }
    }

    /** Select an app: stop its background process (what an ordinary app is allowed to do). */
    private fun stop(item: UsageItem) {
        val pkg = item.packageName ?: return
        if (pkg == selfPackage) return
        stopApp(pkg)
        emit(UsageEffect.Toast(msg(R.string.ram_stopped, item.label)))
        delayed(1200) { refresh() }
    }
}

class StorageStore(
    scheduler: Scheduler,
    private val getStorageUsage: GetStorageUsage,
    private val openAppInfo: OpenAppInfo,
    private val openSystemScreen: OpenSystemScreen,
) : MviStore<StorageScreenState, UsageIntent, UsageEffect>(StorageScreenState(), scheduler) {

    private var refreshing = false

    override fun handle(intent: UsageIntent) {
        when (intent) {
            UsageIntent.Refresh -> refresh()
            UsageIntent.Boost -> Unit
            is UsageIntent.ItemClicked -> intent.item.packageName?.let {
                if (!openAppInfo(it)) emit(UsageEffect.Toast(msg(R.string.cant_open_screen)))
            }
            is UsageIntent.OpenSystem ->
                if (!openSystemScreen(intent.screen)) emit(UsageEffect.Toast(msg(R.string.cant_open_screen)))
        }
    }

    private fun refresh() {
        if (refreshing) return
        refreshing = true
        network({ getStorageUsage() }) { usage -> refreshing = false; show(usage) }
    }

    private fun show(usage: StorageUsage) {
        val max = usage.apps.firstOrNull()?.bytes?.coerceAtLeast(1) ?: 1
        val items = usage.apps.take(MAX_ROWS).map { UsageItem(it.label, it.bytes, it.bytes.toFloat() / max, it.packageName) }
        val hint = if (usage.failure == null) UsageHint.TAP_FOR_STORAGE else usage.failure.toHint()
        setState { copy(loading = false, spaces = usage.spaces, items = items, hint = hint) }
    }
}
