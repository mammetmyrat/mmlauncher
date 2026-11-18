package com.mmlauncher.tv.data.diagnostics

import android.app.ActivityManager
import android.content.Context
import com.mmlauncher.tv.domain.model.*
import com.mmlauncher.tv.domain.repository.DiagnosticsRepository
import java.io.File

/**
 * Per-app memory and sizes. Ordinary apps cannot read them, so they come from `dumpsys` run through
 * the box's own adbd ([LocalAdb]); apps that cannot (or may not) get that still see sensible fallbacks.
 */
class AdbDiagnosticsRepository(private val context: Context) : DiagnosticsRepository {

    private val pm get() = context.packageManager

    private fun label(name: String): String? = try {
        pm.getApplicationLabel(pm.getApplicationInfo(name, 0)).toString()
    } catch (e: Exception) {
        null
    }

    private fun failure(): AdbFailure = when (LocalAdb.lastError) {
        "refused" -> AdbFailure.NOT_LISTENING
        "denied" -> AdbFailure.WAITING_FOR_APPROVAL
        else -> AdbFailure.UNEXPECTED
    }

    override fun lastFailure(): AdbFailure? = LocalAdb.lastError?.let { failure() }

    override fun processMemory(): Fetch<List<ProcessMemory>> {
        val text = LocalAdb.shell(context, "dumpsys meminfo") ?: return Fetch.Failed(failure())
        val parsed = DumpParsers.meminfo(text) ?: return Fetch.Failed(AdbFailure.UNEXPECTED)
        return Fetch.Ok(parsed.map { p ->
            val appLabel = label(p.name)
            ProcessMemory(p.name, if (appLabel != null) p.name else null, appLabel ?: p.name, p.kb)
        })
    }

    override fun ownMemoryKb(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid())).firstOrNull()?.totalPss?.toLong() ?: 0L
    }

    override fun appSizes(): Fetch<List<AppSize>> {
        val text = LocalAdb.shell(context, "dumpsys diskstats", runMs = 60000) ?: return Fetch.Failed(failure())
        val parsed = DumpParsers.diskstats(text)
            ?: return Fetch.Failed(AdbFailure.NO_DATA) // older Android versions do not report per-app sizes
        return Fetch.Ok(parsed.map { AppSize(it.packageName, label(it.packageName) ?: it.packageName, it.total) })
    }

    /** What an ordinary app can measure: the installed size (APK files) of each app. */
    @Suppress("DEPRECATION")
    override fun apkSizes(): List<AppSize> = pm.getInstalledApplications(0).map { info ->
        var size = File(info.sourceDir).length()
        info.splitSourceDirs?.forEach { size += File(it).length() }
        AppSize(info.packageName, label(info.packageName) ?: info.packageName, size)
    }

    override fun stopBackground(packageName: String) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        try { am.killBackgroundProcesses(packageName) } catch (_: Exception) {}
    }

    override fun grantFileAccess(): Boolean =
        LocalAdb.shell(context, "appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow") != null

    /** Role holder survives reboots (Android 10+); older versions use the preferred home activity. */
    override fun setDefaultHome(): Boolean {
        val pkg = context.packageName
        val role = LocalAdb.shell(context, "cmd role add-role-holder --user 0 android.app.role.HOME $pkg 0")
        if (role != null && !role.contains("Error", ignoreCase = true) && !role.contains("not found", ignoreCase = true)) return true
        return LocalAdb.shell(context, "cmd package set-home-activity $pkg/.presentation.home.MainActivity") != null
    }

    override fun setLaunchersEnabled(packages: List<String>, enabled: Boolean): Boolean {
        if (packages.isEmpty()) return false
        var all = true
        for (pkg in packages) {
            val cmd = if (enabled) "pm enable $pkg" else "pm disable-user --user 0 $pkg"
            val out = LocalAdb.shell(context, cmd)
            val ok = out != null && out.contains(if (enabled) "enabled" else "disabled", ignoreCase = true)
            if (!ok) all = false
        }
        return all
    }

    override fun grantWriteSecureSettings(): Boolean =
        LocalAdb.shell(context, "pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS") != null
}
