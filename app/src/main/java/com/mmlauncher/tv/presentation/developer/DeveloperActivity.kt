package com.mmlauncher.tv.presentation.developer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.mmlauncher.tv.R
import com.mmlauncher.tv.appContainer
import com.mmlauncher.tv.domain.model.SystemScreen
import com.mmlauncher.tv.presentation.common.BaseActivity
import com.mmlauncher.tv.presentation.common.Fonts
import com.mmlauncher.tv.presentation.common.Hints

/** Own "Developer mode" screen: shows the state and, with WRITE_SECURE_SETTINGS, switches it directly. */
class DeveloperActivity : BaseActivity() {

    private lateinit var store: DeveloperStore
    private lateinit var status: TextView
    private lateinit var hint: TextView
    private lateinit var row: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_developer)
        store = appContainer.developerStore()
        status = findViewById(R.id.d_status)
        hint = findViewById(R.id.d_hint)
        row = findViewById(R.id.d_actions)
        Fonts.apply(window.decorView)
    }

    override fun onStart() {
        super.onStart()
        store.bind(::render, ::onEffect)
    }

    override fun onResume() {
        super.onResume()
        store.dispatch(DeveloperIntent.Load) // state may have changed in a system screen
    }

    override fun onStop() {
        super.onStop()
        store.unbind()
    }

    override fun onDestroy() {
        super.onDestroy()
        store.clear()
    }

    private fun chip(label: String, intent: DeveloperIntent) {
        val v = LayoutInflater.from(this).inflate(R.layout.item_quick, row, false) as TextView
        v.text = label
        v.setOnClickListener { store.dispatch(intent) }
        Fonts.apply(v)
        row.addView(v)
    }

    private fun onOff(v: Boolean) = getString(if (v) R.string.s_on else R.string.s_off)

    private fun render(state: DeveloperState) {
        val s = state.status ?: return
        val focusedIdx = row.indexOfChild(row.focusedChild)
        row.removeAllViews()
        status.text = getString(R.string.dev_status_mode, onOff(s.developerMode)) + "\n" +
            getString(R.string.dev_status_usb, onOff(s.usbDebugging)) + "\n\n" +
            getString(R.string.dev_info, s.device.release, s.device.sdk, s.device.model) + "\n" +
            getString(R.string.dev_build, s.device.build)

        if (s.canWriteSecure) {
            hint.visibility = View.GONE
            chip(getString(if (s.developerMode) R.string.dev_turn_off else R.string.dev_turn_on), DeveloperIntent.ToggleDeveloper)
            chip(getString(if (s.usbDebugging) R.string.dev_usb_off else R.string.dev_usb_on), DeveloperIntent.ToggleUsb)
        } else {
            chip(getString(R.string.dev_local_adb), DeveloperIntent.GrantViaAdb)
        }
        // Some firmwares hide the developer screen or the "Build number" row: let the user try every way in.
        chip(getString(R.string.dev_open_system), DeveloperIntent.OpenSystem(SystemScreen.DEVELOPER))
        chip(getString(R.string.dev_open_about), DeveloperIntent.OpenSystem(SystemScreen.ABOUT))
        if (!s.developerMode) chip(getString(R.string.dev_open_stock), DeveloperIntent.OpenSystem(SystemScreen.ABOUT_STOCK))

        val hints = ArrayList<String>()
        if (!s.canWriteSecure) hints += getString(R.string.dev_need_perm, Hints.ENABLE_TCPIP, Hints.GRANT_SECURE)
        if (!s.developerMode) hints += getString(R.string.dev_tips)
        hint.text = hints.joinToString("\n\n")
        hint.visibility = if (hints.isEmpty()) View.GONE else View.VISIBLE
        chip(getString(R.string.q_system), DeveloperIntent.OpenSystem(SystemScreen.ALL))

        row.getChildAt(focusedIdx.coerceIn(0, row.childCount - 1))?.requestFocus()
    }

    private fun onEffect(effect: DeveloperEffect) {
        when (effect) {
            is DeveloperEffect.Toast -> Toast.makeText(this, effect.msg.text(this), Toast.LENGTH_LONG).show()
        }
    }
}
