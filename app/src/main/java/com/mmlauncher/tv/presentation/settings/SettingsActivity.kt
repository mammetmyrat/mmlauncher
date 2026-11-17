package com.mmlauncher.tv.presentation.settings

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.mmlauncher.tv.R
import com.mmlauncher.tv.appContainer
import com.mmlauncher.tv.domain.model.AppInfo
import com.mmlauncher.tv.domain.model.Language
import com.mmlauncher.tv.domain.model.Wallpaper
import com.mmlauncher.tv.domain.usecase.LauncherSettings
import com.mmlauncher.tv.presentation.common.BaseActivity
import com.mmlauncher.tv.presentation.common.Fonts
import com.mmlauncher.tv.presentation.common.IconLoader
import com.mmlauncher.tv.presentation.wizard.WizardActivity

/** Launcher's own settings. Renders [SettingsState]; every click becomes a [SettingsIntent]. */
class SettingsActivity : BaseActivity() {

    private lateinit var store: SettingsStore
    private var shown: LauncherSettings? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = appContainer.settingsStore()
        Fonts.apply(window.decorView)
    }

    override fun onStart() {
        super.onStart()
        store.bind(::render, ::onEffect)
        store.dispatch(SettingsIntent.Load)
    }

    override fun onStop() {
        super.onStop()
        store.unbind()
    }

    override fun onDestroy() {
        super.onDestroy()
        store.clear()
    }

    private fun chip(parent: LinearLayout, text: String, selected: Boolean, onClick: () -> Unit): TextView {
        val v = LayoutInflater.from(this).inflate(R.layout.item_quick, parent, false) as TextView
        v.text = text
        v.isSelected = selected
        v.setOnClickListener { onClick() }
        Fonts.apply(v)
        parent.addView(v)
        return v
    }

    /** Rebuilds a row of chips and keeps focus where the user was. */
    private fun row(id: Int, build: (LinearLayout) -> Unit) {
        val row = findViewById<LinearLayout>(id)
        val focusedIdx = row.indexOfChild(row.focusedChild)
        row.removeAllViews()
        build(row)
        if (focusedIdx >= 0 && row.childCount > 0) row.getChildAt(focusedIdx.coerceAtMost(row.childCount - 1)).requestFocus()
    }

    private fun render(state: SettingsState) {
        val s = state.settings ?: return
        if (s == shown) return
        val first = shown == null
        shown = s

        findViewById<TextView>(R.id.about).text = getString(R.string.about_line, getString(R.string.app_name), s.versionName)

        row(R.id.lang_row) { r ->
            val options = listOf(
                Language.SYSTEM to getString(R.string.lang_system),
                Language.RU to "Русский", Language.TK to "Türkmençe", Language.EN to "English",
            )
            for ((lang, label) in options) {
                val v = chip(r, label, s.language == lang) { store.dispatch(SettingsIntent.SelectLanguage(lang)) }
                if (first && s.language == lang) v.requestFocus()
            }
        }

        row(R.id.wall_row) { r ->
            val names = listOf(
                Wallpaper.NONE to R.string.wp_none, Wallpaper.NIGHT to R.string.wp_night, Wallpaper.OCEAN to R.string.wp_ocean,
                Wallpaper.VIOLET to R.string.wp_violet, Wallpaper.SUNSET to R.string.wp_sunset,
            )
            for ((w, res) in names) chip(r, getString(res), s.wallpaper == w) { store.dispatch(SettingsIntent.SelectWallpaper(w)) }
            chip(r, getString(R.string.wp_custom), s.wallpaper == Wallpaper.CUSTOM) { pickImage() }
        }

        row(R.id.auto_row) { r ->
            val on = s.autostartPackage.isNotEmpty()
            chip(r, getString(R.string.as_off), !on) { store.dispatch(SettingsIntent.AutostartOff) }
            chip(r, if (s.autostartLabel != null) getString(R.string.as_app, s.autostartLabel) else getString(R.string.as_choose), on) {
                store.dispatch(SettingsIntent.ChooseAutostartApp)
            }
            if (on) for (sec in listOf(0, 3, 5, 10)) {
                chip(r, getString(R.string.as_delay, sec), s.autostartDelaySeconds == sec) {
                    store.dispatch(SettingsIntent.SetAutostartDelay(sec))
                }
            }
        }

        row(R.id.mem_row) { r ->
            chip(r, getString(R.string.clear_cache), false) { store.dispatch(SettingsIntent.ClearCache) }
            // Other apps' caches can only be cleared by the user in system screens.
            chip(r, getString(R.string.storage_settings), false) { store.dispatch(SettingsIntent.OpenStorageScreen) }
            chip(r, getString(R.string.apps_settings), false) { store.dispatch(SettingsIntent.OpenAppsScreen) }
        }

        row(R.id.upd_row) { r ->
            chip(r, getString(if (s.autoUpdates) R.string.upd_auto_on else R.string.upd_auto_off), s.autoUpdates) {
                store.dispatch(SettingsIntent.ToggleAutoUpdates)
            }
            chip(r, getString(R.string.upd_check), false) { store.dispatch(SettingsIntent.CheckNow) }
            chip(r, getString(R.string.setup_guide), false) { startActivity(Intent(this, WizardActivity::class.java)) }
        }
    }

    private fun onEffect(effect: SettingsEffect) {
        when (effect) {
            is SettingsEffect.Toast -> Toast.makeText(this, effect.msg.text(this), Toast.LENGTH_LONG).show()
            SettingsEffect.Recreate -> recreate() // re-applies the locale via attachBaseContext
            is SettingsEffect.ShowAppPicker -> showAppPicker(effect.apps)
            SettingsEffect.CacheCleared -> IconLoader.clearAll()
        }
    }

    private fun showAppPicker(apps: List<AppInfo>) {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.autostart)
            .setItems(apps.map { it.label }.toTypedArray()) { _, which ->
                store.dispatch(SettingsIntent.AutostartAppChosen(apps[which].packageName))
            }
            .show()
    }

    // ---- custom wallpaper picker (platform) -------------------------------------------------

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
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
        if (requestCode == REQ_IMAGE && resultCode == RESULT_OK && uri != null) {
            store.dispatch(SettingsIntent.WallpaperPicked(uri.toString()))
        }
    }

    private companion object { const val REQ_IMAGE = 1 }
}
