package com.mmlauncher.tv.presentation.files

import com.mmlauncher.tv.domain.model.FileEntry
import com.mmlauncher.tv.presentation.common.Msg

data class FilesState(
    /** null = the list of storage roots. */
    val path: String? = null,
    val entries: List<FileEntry> = emptyList(),
    /** Path of the entry the View should focus after showing this list. */
    val focusPath: String? = null,
    val ready: Boolean = false,
    /** No storage access yet: the View shows an explanation with ways to grant it. */
    val needsAccess: Boolean = false,
)

sealed interface FilesIntent {
    /** [startPath] opens a folder directly (the USB card); null shows the roots. */
    data class Init(val startPath: String?) : FilesIntent
    /** The View asks the system for access and reports the outcome (or re-checks on resume). */
    object Recheck : FilesIntent
    object AccessDenied : FilesIntent
    /** Ask the box's own adbd to grant "All files access". */
    object GrantViaAdb : FilesIntent
    data class Open(val entry: FileEntry) : FilesIntent
    data class LongPress(val entry: FileEntry) : FilesIntent
    data class OpenFile(val path: String) : FilesIntent
    data class SetWallpaper(val path: String) : FilesIntent
    object Back : FilesIntent
}

sealed interface FilesEffect {
    object RequestAccess : FilesEffect
    object Finish : FilesEffect
    data class Toast(val msg: Msg) : FilesEffect
    data class ShowFileMenu(val entry: FileEntry, val isImage: Boolean) : FilesEffect
}
