package com.mmlauncher.tv.presentation.wizard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.mmlauncher.tv.R
import com.mmlauncher.tv.appContainer
import com.mmlauncher.tv.domain.model.Language
import com.mmlauncher.tv.domain.model.Wallpaper
import com.mmlauncher.tv.presentation.common.BaseActivity
import com.mmlauncher.tv.presentation.common.Fonts

/** First-run wizard: language, wallpaper, update consent, "make me your home screen". */
class WizardActivity : BaseActivity() {

    private lateinit var store: WizardStore
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var row: LinearLayout
    private lateinit var back: TextView
    private lateinit var next: TextView
    private var shown: WizardState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wizard)
        store = appContainer.wizardStore()
        title = findViewById(R.id.wiz_title)
        body = findViewById(R.id.wiz_body)
        row = findViewById(R.id.wiz_row)
        back = findViewById(R.id.wiz_back)
        next = findViewById(R.id.wiz_next)
        back.setOnClickListener { store.dispatch(WizardIntent.Back) }
        next.setOnClickListener { store.dispatch(WizardIntent.Next) }
        Fonts.apply(window.decorView)
        store.dispatch(WizardIntent.Load(savedInstanceState?.getInt("page") ?: 0))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("page", store.state.page)
    }

    override fun onStart() {
        super.onStart()
        shown = null
        store.bind(::render, ::onEffect)
    }

    override fun onResume() {
        super.onResume()
        store.dispatch(WizardIntent.Refresh) // may have become the home screen meanwhile
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
    override fun onBackPressed() = store.dispatch(WizardIntent.Back)

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

    private fun render(s: WizardState) {
        if (s == shown) return
        val pageChanged = shown?.page != s.page
        shown = s

        row.removeAllViews()
        back.visibility = if (s.page == 0) View.INVISIBLE else View.VISIBLE
        next.setText(if (s.isLastPage) R.string.wiz_done else R.string.wiz_next)
        var focus: TextView? = null

        when (s.page) {
            0 -> {
                title.setText(R.string.wiz_lang_title)
                body.setText(R.string.wiz_lang_body)
                val options = listOf(
                    Language.SYSTEM to getString(R.string.lang_system),
                    Language.RU to "Русский", Language.TK to "Türkmençe", Language.EN to "English",
                )
                for ((lang, label) in options) {
                    val c = chip(label, s.language == lang) { store.dispatch(WizardIntent.SelectLanguage(lang)) }
                    if (s.language == lang) focus = c
                }
            }
            1 -> {
                title.setText(R.string.wallpaper)
                body.setText(R.string.wiz_wall_body)
                val names = listOf(
                    Wallpaper.NONE to R.string.wp_none, Wallpaper.NIGHT to R.string.wp_night, Wallpaper.OCEAN to R.string.wp_ocean,
                    Wallpaper.VIOLET to R.string.wp_violet, Wallpaper.SUNSET to R.string.wp_sunset,
                )
                for ((w, res) in names) {
                    val c = chip(getString(res), s.wallpaper == w) { store.dispatch(WizardIntent.SelectWallpaper(w)) }
                    if (s.wallpaper == w) focus = c
                }
            }
            2 -> {
                title.setText(R.string.wiz_updates_title)
                body.setText(R.string.wiz_updates_body)
                focus = chip(getString(R.string.yes), s.autoUpdates) { store.dispatch(WizardIntent.SetAutoUpdates(true)) }
                chip(getString(R.string.no), !s.autoUpdates) { store.dispatch(WizardIntent.SetAutoUpdates(false)) }
            }
            else -> {
                title.setText(R.string.wiz_home_title)
                if (s.isDefaultHome) {
                    body.setText(R.string.wiz_home_ok)
                } else {
                    body.setText(R.string.wiz_home_body)
                    focus = chip(getString(R.string.wiz_open_home_settings), false) { store.dispatch(WizardIntent.OpenHomeSettings) }
                }
            }
        }
        if (pageChanged || focus != null) (focus ?: if (row.childCount > 0) row.getChildAt(0) else next).requestFocus()
    }

    private fun onEffect(effect: WizardEffect) {
        when (effect) {
            WizardEffect.Recreate -> recreate()
            WizardEffect.Finish -> finish()
            is WizardEffect.Toast -> Toast.makeText(this, effect.msg.text(this), Toast.LENGTH_SHORT).show()
        }
    }
}
