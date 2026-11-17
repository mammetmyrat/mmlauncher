package com.mmlauncher.tv

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import java.io.File

/** Total / used / free storage (ROM) and the size of every app (data and cache too with DUMP). */
class StorageActivity : UsageActivity() {

    override val titleRes = R.string.q_storage

    override fun actions(): List<Pair<Int, () -> Unit>> = listOf(
        R.string.refresh to { refresh() },
        R.string.storage_settings to { openSystem("storage") },
        R.string.apps_settings to { openSystem("apps") },
    )

    private fun openSystem(key: String) {
        if (!SettingsChains.open(this, key)) Toast.makeText(this, R.string.cant_open_screen, Toast.LENGTH_SHORT).show()
    }

    private fun space(dir: File): Triple<Long, Long, Long>? = try {
        val s = StatFs(dir.path)
        val total = s.blockCountLong * s.blockSizeLong
        val free = s.availableBlocksLong * s.blockSizeLong
        Triple(total, total - free, free)
    } catch (e: Exception) {
        null
    }

    private fun line(name: String, t: Triple<Long, Long, Long>): String {
        val percent = if (t.first > 0) (t.second * 100 / t.first).toInt() else 0
        return getString(
            R.string.st_line, name,
            Formatter.formatFileSize(this, t.second), Formatter.formatFileSize(this, t.first), percent,
            Formatter.formatFileSize(this, t.third),
        )
    }

    override fun compute(): Result {
        val internal = space(Environment.getDataDirectory())
        val lines = ArrayList<String>()
        internal?.let { lines += line(getString(R.string.internal_storage), it) }
        for (v in Storage.removable(this)) space(v.dir)?.let { lines += line(v.label, it) }
        val percent = internal?.let { if (it.first > 0) (it.second * 100 / it.first).toInt() else 0 } ?: 0

        val sizes = Dump.diskstats(this)
        val items: List<Usage>
        val hint: String
        if (sizes != null) {
            val max = sizes.first().total.coerceAtLeast(1)
            items = sizes.take(80).map { a ->
                Usage(label(a.pkg), Formatter.formatShortFileSize(this, a.total), a.total.toFloat() / max, a.pkg)
            }
            hint = getString(R.string.st_tap)
        } else {
            val apks = apkSizes()
            val max = apks.firstOrNull()?.second?.coerceAtLeast(1) ?: 1
            items = apks.take(80).map { (pkg, size) ->
                Usage(label(pkg), Formatter.formatShortFileSize(this, size), size.toFloat() / max, pkg)
            }
            hint = Dump.whyMissing(this, R.string.st_need_dump)
        }
        return Result(lines.joinToString("\n\n"), percent, hint, getString(R.string.st_apps), items)
    }

    private fun label(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    /** What an ordinary app can measure: the installed size (APK files) of each app. */
    @Suppress("DEPRECATION")
    private fun apkSizes(): List<Pair<String, Long>> =
        packageManager.getInstalledApplications(0).map { info ->
            var size = File(info.sourceDir).length()
            info.splitSourceDirs?.forEach { size += File(it).length() }
            info.packageName to size
        }.sortedByDescending { it.second }

    override fun onItem(item: Usage) {
        val pkg = item.pkg ?: return
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Toast.makeText(this, R.string.cant_open_screen, Toast.LENGTH_SHORT).show()
        }
    }
}
