package com.mmlauncher.tv

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * The launcher's own "Developer mode" screen. Many boxes hide the "tap Build number 7 times" step
 * or have no such entry. With the WRITE_SECURE_SETTINGS permission (given once from a computer, or
 * automatic when installed as a system app) this screen can switch developer mode and USB
 * debugging on and off directly; without it, it explains the state and points to system screens.
 */
class DeveloperActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var hint: TextView
    private lateinit var row: LinearLayout

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(LocaleHelper.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_developer)
        status = findViewById(R.id.d_status)
        hint = findViewById(R.id.d_hint)
        row = findViewById(R.id.d_actions)
        Fonts.apply(window.decorView)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun devOn() = SettingsChains.isDeveloperModeOn(this)

    private fun adbOn() = try {
        Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
    } catch (e: Exception) { false }

    private fun onOff(v: Boolean) = getString(if (v) R.string.s_on else R.string.s_off)

    private fun chip(label: String, action: () -> Unit) {
        val v = LayoutInflater.from(this).inflate(R.layout.item_quick, row, false) as TextView
        v.text = label
        v.setOnClickListener { action() }
        Fonts.apply(v)
        row.addView(v)
    }

    private fun render() {
        val focusedIdx = row.indexOfChild(row.focusedChild)
        row.removeAllViews()
        val dev = devOn()
        val adb = adbOn()
        status.text = getString(R.string.dev_status_mode, onOff(dev)) + "\n" +
            getString(R.string.dev_status_usb, onOff(adb)) + "\n\n" +
            getString(R.string.dev_info, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Build.MODEL) + "\n" +
            getString(R.string.dev_build, Build.DISPLAY)

        if (Dump.hasWriteSecure(this)) {
            hint.visibility = android.view.View.GONE
            chip(getString(if (dev) R.string.dev_turn_off else R.string.dev_turn_on)) { setGlobal(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, !dev) }
            chip(getString(if (adb) R.string.dev_usb_off else R.string.dev_usb_on)) {
                // USB debugging only makes sense with developer mode on.
                if (!adb && !dev) setGlobal(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, true, quiet = true)
                setGlobal(Settings.Global.ADB_ENABLED, !adb)
            }
        } else {
            hint.text = getString(R.string.dev_need_perm, LocalAdb.ENABLE_HINT, Dump.GRANT_SECURE)
            hint.visibility = android.view.View.VISIBLE
            chip(getString(R.string.dev_local_adb)) { grantViaLocalAdb() }
        }
        if (dev) chip(getString(R.string.dev_open_system)) { open("developer") }
        chip(getString(R.string.dev_open_about)) { open("about") }
        chip(getString(R.string.q_system)) { open("all") }

        val idx = focusedIdx.coerceIn(0, row.childCount - 1)
        row.getChildAt(idx)?.requestFocus()
    }

    /** Asks the box's own adbd to grant this app WRITE_SECURE_SETTINGS (needs ADB over network). */
    private fun grantViaLocalAdb() {
        Toast.makeText(this, R.string.adb_working, Toast.LENGTH_SHORT).show()
        java.util.concurrent.Executors.newSingleThreadExecutor().execute {
            val out = LocalAdb.shell(this, "pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                val ok = out != null && Dump.hasWriteSecure(this)
                Toast.makeText(
                    this,
                    when {
                        ok -> getString(R.string.dev_changed)
                        LocalAdb.lastError == "denied" -> getString(R.string.adb_denied)
                        else -> getString(R.string.adb_unavailable, LocalAdb.ENABLE_HINT)
                    },
                    Toast.LENGTH_LONG,
                ).show()
                render()
            }
        }
    }

    private fun setGlobal(key: String, on: Boolean, quiet: Boolean = false) {
        try {
            Settings.Global.putInt(contentResolver, key, if (on) 1 else 0)
            if (!quiet) Toast.makeText(this, R.string.dev_changed, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.dev_denied, Toast.LENGTH_LONG).show()
        }
        render()
    }

    private fun open(key: String) {
        if (!SettingsChains.open(this, key)) Toast.makeText(this, R.string.cant_open_screen, Toast.LENGTH_SHORT).show()
    }
}
