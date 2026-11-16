package com.mmlauncher.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** First-run wizard: language, wallpaper, update consent, "make me your home screen". */
class WizardActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var row: LinearLayout
    private lateinit var back: TextView
    private lateinit var next: TextView
    private var page = 0

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(LocaleHelper.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wizard)
        prefs = Prefs(this)
        title = findViewById(R.id.wiz_title)
        body = findViewById(R.id.wiz_body)
        row = findViewById(R.id.wiz_row)
        back = findViewById(R.id.wiz_back)
        next = findViewById(R.id.wiz_next)
        page = savedInstanceState?.getInt("page") ?: 0
        back.setOnClickListener { if (page > 0) { page--; render() } }
        next.setOnClickListener { if (page < LAST) { page++; render() } else finishWizard() }
        Fonts.apply(window.decorView)
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("page", page)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (page > 0) { page--; render() } else finishWizard()
    }

    private fun finishWizard() {
        prefs.wizardDone = true
        finish()
    }

    private fun chip(text: String, selected: Boolean, onClick: () -> Unit): TextView {
        val v = LayoutInflater.from(this).inflate(R.layout.item_quick, row, false) as TextView
        v.text = text
        v.isSelected = selected
        v.setOnClickListener { onClick() }
        v.nextFocusDownId = R.id.wiz_next
        Fonts.apply(v)
        row.addView(v)
        return v
    }

    private fun render() {
        row.removeAllViews()
        back.visibility = if (page == 0) android.view.View.INVISIBLE else android.view.View.VISIBLE
        next.setText(if (page == LAST) R.string.wiz_done else R.string.wiz_next)
        var focus: TextView? = null

        when (page) {
            0 -> {
                title.setText(R.string.wiz_lang_title)
                body.setText(R.string.wiz_lang_body)
                for ((code, label) in listOf("system" to getString(R.string.lang_system), "ru" to "Русский", "tk" to "Türkmençe", "en" to "English")) {
                    val c = chip(label, prefs.language == code) {
                        if (prefs.language != code) { prefs.language = code; recreate() }
                    }
                    if (prefs.language == code) focus = c
                }
            }
            1 -> {
                title.setText(R.string.wallpaper)
                body.setText(R.string.wiz_wall_body)
                val names = linkedMapOf(
                    "none" to R.string.wp_none, "night" to R.string.wp_night, "ocean" to R.string.wp_ocean,
                    "violet" to R.string.wp_violet, "sunset" to R.string.wp_sunset,
                )
                for ((key, res) in names) {
                    val c = chip(getString(res), prefs.wallpaper == key) {
                        prefs.wallpaper = key
                        for (i in 0 until row.childCount) row.getChildAt(i).isSelected = (row.getChildAt(i).tag == key)
                    }
                    c.tag = key
                    if (prefs.wallpaper == key) focus = c
                }
            }
            2 -> {
                title.setText(R.string.wiz_updates_title)
                body.setText(R.string.wiz_updates_body)
                focus = chip(getString(R.string.yes), prefs.autoUpdates) {
                    prefs.autoUpdates = true; page++; render()
                }
                chip(getString(R.string.no), !prefs.autoUpdates) {
                    prefs.autoUpdates = false; page++; render()
                }
            }
            else -> {
                title.setText(R.string.wiz_home_title)
                if (isDefaultHome()) {
                    body.setText(R.string.wiz_home_ok)
                } else {
                    body.setText(R.string.wiz_home_body)
                    focus = chip(getString(R.string.wiz_open_home_settings), false) { openHomeSettings() }
                }
            }
        }
        (focus ?: if (row.childCount > 0) row.getChildAt(0) else next).requestFocus()
    }

    private fun isDefaultHome(): Boolean = try {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.resolveActivity(home, 0)?.activityInfo?.packageName == packageName
    } catch (e: Exception) {
        false
    }

    private fun openHomeSettings() {
        val tries = listOf(Settings.ACTION_HOME_SETTINGS, Settings.ACTION_SETTINGS)
        for (action in tries) {
            try {
                startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {}
        }
        Toast.makeText(this, R.string.cant_open_screen, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val LAST = 3
    }
}
