package com.mmlauncher.tv.data.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import com.mmlauncher.tv.R
import com.mmlauncher.tv.domain.model.*
import com.mmlauncher.tv.domain.repository.SystemRepository
import com.mmlauncher.tv.domain.repository.SystemScreens
import java.io.File

/** RAM, storage, TV inputs, boot id, device facts. */
class AndroidSystemRepository(private val context: Context) : SystemRepository {

    override fun ram(): RamInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return RamInfo((mi.totalMem / MB).toInt(), (mi.availMem / MB).toInt())
    }

    /**
     * Asks the system to stop background processes of user-installed apps (the only thing a normal
     * app may do). Apps can restart themselves, so the result is reported as measured, not promised.
     */
    override fun boostMemory(): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = ram().availMb
        for (pkg in context.packageManager.getInstalledPackages(0)) {
            val info = pkg.applicationInfo ?: continue
            if (pkg.packageName == context.packageName) continue
            val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
            val updatedSystem = info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
            if (system && !updatedSystem) continue // never touch plain system apps
            try { am.killBackgroundProcesses(pkg.packageName) } catch (_: Exception) {}
        }
        Thread.sleep(900) // let the kernel reclaim memory before measuring
        return (ram().availMb - before).coerceAtLeast(0)
    }

    private fun space(label: String, dir: File): StorageSpace? = try {
        val s = StatFs(dir.path)
        val total = s.blockCountLong * s.blockSizeLong
        StorageSpace(label, total, s.availableBlocksLong * s.blockSizeLong)
    } catch (e: Exception) {
        null
    }

    override fun internalStorage() =
        space(context.getString(R.string.internal_storage), Environment.getDataDirectory())

    override fun removableVolumes() = StorageVolumes.removable(context)
    override fun spaceOf(volume: Volume) = space(volume.label, File(volume.path))

    /** FR-14 / FR-15: pass-through inputs (HDMI...) via TvInputManager; empty on boxes without it. */
    override fun tvInputs(): List<TvInput> {
        if (!context.packageManager.hasSystemFeature("android.software.live_tv")) return emptyList()
        return try {
            val tim = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
            tim.tvInputList.filter { it.isPassthroughInput }.map { TvInput(it.id, labelOf(it)) }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private fun labelOf(info: TvInputInfo): String {
        val custom = info.loadCustomLabel(context)?.toString()?.takeIf { it.isNotBlank() }
        return custom ?: info.loadLabel(context)?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.input_fallback)
    }

    /** Identifies the current boot: BOOT_COUNT where available, else boot time rounded to 5 minutes. */
    override fun bootId(): Long {
        val count = try {
            Settings.Global.getInt(context.contentResolver, "boot_count", -1)
        } catch (e: Exception) {
            -1
        }
        if (count >= 0) return count.toLong()
        return (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / (5 * 60 * 1000L)
    }

    override fun isDefaultHome(): Boolean = try {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName == context.packageName
    } catch (e: Exception) {
        false
    }

    override fun versionName(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (e: Exception) {
        ""
    }

    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    override fun stockHomePackages(): List<String> = try {
        context.packageManager.queryIntentActivities(homeIntent(), 0)
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .distinct()
    } catch (e: Exception) {
        emptyList()
    }

    override fun currentHomeLabel(): String? = try {
        val ri = context.packageManager.resolveActivity(homeIntent(), 0)
        ri?.loadLabel(context.packageManager)?.toString()
    } catch (e: Exception) {
        null
    }

    /** FR-22: the launcher's own cache directory (icons are cleared by the View). */
    override fun clearCaches() { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }

    override fun now() = System.currentTimeMillis()

    private companion object { const val MB = 1024L * 1024L }
}

/** Opening other screens needs the platform; each call is guarded so a missing screen never crashes. */
class AndroidSystemScreens(private val context: Context) : SystemScreens {

    override fun open(screen: String) = SettingsChains.open(context, screen)

    override fun openTvInput(id: String) = start(
        Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id))
    )

    override fun openAppInfo(packageName: String) = start(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))
    )

    override fun uninstall(packageName: String) = start(
        Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:$packageName"))
    )

    override fun openHomeSettings(): Boolean =
        start(Intent(Settings.ACTION_HOME_SETTINGS)) || start(Intent(Settings.ACTION_SETTINGS))

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}

class AndroidDeveloperRepository(private val context: Context) :
    com.mmlauncher.tv.domain.repository.DeveloperRepository {

    private fun globalOn(key: String) = try {
        Settings.Global.getInt(context.contentResolver, key, 0) == 1
    } catch (e: Exception) {
        false
    }

    private fun canWriteSecure() =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    override fun status() = DeveloperStatus(
        developerMode = globalOn(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
        usbDebugging = globalOn(Settings.Global.ADB_ENABLED),
        canWriteSecure = canWriteSecure(),
        device = DeviceInfo(
            release = android.os.Build.VERSION.RELEASE,
            sdk = android.os.Build.VERSION.SDK_INT,
            model = android.os.Build.MODEL,
            build = android.os.Build.DISPLAY,
        ),
    )

    private fun put(key: String, on: Boolean) = try {
        Settings.Global.putInt(context.contentResolver, key, if (on) 1 else 0)
    } catch (e: Exception) {
        false // SecurityException without WRITE_SECURE_SETTINGS
    }

    override fun setDeveloperMode(on: Boolean) = put(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, on)
    override fun setUsbDebugging(on: Boolean) = put(Settings.Global.ADB_ENABLED, on)
}
