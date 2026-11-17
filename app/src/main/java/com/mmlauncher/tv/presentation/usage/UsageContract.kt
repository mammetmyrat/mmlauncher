package com.mmlauncher.tv.presentation.usage

import com.mmlauncher.tv.domain.model.RamInfo
import com.mmlauncher.tv.domain.model.StorageSpace
import com.mmlauncher.tv.presentation.common.Msg

/** One row of a usage list (an app or a process). */
data class UsageItem(
    val label: String,
    val bytes: Long,
    val fraction: Float,
    val packageName: String?,
    /** The launcher's own row, shown when per-app numbers are unavailable. */
    val isSelf: Boolean = false,
)

/** Why the list looks the way it does; the View turns it into a sentence. */
enum class UsageHint {
    NONE, TAP_TO_STOP, TAP_FOR_STORAGE, NEED_ADB, WAITING_FOR_APPROVAL, UNEXPECTED, NO_DATA
}

data class RamScreenState(
    val loading: Boolean = true,
    val ram: RamInfo? = null,
    val items: List<UsageItem> = emptyList(),
    val hint: UsageHint = UsageHint.NONE,
)

data class StorageScreenState(
    val loading: Boolean = true,
    val spaces: List<StorageSpace> = emptyList(),
    val items: List<UsageItem> = emptyList(),
    val hint: UsageHint = UsageHint.NONE,
)

sealed interface UsageIntent {
    object Refresh : UsageIntent
    object Boost : UsageIntent
    data class ItemClicked(val item: UsageItem) : UsageIntent
    data class OpenSystem(val screen: String) : UsageIntent
}

sealed interface UsageEffect {
    data class Toast(val msg: Msg) : UsageEffect
}
