package com.mmlauncher.tv

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Stage 7: update check against a static update.json (GitHub Pages), plus the downloadable
 * settings-chains config (FR-10). Plain HttpURLConnection, no libraries, no identifiers sent.
 *
 * update.json:
 * { "versionCode": 2, "versionName": "0.2.0", "apkUrl": "https://.../mm-launcher-0.2.0.apk",
 *   "sha256": "<hex of the apk>", "notes": {"en": "...", "ru": "...", "tk": "..."},
 *   "chains": {"version": 3, "url": "https://.../settings_chains.json"} }
 */
object UpdateChecker {

    data class Update(val versionCode: Int, val versionName: String, val apkUrl: String, val sha256: String, val notes: String)

    sealed class Result {
        data class Available(val update: Update) : Result()
        object UpToDate : Result()
        object Failed : Result()
    }

    private const val MAX_JSON = 64 * 1024
    private const val MAX_APK = 80L * 1024 * 1024

    private fun isDebuggable(context: Context) = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** HTTPS only (plain http is allowed for debug builds, so a local test server works). */
    private fun open(context: Context, url: String): HttpURLConnection {
        val u = URL(url)
        require(u.protocol == "https" || (isDebuggable(context) && u.protocol == "http")) { "https required" }
        return (u.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 12000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MMLauncher/${versionName(context)}")
        }
    }

    private fun httpGet(context: Context, url: String): String {
        val c = open(context, url)
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            val bytes = c.inputStream.use { it.readNBytesCompat(MAX_JSON + 1) }
            if (bytes.size > MAX_JSON) error("too large")
            return String(bytes, Charsets.UTF_8)
        } finally {
            c.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < max) {
            val n = read(buf, 0, minOf(buf.size, max - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    @Suppress("DEPRECATION")
    fun versionCode(context: Context): Int = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode
    } catch (e: Exception) { 0 }

    fun versionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (e: Exception) { "" }

    /** Blocking. Fetches update.json, applies a newer chains config, reports a newer app version. */
    fun check(context: Context, prefs: Prefs): Result {
        return try {
            val root = JSONObject(httpGet(context, prefs.updateUrl))
            prefs.lastUpdateCheck = System.currentTimeMillis()

            root.optJSONObject("chains")?.let { c ->
                val v = c.optInt("version", 0)
                val url = c.optString("url")
                if (v > prefs.chainsVersion && url.isNotEmpty() && SettingsChains.applyOverride(context, httpGet(context, url))) {
                    prefs.chainsVersion = v
                }
            }

            val code = root.optInt("versionCode", 0)
            if (code > versionCode(context)) {
                val update = Update(
                    versionCode = code,
                    versionName = root.optString("versionName"),
                    apkUrl = root.optString("apkUrl"),
                    sha256 = root.optString("sha256").lowercase(Locale.ROOT),
                    notes = pickNotes(context, root.optJSONObject("notes")),
                )
                if (update.apkUrl.isEmpty()) return Result.Failed
                prefs.availableUpdate = root.toString()
                Result.Available(update)
            } else {
                prefs.availableUpdate = ""
                Result.UpToDate
            }
        } catch (e: Exception) {
            Result.Failed
        }
    }

    /** Rebuilds an Update from the stored update.json (for the "update available" card). */
    fun stored(context: Context, prefs: Prefs): Update? {
        val raw = prefs.availableUpdate
        if (raw.isEmpty()) return null
        return try {
            val root = JSONObject(raw)
            val code = root.optInt("versionCode", 0)
            if (code <= versionCode(context)) return null
            Update(code, root.optString("versionName"), root.optString("apkUrl"),
                root.optString("sha256").lowercase(Locale.ROOT), pickNotes(context, root.optJSONObject("notes")))
        } catch (e: Exception) { null }
    }

    private fun pickNotes(context: Context, notes: JSONObject?): String {
        if (notes == null) return ""
        val chosen = Prefs(context).language.takeIf { it != "system" } ?: Locale.getDefault().language
        return notes.optString(chosen).ifEmpty { notes.optString("en") }
    }

    /**
     * Blocking. Downloads the APK and verifies SHA-256. A missing sha256 in update.json means we
     * refuse to install: the checksum is the only integrity check besides Android's signature rule.
     */
    fun download(context: Context, update: Update): File? {
        if (update.sha256.length != 64) return null
        return try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "update.apk")
            val digest = MessageDigest.getInstance("SHA-256")
            val c = open(context, update.apkUrl)
            try {
                if (c.responseCode != 200) return null
                var total = 0L
                c.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_APK) error("too large")
                            digest.update(buf, 0, n)
                            out.write(buf, 0, n)
                        }
                    }
                }
            } finally { c.disconnect() }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            if (hex == update.sha256) file else { file.delete(); null }
        } catch (e: Exception) {
            null
        }
    }

    /** Hands the verified APK to the system installer. Returns false if "unknown sources" is off. */
    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {}
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
