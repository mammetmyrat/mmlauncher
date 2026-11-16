package com.mmlauncher.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/** Launcher's own settings: language and wallpaper (more arrive in later stages). */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var langRow: LinearLayout
    private lateinit var wallRow: LinearLayout
    private val io = Executors.newSingleThreadExecutor()

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(LocaleHelper.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)
        langRow = findViewById(R.id.lang_row)
        wallRow = findViewById(R.id.wall_row)
        findViewById<TextView>(R.id.about).text =
            getString(R.string.about_line, getString(R.string.app_name), BuildConfigVersion.name(this))
        buildLanguageRow()
        buildWallpaperRow()
        buildAutostartRow()
        buildMemoryRow()
        buildUpdatesRow()
        Fonts.apply(window.decorView)
    }

    private fun chip(parent: LinearLayout, text: String, selected: Boolean, onClick: () -> Unit): TextView {
        val v = LayoutInflater.from(this).inflate(R.layout.item_quick, parent, false) as TextView
        v.text = text
        v.isSelected = selected
        v.setOnClickListener { onClick() }
        parent.addView(v)
        return v
    }

    // ---- language ---------------------------------------------------------------------------

    private fun buildLanguageRow() {
        langRow.removeAllViews()
        val options = listOf(
            "system" to getString(R.string.lang_system),
            "ru" to "Русский",
            "tk" to "Türkmençe",
            "en" to "English",
        )
        var focus: TextView? = null
        for ((code, label) in options) {
            val v = chip(langRow, label, prefs.language == code) {
                if (prefs.language != code) {
                    prefs.language = code
                    recreate() // re-applies the locale via attachBaseContext
                }
            }
            if (prefs.language == code) focus = v
        }
        focus?.requestFocus()
    }

    // ---- autostart (FR-17, FR-18) ----------------------------------------------------------

    private fun buildAutostartRow() {
        val row = findViewById<LinearLayout>(R.id.auto_row)
        val focusedIdx = row.indexOfChild(row.focusedChild) // keep focus where the user was
        row.removeAllViews()
        val pkg = prefs.autostartPkg
        val label = if (pkg.isEmpty()) null else appLabel(pkg)

        chip(row, getString(R.string.as_off), pkg.isEmpty()) {
            prefs.autostartPkg = ""
            buildAutostartRow()
        }
        chip(row, if (label != null) getString(R.string.as_app, label) else getString(R.string.as_choose), pkg.isNotEmpty()) {
            chooseAutostartApp()
        }
        if (pkg.isNotEmpty()) {
            for (sec in listOf(0, 3, 5, 10)) {
                chip(row, getString(R.string.as_delay, sec), prefs.autostartDelay == sec) {
                    prefs.autostartDelay = sec
                    buildAutostartRow()
                }
            }
        }
        if (focusedIdx >= 0 && row.childCount > 0) row.getChildAt(focusedIdx.coerceAtMost(row.childCount - 1)).requestFocus()
    }

    private fun appLabel(pkg: String): String? = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        null
    }

    private fun chooseAutostartApp() {
        io.execute {
            val apps = AppRepository.load(this)
            runOnUiThread {
                if (isDestroyed || apps.isEmpty()) return@runOnUiThread
                AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(R.string.autostart)
                    .setItems(apps.map { it.label }.toTypedArray()) { _, which ->
                        prefs.autostartPkg = apps[which].packageName
                        // Do not fire right now: treat the current boot as already handled.
                        prefs.autostartBoot = Autostart.bootId(this)
                        buildAutostartRow()
                    }
                    .show()
            }
        }
    }

    // ---- updates and setup guide -----------------------------------------------------------

    private fun buildUpdatesRow() {
        val row = findViewById<LinearLayout>(R.id.upd_row)
        val focusedIdx = row.indexOfChild(row.focusedChild)
        row.removeAllViews()
        chip(row, getString(if (prefs.autoUpdates) R.string.upd_auto_on else R.string.upd_auto_off), prefs.autoUpdates) {
            prefs.autoUpdates = !prefs.autoUpdates
            buildUpdatesRow()
        }
        chip(row, getString(R.string.upd_check), false) { checkNow() }
        chip(row, getString(R.string.setup_guide), false) { startActivity(Intent(this, WizardActivity::class.java)) }
        if (focusedIdx >= 0) row.getChildAt(focusedIdx.coerceAtMost(row.childCount - 1)).requestFocus()
    }

    /** A manual check is an explicit user action, so it does not depend on the auto-check consent. */
    private fun checkNow() {
        Toast.makeText(this, R.string.upd_checking, Toast.LENGTH_SHORT).show()
        io.execute {
            val result = UpdateChecker.check(this, prefs)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                val msg = when (result) {
                    is UpdateChecker.Result.Available -> getString(R.string.upd_available, result.update.versionName)
                    UpdateChecker.Result.UpToDate -> getString(R.string.upd_latest)
                    UpdateChecker.Result.Failed -> getString(R.string.upd_failed)
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---- memory (FR-22, FR-23) -------------------------------------------------------------

    private fun buildMemoryRow() {
        val row = findViewById<LinearLayout>(R.id.mem_row)
        row.removeAllViews()
        chip(row, getString(R.string.clear_cache), false) {
            IconLoader.clearAll()
            cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            Toast.makeText(this, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
        }
        // Other apps' caches can only be cleared by the user in system screens.
        chip(row, getString(R.string.storage_settings), false) { openSystem("storage") }
        chip(row, getString(R.string.apps_settings), false) { openSystem("apps") }
    }

    private fun openSystem(key: String) {
        if (!SettingsChains.open(this, key)) Toast.makeText(this, R.string.cant_open_screen, Toast.LENGTH_SHORT).show()
    }

    // ---- wallpaper --------------------------------------------------------------------------

    private fun buildWallpaperRow() {
        wallRow.removeAllViews()
        val names = mapOf(
            "none" to R.string.wp_none, "night" to R.string.wp_night, "ocean" to R.string.wp_ocean,
            "violet" to R.string.wp_violet, "sunset" to R.string.wp_sunset,
        )
        for ((key, res) in names) {
            chip(wallRow, getString(res), prefs.wallpaper == key) {
                prefs.wallpaper = key
                refreshWallpaperSelection()
            }.tag = key
        }
        chip(wallRow, getString(R.string.wp_custom), prefs.wallpaper == "custom") { pickImage() }.tag = "custom"
    }

    private fun refreshWallpaperSelection() {
        for (i in 0 until wallRow.childCount) {
            val v = wallRow.getChildAt(i)
            v.isSelected = v.tag == prefs.wallpaper
        }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
        try {
            startActivityForResult(intent, REQ_IMAGE)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.no_picker, Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQ_IMAGE || resultCode != RESULT_OK || uri == null) return
        io.execute {
            val ok = Wallpapers.saveCustom(this, uri)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (ok) {
                    prefs.wallpaper = "custom"
                    refreshWallpaperSelection()
                } else {
                    Toast.makeText(this, R.string.wallpaper_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private companion object {
        const val REQ_IMAGE = 1
    }
}

/** versionName without BuildConfig generation (keeps the build config minimal). */
object BuildConfigVersion {
    fun name(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (e: Exception) {
        ""
    }
}
