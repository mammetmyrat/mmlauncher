package com.mmlauncher.tv.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.mmlauncher.tv.Config
import com.mmlauncher.tv.data.system.SettingsChains
import com.mmlauncher.tv.domain.model.*
import com.mmlauncher.tv.domain.repository.PreferencesRepository
import com.mmlauncher.tv.domain.repository.UpdateRepository
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Update check against a static update.json (GitHub Pages) plus the downloadable settings-chains
 * config. Plain HttpURLConnection, no identifiers sent, HTTPS only (http allowed in debug builds).
 */
class HttpUpdateRepository(
    private val context: Context,
    private val prefs: PreferencesRepository,
    private val store: android.content.SharedPreferences,
) : UpdateRepository {

    private val updateUrl get() = store.getString("update_url", Config.DEFAULT_UPDATE_URL).orEmpty()

    private fun debuggable() = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    @Suppress("DEPRECATION")
    private fun versionCode() = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode
    } catch (e: Exception) { 0 }

    private fun versionName() = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (e: Exception) { "" }

    private fun language() = prefs.language.code.takeIf { it != "system" } ?: Locale.getDefault().language

    private fun open(url: String): HttpURLConnection {
        val u = URL(url)
        require(u.protocol == "https" || (debuggable() && u.protocol == "http")) { "https required" }
        return (u.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 12000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MMLauncher/${versionName()}")
        }
    }

    private fun httpGet(url: String): String {
        val c = open(url)
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            c.inputStream.use { input ->
                while (out.size() <= MAX_JSON) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
            }
            if (out.size() > MAX_JSON) error("too large")
            return String(out.toByteArray(), Charsets.UTF_8)
        } finally {
            c.disconnect()
        }
    }

    override fun check(): UpdateCheck = try {
        val root = JSONObject(httpGet(updateUrl))
        prefs.lastUpdateCheck = System.currentTimeMillis()

        UpdateManifest.parseChains(root)?.let { c ->
            val known = store.getInt("chains_version", 0)
            if (c.version > known && c.url.isNotEmpty() && SettingsChains.applyOverride(context, httpGet(c.url))) {
                store.edit().putInt("chains_version", c.version).apply()
            }
        }

        val update = UpdateManifest.parseUpdate(root, versionCode(), language())
        if (update != null) {
            store.edit().putString("available_update", root.toString()).apply()
            UpdateCheck.Available(update)
        } else {
            store.edit().putString("available_update", "").apply()
            UpdateCheck.UpToDate
        }
    } catch (e: Exception) {
        UpdateCheck.Failed
    }

    override fun stored(): UpdateInfo? {
        val raw = store.getString("available_update", "").orEmpty()
        if (raw.isEmpty()) return null
        return try { UpdateManifest.parseUpdate(JSONObject(raw), versionCode(), language()) } catch (e: Exception) { null }
    }

    /** The SHA-256 from update.json is the integrity check (besides Android's same-signature rule). */
    override fun downloadAndInstall(update: UpdateInfo): UpdateInstall {
        val apk = download(update) ?: return UpdateInstall.DOWNLOAD_FAILED
        return try {
            if (install(apk)) UpdateInstall.STARTED else UpdateInstall.NEEDS_PERMISSION
        } catch (e: Exception) {
            UpdateInstall.DOWNLOAD_FAILED
        }
    }

    private fun download(update: UpdateInfo): File? {
        if (update.sha256.length != 64) return null
        return try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "update.apk")
            val digest = MessageDigest.getInstance("SHA-256")
            val c = open(update.apkUrl)
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

    private fun install(apk: File): Boolean {
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

    private companion object {
        const val MAX_JSON = 64 * 1024
        const val MAX_APK = 80L * 1024 * 1024
    }
}
