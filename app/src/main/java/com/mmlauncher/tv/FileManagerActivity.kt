package com.mmlauncher.tv

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.concurrent.Executors

/**
 * Simple built-in file manager: browse internal storage and USB, open files with other apps,
 * set an image as the launcher wallpaper. Read-only on purpose (no delete / copy in the MVP).
 */
class FileManagerActivity : Activity() {

    private data class Entry(val name: String, val file: File?, val isDir: Boolean, val info: String)

    private lateinit var list: RecyclerView
    private lateinit var pathView: TextView
    private lateinit var emptyView: TextView
    private val adapter = FileAdapter()
    private var current: File? = null            // null = list of storage roots
    private var roots: List<Storage.Volume> = emptyList()
    private val focusStack = ArrayDeque<Int>()   // focused row of each parent directory
    private var focusedIndex = 0
    private var startPath: String? = null

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(LocaleHelper.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)
        pathView = findViewById(R.id.path)
        emptyView = findViewById(R.id.empty)
        list = findViewById<RecyclerView>(R.id.files).apply {
            layoutManager = LinearLayoutManager(this@FileManagerActivity)
            adapter = this@FileManagerActivity.adapter
            itemAnimator = null
        }
        Fonts.apply(window.decorView)
        startPath = intent.getStringExtra(EXTRA_PATH)

        if (hasStoragePermission()) start() else requestStoragePermission()
    }

    // ---- permission -------------------------------------------------------------------------

    /** Android 11+: "All files access". Android 6-10: READ_EXTERNAL_STORAGE (legacy storage). */
    private fun hasStoragePermission(): Boolean = when {
        Build.VERSION.SDK_INT >= 30 -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT >= 23 ->
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        else -> true
    }

    private var waitingForAllFiles = false

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            waitingForAllFiles = true
            val uri = Uri.parse("package:$packageName")
            val tries = listOf(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri),
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
            )
            for (i in tries) {
                try { startActivity(i); return } catch (_: Exception) {}
            }
            // No such screen on many TV builds: tell how to grant it from a computer.
            waitingForAllFiles = false
            Toast.makeText(this, getString(R.string.files_need_perm, Dump.GRANT_FILES), Toast.LENGTH_LONG).show()
            finish()
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQ_PERM)
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from the "All files access" screen.
        if (waitingForAllFiles && !started) {
            if (hasStoragePermission()) start() else { Toast.makeText(this, R.string.perm_denied, Toast.LENGTH_LONG).show(); finish() }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERM) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) start()
            else { Toast.makeText(this, R.string.perm_denied, Toast.LENGTH_LONG).show(); finish() }
        }
    }

    private var started = false

    private fun start() {
        started = true
        roots = listOf(Storage.internal(this)) + Storage.removable(this)
        val p = startPath?.let { File(it) }
        if (p != null && p.isDirectory) show(p) else showRoots()
    }

    // ---- navigation -------------------------------------------------------------------------

    private fun showRoots() {
        current = null
        pathView.text = ""
        submit(roots.map { Entry(it.label, it.dir, true, it.dir.path) })
    }

    private fun show(dir: File) {
        val files = dir.listFiles()
        if (files == null) {
            Toast.makeText(this, R.string.no_access, Toast.LENGTH_SHORT).show()
            return
        }
        current = dir
        pathView.text = dir.path
        val sorted = files.filter { !it.name.startsWith('.') }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
        submit(sorted.map {
            Entry(it.name, it, it.isDirectory, if (it.isDirectory) "" else Formatter.formatShortFileSize(this, it.length()))
        })
    }

    private fun submit(entries: List<Entry>, focusIndex: Int = 0) {
        adapter.items = entries
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        emptyView.setText(R.string.folder_empty)
        val idx = focusIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
        list.scrollToPosition(idx)
        focusAt(idx, 3)
    }

    private fun focusAt(idx: Int, retries: Int) {
        list.post {
            val v = list.findViewHolderForAdapterPosition(idx)?.itemView
            if (v != null) v.requestFocus() else if (retries > 0) focusAt(idx, retries - 1)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val dir = current
        if (dir == null) { finish(); return }
        val isRoot = roots.any { it.dir == dir }
        if (isRoot || dir.parentFile == null) {
            showRoots()
            return
        }
        val parent = dir.parentFile!!
        val files = parent.listFiles()
        if (files == null) { showRoots(); return }
        show(parent)
        // focus the folder we just left
        val idx = adapter.items.indexOfFirst { it.file == dir }
        if (idx >= 0) { list.scrollToPosition(idx); focusAt(idx, 3) }
    }

    // ---- actions ----------------------------------------------------------------------------

    private fun onClick(e: Entry) {
        val f = e.file ?: return
        if (e.isDir) show(f) else openFile(f)
    }

    private fun onLongClick(e: Entry) {
        val f = e.file ?: return
        if (e.isDir) return
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        labels += getString(R.string.open); actions += { openFile(f) }
        if (mimeOf(f).startsWith("image/")) {
            labels += getString(R.string.set_wallpaper); actions += { setWallpaper(f) }
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(f.name)
            .setItems(labels.toTypedArray()) { _, i -> actions[i]() }
            .show()
    }

    private fun mimeOf(f: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"

    private fun openFile(f: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mimeOf(f))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (e: Exception) {
            Toast.makeText(this, R.string.cant_open_file, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setWallpaper(f: File) {
        Executors.newSingleThreadExecutor().execute {
            val ok = Wallpapers.saveCustom(this, Uri.fromFile(f))
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (ok) Prefs(this).wallpaper = "custom"
                Toast.makeText(this, if (ok) R.string.wallpaper_set else R.string.wallpaper_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---- adapter ----------------------------------------------------------------------------

    private inner class FileAdapter : RecyclerView.Adapter<FileAdapter.VH>() {
        var items: List<Entry> = emptyList()

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
            Fonts.apply(v)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val e = items[position]
            h.name.text = e.name
            h.info.text = e.info
            h.icon.setImageResource(if (e.isDir) R.drawable.ic_folder else R.drawable.ic_file)
            h.icon.setColorFilter(getColor(if (e.isDir) R.color.accent else R.color.text_secondary))
            h.itemView.setOnClickListener { onClick(e) }
            h.itemView.setOnLongClickListener { onLongClick(e); true }
            h.itemView.setOnFocusChangeListener { _, has -> if (has) focusedIndex = h.bindingAdapterPosition }
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
