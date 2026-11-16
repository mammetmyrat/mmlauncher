package com.mmlauncher.tv

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo

/** FR-20 / FR-21: RAM numbers and an honest "free memory" action. */
object Memory {

    data class Ram(val totalMb: Int, val availMb: Int) {
        val usedMb get() = totalMb - availMb
        val usedPercent get() = if (totalMb > 0) (usedMb * 100 / totalMb) else 0
    }

    fun ram(context: Context): Ram {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return Ram((mi.totalMem / MB).toInt(), (mi.availMem / MB).toInt())
    }

    /**
     * Asks the system to stop background processes of user-installed apps (the only thing a normal
     * app may do). Apps can restart themselves, so the result is reported as measured, not promised.
     * Blocks ~1 s, call off the UI thread. Returns freed MB (>= 0).
     */
    fun boost(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = ram(context).availMb
        val pm = context.packageManager
        for (pkg in pm.getInstalledPackages(0)) {
            val info = pkg.applicationInfo ?: continue
            if (pkg.packageName == context.packageName) continue
            val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
            val updatedSystem = info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
            if (system && !updatedSystem) continue // never touch plain system apps
            try { am.killBackgroundProcesses(pkg.packageName) } catch (_: Exception) {}
        }
        Thread.sleep(900) // let the kernel reclaim memory before measuring
        return (ram(context).availMb - before).coerceAtLeast(0)
    }

    private const val MB = 1024L * 1024L
}
