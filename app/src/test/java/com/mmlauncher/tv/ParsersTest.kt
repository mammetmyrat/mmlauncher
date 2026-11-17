package com.mmlauncher.tv

import com.mmlauncher.tv.data.diagnostics.DumpParsers
import com.mmlauncher.tv.data.update.UpdateManifest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ParsersTest {

    private val meminfo = """
        Applications Memory Usage (in Kilobytes):
        Uptime: 1 Realtime: 1

        Total PSS by process:
            123,456K: system (pid 1234)
             65,432K: com.example.tv (pid 2000 / activities)
             10,000K: com.example.tv:service (pid 2001)
              5,000K: surfaceflinger (pid 300)

        Total PSS by OOM adjustment:
            999,999K: Native
    """.trimIndent()

    @Test fun `meminfo is aggregated per package and sorted`() {
        val r = DumpParsers.meminfo(meminfo)!!
        assertEquals(listOf("system", "com.example.tv", "surfaceflinger"), r.map { it.name })
        assertEquals(65_432L + 10_000L, r[1].kb)          // ":service" process merged into the package
        assertEquals(123_456L, r[0].kb)
    }

    @Test fun `meminfo without the section is null`() {
        assertNull(DumpParsers.meminfo("Can't find service: meminfo"))
    }

    @Test fun `diskstats lists per package sizes`() {
        val text = """
            Data-Free: 100K / 200K total = 50% free
            Package Names: ["com.a","com.b"]
            App Sizes: [1000,2000]
            App Data Sizes: [10,20]
            Cache Sizes: [1,2]
        """.trimIndent()
        val r = DumpParsers.diskstats(text)!!
        assertEquals(2, r.size)
        assertEquals(1011L, r[0].total)
        assertEquals("com.b", r[1].packageName)
    }

    @Test fun `diskstats on old Android has no per-app data`() {
        assertNull(DumpParsers.diskstats("Latency: 1ms [512B Data Write]\nData-Free: 1K / 2K total = 50% free"))
    }

    private fun manifest(code: Int, apk: String = "https://x/a.apk") = JSONObject(
        """{"versionCode":$code,"versionName":"v$code","apkUrl":"$apk","sha256":"ABCDEF",
            "notes":{"en":"English","ru":"Русский"},"chains":{"version":3,"url":"https://x/c.json"}}"""
    )

    @Test fun `only a newer version is offered`() {
        assertNull(UpdateManifest.parseUpdate(manifest(1), currentVersionCode = 1, language = "en"))
        assertNull(UpdateManifest.parseUpdate(manifest(1), currentVersionCode = 2, language = "en"))
        val u = UpdateManifest.parseUpdate(manifest(2), currentVersionCode = 1, language = "en")!!
        assertEquals("v2", u.versionName)
        assertEquals("abcdef", u.sha256)                     // normalised to lower case
    }

    @Test fun `an update without a download address is ignored`() {
        assertNull(UpdateManifest.parseUpdate(manifest(5, apk = ""), 1, "en"))
    }

    @Test fun `notes follow the language and fall back to English`() {
        assertEquals("Русский", UpdateManifest.parseUpdate(manifest(2), 1, "ru")!!.notes)
        assertEquals("English", UpdateManifest.parseUpdate(manifest(2), 1, "tk")!!.notes)
    }

    @Test fun `chains block is parsed`() {
        val c = UpdateManifest.parseChains(manifest(2))!!
        assertEquals(3, c.version); assertEquals("https://x/c.json", c.url)
    }
}
