package com.mmlauncher.tv.presentation.files

import com.mmlauncher.tv.R
import com.mmlauncher.tv.core.MviStore
import com.mmlauncher.tv.core.Scheduler
import com.mmlauncher.tv.domain.usecase.*
import com.mmlauncher.tv.presentation.common.msg
import java.io.File

class FilesStore(
    scheduler: Scheduler,
    private val hasAccess: HasFileAccess,
    private val listDirectory: ListDirectory,
    private val openFile: OpenFile,
    private val isImage: IsImageFile,
    private val roots: FileRoots,
    private val changeWallpaper: ChangeWallpaper,
) : MviStore<FilesState, FilesIntent, FilesEffect>(FilesState(), scheduler) {

    private var startPath: String? = null
    private var started = false

    override fun handle(intent: FilesIntent) {
        when (intent) {
            is FilesIntent.Init -> { startPath = intent.startPath; if (hasAccess()) begin() else emit(FilesEffect.RequestAccess) }
            FilesIntent.Recheck -> if (!started) {
                if (hasAccess()) begin() else handle(FilesIntent.AccessDenied) // came back from the permission screen
            }
            FilesIntent.AccessDenied -> { emit(FilesEffect.Toast(msg(R.string.perm_denied))); emit(FilesEffect.Finish) }
            is FilesIntent.Open -> open(intent.entry.path, intent.entry.isDirectory)
            is FilesIntent.LongPress ->
                if (!intent.entry.isDirectory) emit(FilesEffect.ShowFileMenu(intent.entry, isImage(intent.entry.path)))
            is FilesIntent.OpenFile -> if (!openFile(intent.path)) emit(FilesEffect.Toast(msg(R.string.cant_open_file)))
            is FilesIntent.SetWallpaper -> io({ changeWallpaper.fromFile(intent.path) }) { ok ->
                emit(FilesEffect.Toast(if (ok) msg(R.string.wallpaper_set) else msg(R.string.wallpaper_failed)))
            }
            FilesIntent.Back -> back()
        }
    }

    private fun begin() {
        started = true
        val p = startPath
        if (p != null && File(p).isDirectory) show(p, null) else show(null, null)
    }

    private fun open(path: String, isDirectory: Boolean) {
        if (isDirectory) show(path, null)
        else if (!openFile(path)) emit(FilesEffect.Toast(msg(R.string.cant_open_file)))
    }

    private fun show(path: String?, focus: String?) = io({ listDirectory(path) }) { listing ->
        if (listing == null) {
            emit(FilesEffect.Toast(msg(R.string.no_access)))
        } else {
            setState { copy(path = listing.path, entries = listing.entries, focusPath = focus, ready = true) }
        }
    }

    /** Up one level; from a storage root go to the root list; from the root list leave. */
    private fun back() {
        val current = state.path
        if (current == null) { emit(FilesEffect.Finish); return }
        val parent = File(current).parent
        if (roots().any { it.path == current } || parent == null) show(null, current) else show(parent, current)
    }
}
