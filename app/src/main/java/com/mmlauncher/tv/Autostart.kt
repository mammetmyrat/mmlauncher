package com.mmlauncher.tv

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/** FR-19: autostart fires once per device boot, not on every return to the home screen. */
object Autostart {

    /** Identifies the current boot. BOOT_COUNT where available, else boot time rounded to 5 min. */
    fun bootId(context: Context): Long {
        val count = try {
            Settings.Global.getInt(context.contentResolver, "boot_count", -1)
        } catch (e: Exception) {
            -1
        }
        if (count >= 0) return count.toLong()
        val bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        return bootTime / (5 * 60 * 1000L)
    }

    /** True when an app is configured and has not been auto-started during this boot yet. */
    fun isDue(context: Context, prefs: Prefs): Boolean =
        prefs.autostartPkg.isNotEmpty() && prefs.autostartBoot != bootId(context)
}
