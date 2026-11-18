package com.mmlauncher.tv.presentation.files

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mmlauncher.tv.R
import com.mmlauncher.tv.appContainer
import com.mmlauncher.tv.domain.model.FileEntry
import com.mmlauncher.tv.presentation.common.BaseActivity
import com.mmlauncher.tv.presentation.common.Fonts
import com.mmlauncher.tv.presentation.common.Hints

/**
 * Simple built-in file manager: browse internal storage and USB, open files, set an image as the
 * wallpaper. A dumb View of [FilesState]; the permission screens are platform work and stay here.
 */
class FileManagerActivity : BaseActivity() {

    private lateinit var store: FilesStore
    private lateinit var list: RecyclerView
    private lateinit var pathView: TextView
    private lateinit var emptyView: TextView
    private val adapter = FileAdapter()
    private var shown: FilesState? = null
    private lateinit var accessPanel: LinearLayout
    private lateinit var accessMsg: TextView
    private lateinit var accessActions: LinearLayout
    private var waitingForAllFiles = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)
        store = appContainer.filesStore()
        pathView = findViewById(R.id.path)
        emptyView = findViewById(R.id.empty)
        accessPanel = findViewById(R.id.access_panel)
        accessMsg = findViewById(R.id.access_msg)
        accessActions = findViewById(R.id.access_actions)
        list = findViewById<RecyclerView>(R.id.files).apply {
            layoutManager = LinearLayoutManager(this@FileManagerActivity)
            adapter = this@FileManagerActivity.adapter
            itemAnimator = null
        }
        Fonts.apply(window.decorView)
        store.dispatch(FilesIntent.Init(intent.getStringExtra(EXTRA_PATH)))
    }

    override fun onStart() {
        super.onStart()
        shown = null
        store.bind(::render, ::onEffect)
    }

    override fun onResume() {
        super.onResume()
        // Back from the "All files access" screen.
        if (waitingForAllFiles) store.dispatch(FilesIntent.Recheck)
    }

    override fun onStop() {
        super.onStop()
        store.unbind()
    }

    override fun onDestroy() {
        super.onDestroy()
        store.clear()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = store.dispatch(FilesIntent.Back)

    // ---- rendering --------------------------------------------------------------------------

    private fun render(s: FilesState) {
        if (s == shown) return
        shown = s
        if (s.needsAccess && !s.ready) { showAccessPanel(); return }
        accessPanel.visibility = View.GONE
        if (!s.ready) return
        pathView.text = s.path.orEmpty()
        adapter.items = s.entries
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (s.entries.isEmpty()) View.VISIBLE else View.GONE
        emptyView.setText(R.string.folder_empty)
        val idx = s.focusPath?.let { p -> s.entries.indexOfFirst { it.path == p } }?.takeIf { it >= 0 } ?: 0
        list.scrollToPosition(idx)
        focusAt(idx, 3)
    }

    private fun focusAt(idx: Int, retries: Int) {
        list.post {
            val v = list.findViewHolderForAdapterPosition(idx)?.itemView
            if (v != null) v.requestFocus() else if (retries > 0) focusAt(idx, retries - 1)
        }
    }

    /** Shown instead of an empty screen when the system gives no way (or no obvious way) to allow file access. */
    private fun showAccessPanel() {
        accessPanel.visibility = View.VISIBLE
        accessMsg.text = getString(R.string.files_access_msg, Hints.GRANT_FILES)
        if (accessActions.childCount == 0) {
            fun chip(label: Int, onClick: () -> Unit) {
                val v = LayoutInflater.from(this).inflate(R.layout.item_quick, accessActions, false) as TextView
                v.setText(label)
                v.setOnClickListener { onClick() }
                Fonts.apply(v)
                accessActions.addView(v)
            }
            if (Build.VERSION.SDK_INT >= 30) chip(R.string.files_open_perm_screen) { openPermissionScreen() }
            chip(R.string.files_allow_adb) { store.dispatch(FilesIntent.GrantViaAdb) }
            accessActions.getChildAt(0)?.requestFocus()
        }
    }

    // ---- effects ----------------------------------------------------------------------------

    private fun onEffect(effect: FilesEffect) {
        when (effect) {
            FilesEffect.RequestAccess -> if (Build.VERSION.SDK_INT < 30) requestAccess() // Android 11+: the panel explains
            FilesEffect.Finish -> finish()
            is FilesEffect.Toast -> Toast.makeText(this, effect.msg.text(this), Toast.LENGTH_LONG).show()
            is FilesEffect.ShowFileMenu -> showFileMenu(effect)
        }
    }

    private fun showFileMenu(effect: FilesEffect.ShowFileMenu) {
        val e = effect.entry
        val labels = ArrayList<String>()
        val actions = ArrayList<FilesIntent>()
        labels += getString(R.string.open); actions += FilesIntent.OpenFile(e.path)
        if (effect.isImage) { labels += getString(R.string.set_wallpaper); actions += FilesIntent.SetWallpaper(e.path) }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(e.name)
            .setItems(labels.toTypedArray()) { _, i -> store.dispatch(actions[i]) }
            .show()
    }

    // ---- storage permission (platform) ------------------------------------------------------

    /** Android 6-10: the usual runtime permission dialog. */
    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= 23) requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQ_PERM)
    }

    /** Android 11+: the "All files access" screen. Many TV firmwares do not have it, so failure is reported. */
    private fun openPermissionScreen() {
        waitingForAllFiles = true
        val uri = Uri.parse("package:$packageName")
        val tries = listOf(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri),
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
        )
        for (i in tries) {
            try { startActivity(i); return } catch (_: Exception) {}
        }
        waitingForAllFiles = false
        Toast.makeText(this, R.string.files_no_perm_screen, Toast.LENGTH_LONG).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERM) {
            store.dispatch(
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) FilesIntent.Recheck else FilesIntent.AccessDenied
            )
        }
    }

    // ---- adapter ----------------------------------------------------------------------------

    private inner class FileAdapter : RecyclerView.Adapter<FileAdapter.VH>() {
        var items: List<FileEntry> = emptyList()
        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
            Fonts.apply(v)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val e = items[position]
            h.name.text = e.name
            h.info.text = if (e.isDirectory) "" else Formatter.formatShortFileSize(this@FileManagerActivity, e.sizeBytes)
            h.icon.setImageResource(if (e.isDirectory) R.drawable.ic_folder else R.drawable.ic_file)
            h.icon.setColorFilter(getColor(if (e.isDirectory) R.color.accent else R.color.text_secondary))
            h.itemView.setOnClickListener { store.dispatch(FilesIntent.Open(e)) }
            h.itemView.setOnLongClickListener { store.dispatch(FilesIntent.LongPress(e)); true }
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.icon)
            val name: TextView = v.findViewById(R.id.name)
            val info: TextView = v.findViewById(R.id.info)
        }
    }

    companion object {
        const val EXTRA_PATH = "path"
        private const val REQ_PERM = 10
    }
}
