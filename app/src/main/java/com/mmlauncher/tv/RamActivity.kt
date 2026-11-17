package com.mmlauncher.tv

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.text.format.Formatter
import android.widget.Toast
import java.util.concurrent.Executors

/** Total / used / available RAM and, with the DUMP permission, how much each app uses. */
class RamActivity : UsageActivity() {

    override val titleRes = R.string.q_ram

    override fun actions(): List<Pair<Int, () -> Unit>> = listOf(
        R.string.ram_free_btn to { freeMemory() },
        R.string.refresh to { refresh() },
    )

    override fun compute(): Result {
        val ram = Memory.ram(this)
        val mb = { v: Int -> Formatter.formatFileSize(this, v * 1024L * 1024L) }
        val summary = getString(R.string.ram_total, mb(ram.totalMb)) + "\n" +
            getString(R.string.ram_used, mb(ram.usedMb), ram.usedPercent) + "\n" +
            getString(R.string.ram_free, mb(ram.availMb))

        val procs = Dump.meminfo(this)
        val items: List<Usage>
        val hint: String?
        if (procs != null) {
            val max = procs.first().pssKb.coerceAtLeast(1)
            items = procs.take(80).map { p ->
                Usage(label(p.name), Formatter.formatShortFileSize(this, p.pssKb * 1024), p.pssKb.toFloat() / max, p.name)
            }
            hint = getString(R.string.ram_tap_stop)
        } else {
            val own = ownPssKb()
            items = listOf(Usage(getString(R.string.ram_own), Formatter.formatShortFileSize(this, own * 1024), 1f, null))
            hint = Dump.whyMissing(this, R.string.ram_need_dump)
        }
        return Result(summary, ram.usedPercent, hint, getString(R.string.ram_apps), items)
    }

    private fun label(name: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(name, 0)).toString()
    } catch (e: Exception) {
        name // system process (system_server, surfaceflinger, ...)
    }

    private fun ownPssKb(): Long {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.getProcessMemoryInfo(intArrayOf(Process.myPid())).firstOrNull()?.totalPss?.toLong() ?: 0L
    }

    private fun freeMemory() {
        Executors.newSingleThreadExecutor().execute {
            val freed = Memory.boost(this)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                Toast.makeText(
                    this,
                    if (freed > 0) getString(R.string.boost_freed, freed) else getString(R.string.boost_none),
                    Toast.LENGTH_SHORT,
                ).show()
                refresh()
            }
        }
    }

    /** Select an app: stop its background process (what an ordinary app is allowed to do). */
    override fun onItem(item: Usage) {
        val pkg = item.pkg ?: return
        if (pkg == packageName) return
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        try { am.killBackgroundProcesses(pkg) } catch (_: Exception) {}
        Toast.makeText(this, getString(R.string.ram_stopped, item.label), Toast.LENGTH_SHORT).show()
        window.decorView.postDelayed({ if (!isDestroyed) refresh() }, 1200)
    }
}
