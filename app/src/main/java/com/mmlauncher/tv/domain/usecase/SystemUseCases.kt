package com.mmlauncher.tv.domain.usecase

import com.mmlauncher.tv.domain.model.*
import com.mmlauncher.tv.domain.repository.*

// ---- developer mode -------------------------------------------------------------------------

class GetDeveloperStatus(private val dev: DeveloperRepository) {
    operator fun invoke() = dev.status()
}

class ToggleDeveloperMode(private val dev: DeveloperRepository) {
    operator fun invoke(): Boolean = dev.setDeveloperMode(!dev.status().developerMode)
}

class ToggleUsbDebugging(private val dev: DeveloperRepository) {
    /** USB debugging only makes sense with developer mode on, so turn that on first. */
    operator fun invoke(): Boolean {
        val s = dev.status()
        if (!s.usbDebugging && !s.developerMode) dev.setDeveloperMode(true)
        return dev.setUsbDebugging(!s.usbDebugging)
    }
}

enum class UnlockResult { ALREADY_ON, ENABLED, NEEDS_PERMISSION }

/** What 7 taps on "Build" do: switch developer mode on, getting the needed permission through local ADB if possible. */
class UnlockDeveloperMode(private val dev: DeveloperRepository, private val diag: DiagnosticsRepository) {
    operator fun invoke(): UnlockResult {
        var s = dev.status()
        if (s.developerMode) return UnlockResult.ALREADY_ON
        if (!s.canWriteSecure) { diag.grantWriteSecureSettings(); s = dev.status() }
        if (!s.canWriteSecure) return UnlockResult.NEEDS_PERMISSION
        return if (dev.setDeveloperMode(true)) UnlockResult.ENABLED else UnlockResult.NEEDS_PERMISSION
    }
}

class GrantWriteSecureViaAdb(private val diag: DiagnosticsRepository, private val dev: DeveloperRepository) {
    /** True if the permission is held afterwards. */
    operator fun invoke(): Boolean {
        diag.grantWriteSecureSettings()
        return dev.status().canWriteSecure
    }
}

class GrantFileAccessViaAdb(private val diag: DiagnosticsRepository, private val files: FilesRepository) {
    /** Result: whether access is held afterwards, and why not if it is not. */
    operator fun invoke(): Pair<Boolean, AdbFailure?> {
        diag.grantFileAccess()
        return files.hasAccess() to diag.lastFailure()
    }
}

class SetDefaultHomeViaAdb(private val diag: DiagnosticsRepository, private val system: SystemRepository) {
    /** True if this launcher is the home screen afterwards. */
    operator fun invoke(): Boolean {
        diag.setDefaultHome()
        return system.isDefaultHome()
    }
}

// ---- RAM -------------------------------------------------------------------------------------

/** RAM screen content: totals always, per-process list only when the box's adbd answers. */
data class RamUsage(val ram: RamInfo, val processes: List<ProcessMemory>?, val failure: AdbFailure?, val ownKb: Long)

class GetRamUsage(private val system: SystemRepository, private val diag: DiagnosticsRepository) {
    operator fun invoke(): RamUsage {
        val ram = system.ram()
        return when (val r = diag.processMemory()) {
            is Fetch.Ok -> RamUsage(ram, r.value.sortedByDescending { it.pssKb }, null, 0)
            is Fetch.Failed -> RamUsage(ram, null, r.reason, diag.ownMemoryKb())
        }
    }
}

class BoostMemory(private val system: SystemRepository) {
    operator fun invoke(): Int = system.boostMemory()
}

class StopApp(private val diag: DiagnosticsRepository) {
    operator fun invoke(packageName: String) = diag.stopBackground(packageName)
}

// ---- storage ---------------------------------------------------------------------------------

data class StorageUsage(
    val spaces: List<StorageSpace>,
    val apps: List<AppSize>,
    /** Why per-app data/cache sizes are missing (then [apps] holds installed APK sizes). */
    val failure: AdbFailure?,
)

class GetStorageUsage(
    private val system: SystemRepository,
    private val diag: DiagnosticsRepository,
) {
    operator fun invoke(): StorageUsage {
        val spaces = buildList {
            system.internalStorage()?.let { add(it) }
            system.removableVolumes().forEach { v -> system.spaceOf(v)?.let { add(it) } }
        }
        return when (val r = diag.appSizes()) {
            is Fetch.Ok -> StorageUsage(spaces, r.value.sortedByDescending { it.bytes }, null)
            is Fetch.Failed -> StorageUsage(spaces, diag.apkSizes().sortedByDescending { it.bytes }, r.reason)
        }
    }
}

class OpenAppInfo(private val screens: SystemScreens) {
    operator fun invoke(pkg: String) = screens.openAppInfo(pkg)
}

class UninstallApp(private val screens: SystemScreens) {
    operator fun invoke(pkg: String) = screens.uninstall(pkg)
}

class OpenTvInput(private val screens: SystemScreens) {
    operator fun invoke(id: String) = screens.openTvInput(id)
}

// ---- files -----------------------------------------------------------------------------------

/** Directory content with folders first; null path = the list of storage roots. */
data class Listing(val path: String?, val entries: List<FileEntry>)

class ListDirectory(private val files: FilesRepository) {
    /** Null if the folder cannot be read. */
    operator fun invoke(path: String?): Listing? {
        if (path == null) {
            return Listing(null, files.roots().map { FileEntry(it.label, it.path, true, 0) })
        }
        val list = files.list(path) ?: return null
        val sorted = list.filter { !it.name.startsWith('.') }
            .sortedWith(compareBy<FileEntry>({ !it.isDirectory }, { it.name.lowercase() }))
        return Listing(path, sorted)
    }
}

class OpenFile(private val files: FilesRepository) {
    operator fun invoke(path: String) = files.open(path)
}

class HasFileAccess(private val files: FilesRepository) {
    operator fun invoke() = files.hasAccess()
}

class IsImageFile(private val files: FilesRepository) {
    operator fun invoke(path: String) = files.isImage(path)
}

class FileRoots(private val files: FilesRepository) {
    operator fun invoke() = files.roots()
}
