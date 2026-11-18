package com.mmlauncher.tv.data.tools

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Environment
import com.mmlauncher.tv.data.diagnostics.LocalAdb
import com.mmlauncher.tv.data.diagnostics.failure
import com.mmlauncher.tv.domain.model.ActionOutcome
import com.mmlauncher.tv.domain.model.AppAction
import com.mmlauncher.tv.domain.model.ManagedApp
import com.mmlauncher.tv.domain.repository.AppManagerRepository
import java.io.File

/**
 * Lists apps and runs maintenance actions. What an ordinary app may do (stop background process,
 * copy an APK) is done directly; clearing data/cache and disabling go through the box's own adbd.
 */
class AppManagerRepositoryImpl(private val context: Context) : AppManagerRepository {

    private val pm get() = context.packageManager

    @Suppress("DEPRECATION")
    override fun list(includeSystem: Boolean): List<ManagedApp> =
        pm.getInstalledApplications(0).mapNotNull { info ->
            if (info.packageName == context.packageName) return@mapNotNull null
            val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0 && info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
            if (system && !includeSystem) return@mapNotNull null
            var size = File(info.sourceDir).length()
            info.splitSourceDirs?.forEach { size += File(it).length() }
            ManagedApp(info.packageName, pm.getApplicationLabel(info).toString(), system, info.enabled, size)
        }

    override fun perform(action: AppAction, packageName: String): ActionOutcome = when (action) {
        AppAction.FORCE_STOP -> forceStop(packageName)
        AppAction.CLEAR_CACHE -> shell("pm clear --cache-only $packageName", ok = "Success")
        AppAction.CLEAR_DATA -> shell("pm clear $packageName", ok = "Success")
        AppAction.DISABLE -> shell("pm disable-user --user 0 $packageName", ok = "disabled")
        AppAction.ENABLE -> shell("pm enable $packageName", ok = "enabled")
        AppAction.EXTRACT_APK -> extractApk(packageName)
        AppAction.OPEN_INFO, AppAction.UNINSTALL -> ActionOutcome.Failed // handled by the system screens
    }

    private fun shell(command: String, ok: String): ActionOutcome {
        val out = LocalAdb.shell(context, command) ?: return ActionOutcome.NeedsAdb(LocalAdb.failure())
        return if (out.contains(ok, ignoreCase = true)) ActionOutcome.Done else ActionOutcome.Failed
    }

    /** Real force-stop needs adbd; without it the best an ordinary app can do is stop background processes. */
    private fun forceStop(pkg: String): ActionOutcome {
        val out = LocalAdb.shell(context, "am force-stop $pkg")
        if (out != null) return ActionOutcome.Done
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return try { am.killBackgroundProcesses(pkg); ActionOutcome.Done } catch (e: Exception) { ActionOutcome.Failed }
    }

    /** Copies the APK to Downloads (needs file access) or, failing that, to the launcher's own external folder. */
    private fun extractApk(pkg: String): ActionOutcome = try {
        val src = File(pm.getApplicationInfo(pkg, 0).sourceDir)
        val targets = listOfNotNull(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$pkg.apk"),
            context.getExternalFilesDir(null)?.let { File(it, "$pkg.apk") },
        )
        val written = targets.firstOrNull { dst ->
            try { dst.parentFile?.mkdirs(); src.copyTo(dst, overwrite = true); true } catch (e: Exception) { false }
        }
        if (written != null) ActionOutcome.Saved(written.path) else ActionOutcome.Failed
    } catch (e: Exception) {
        ActionOutcome.Failed
    }
}
