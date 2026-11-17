package com.mmlauncher.tv.domain.repository

import com.mmlauncher.tv.domain.model.*

/** Domain contracts. Implemented in the data layer, used only by use cases. */

interface AppsRepository {
    fun installed(): List<AppInfo>
    fun launch(app: AppInfo): Boolean
    fun canUninstall(packageName: String): Boolean
    fun label(packageName: String): String?
}

interface PreferencesRepository {
    var favorites: List<String>
    var hidden: Set<String>
    var language: Language
    var wallpaper: Wallpaper
    var autostartPackage: String          // empty = off
    var autostartDelaySeconds: Int
    var autostartDoneBoot: Long
    var wizardDone: Boolean
    var autoUpdates: Boolean
    var lastUpdateCheck: Long
}

interface SystemRepository {
    fun ram(): RamInfo
    /** Stops background processes of user apps; returns freed MB (>= 0). Blocking ~1 s. */
    fun boostMemory(): Int
    fun internalStorage(): StorageSpace?
    fun removableVolumes(): List<Volume>
    fun spaceOf(volume: Volume): StorageSpace?
    fun tvInputs(): List<TvInput>
    fun bootId(): Long
    fun isDefaultHome(): Boolean
    fun versionName(): String
    fun clearCaches()
    fun now(): Long
}

interface DeveloperRepository {
    fun status(): DeveloperStatus
    fun setDeveloperMode(on: Boolean): Boolean
    fun setUsbDebugging(on: Boolean): Boolean
}

/** Opening other apps' screens: needs the platform, so it is a contract of its own. */
interface SystemScreens {
    fun open(screen: String): Boolean
    fun openTvInput(id: String): Boolean
    fun openAppInfo(packageName: String): Boolean
    fun uninstall(packageName: String): Boolean
    fun openHomeSettings(): Boolean
}

/** Per-app numbers that ordinary apps cannot read; obtained through the box's own adbd. */
interface DiagnosticsRepository {
    fun processMemory(): Fetch<List<ProcessMemory>>
    fun ownMemoryKb(): Long
    fun appSizes(): Fetch<List<AppSize>>
    fun apkSizes(): List<AppSize>
    fun stopBackground(packageName: String)
    fun grantWriteSecureSettings(): Boolean
    fun lastFailure(): AdbFailure?
}

interface UpdateRepository {
    fun check(): UpdateCheck
    fun stored(): UpdateInfo?
    fun downloadAndInstall(update: UpdateInfo): UpdateInstall
}

interface FilesRepository {
    fun roots(): List<Volume>
    fun list(path: String): List<FileEntry>?          // null = no access
    fun hasAccess(): Boolean
    fun open(path: String): Boolean
    fun isImage(path: String): Boolean
}

interface WallpaperRepository {
    fun saveFromFile(path: String): Boolean
    fun saveFromUri(uri: String): Boolean
}
