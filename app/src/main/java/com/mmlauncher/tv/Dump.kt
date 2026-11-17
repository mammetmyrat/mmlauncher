package com.mmlauncher.tv

import android.content.Context

/**
 * Per-app memory / storage numbers. An ordinary app cannot read them, so they are fetched through
 * [LocalAdb] (the box's own adbd, shell user). If that is not available the screens fall back to
 * what an ordinary app may see.
 */
object Dump {

    const val GRANT_SECURE = "adb shell pm grant com.mmlauncher.tv android.permission.WRITE_SECURE_SETTINGS"
    const val GRANT_FILES = "adb shell appops set com.mmlauncher.tv MANAGE_EXTERNAL_STORAGE allow"

    fun hasWriteSecure(context: Context) =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Text for a hint line explaining why per-app numbers are missing and what to do. */
    fun whyMissing(context: Context, needAdbRes: Int): String = when (LocalAdb.lastError) {
        "denied" -> context.getString(R.string.adb_denied)
        "nodata" -> context.getString(R.string.adb_nodata)
        "error" -> context.getString(R.string.adb_failed)
        else -> context.getString(needAdbRes, LocalAdb.ENABLE_HINT)
    }

    /** One process or package with its PSS in KB. */
    data class ProcMem(val name: String, val pssKb: Long)

    private val PSS_LINE = Regex("""^\s*([\d,.]+)K: (\S+) \(pid (\d+)""")

    /** `dumpsys meminfo` -> "Total PSS by process" aggregated per package. */
    fun meminfo(context: Context): List<ProcMem>? {
        val text = LocalAdb.shell(context, "dumpsys meminfo") ?: return null
        val totals = LinkedHashMap<String, Long>()
        var inSection = false
        for (line in text.lineSequence()) {
            if (line.startsWith("Total PSS by process")) { inSection = true; continue }
            if (inSection && line.startsWith("Total PSS by")) break
            if (!inSection) continue
            val m = PSS_LINE.find(line) ?: continue
            val kb = m.groupValues[1].replace(",", "").replace(".", "").toLongOrNull() ?: continue
            val pkg = m.groupValues[2].substringBefore(':')
            totals[pkg] = (totals[pkg] ?: 0L) + kb
        }
        if (totals.isEmpty()) { LocalAdb.lastError = "error"; return null }
        return totals.map { ProcMem(it.key, it.value) }.sortedByDescending { it.pssKb }
    }

    /** One app with its sizes in bytes. */
    data class AppSize(val pkg: String, val app: Long, val data: Long, val cache: Long) {
        val total get() = app + data + cache
    }

    /** `dumpsys diskstats` -> per package app / data / cache sizes. */
    fun diskstats(context: Context): List<AppSize>? {
        val text = LocalAdb.shell(context, "dumpsys diskstats", runMs = 60000) ?: return null
        fun array(name: String): List<String>? {
            val m = Regex("""$name: \[(.*)]""").find(text) ?: return null
            return m.groupValues[1].split(',').map { it.trim().trim('"') }.filter { it.isNotEmpty() }
        }
        val names = array("Package Names")
        val app = array("App Sizes")?.map { it.toLongOrNull() ?: 0L }
        if (names == null || app == null || names.isEmpty() || app.size < names.size) {
            LocalAdb.lastError = "nodata" // older Android versions do not report per-app sizes
            return null
        }
        val data = array("App Data Sizes")?.map { it.toLongOrNull() ?: 0L } ?: List(names.size) { 0L }
        val cache = array("Cache Sizes")?.map { it.toLongOrNull() ?: 0L } ?: List(names.size) { 0L }
        return names.indices.map { i ->
            AppSize(names[i], app[i], data.getOrElse(i) { 0L }, cache.getOrElse(i) { 0L })
        }.sortedByDescending { it.total }
    }
}
