package com.mmlauncher.tv.data.diagnostics

/** Pure text parsers for `dumpsys` output (unit-tested, no Android types). */
object DumpParsers {

    data class Pss(val name: String, val kb: Long)

    private val PSS_LINE = Regex("""^\s*([\d,.]+)K: (\S+) \(pid (\d+)""")

    /** "Total PSS by process" section of `dumpsys meminfo`, aggregated per package. Null if absent. */
    fun meminfo(text: String): List<Pss>? {
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
        if (totals.isEmpty()) return null
        return totals.map { Pss(it.key, it.value) }.sortedByDescending { it.kb }
    }

    data class DiskApp(val packageName: String, val appBytes: Long, val dataBytes: Long, val cacheBytes: Long) {
        val total get() = appBytes + dataBytes + cacheBytes
    }

    /** Per-package sizes from `dumpsys diskstats`. Null if this Android version does not list them. */
    fun diskstats(text: String): List<DiskApp>? {
        fun array(name: String): List<String>? {
            val m = Regex("""$name: \[(.*)]""").find(text) ?: return null
            return m.groupValues[1].split(',').map { it.trim().trim('"') }.filter { it.isNotEmpty() }
        }
        val names = array("Package Names")
        val app = array("App Sizes")?.map { it.toLongOrNull() ?: 0L }
        if (names == null || app == null || names.isEmpty() || app.size < names.size) return null
        val data = array("App Data Sizes")?.map { it.toLongOrNull() ?: 0L } ?: List(names.size) { 0L }
        val cache = array("Cache Sizes")?.map { it.toLongOrNull() ?: 0L } ?: List(names.size) { 0L }
        return names.indices.map { i -> DiskApp(names[i], app[i], data.getOrElse(i) { 0L }, cache.getOrElse(i) { 0L }) }
    }
}
