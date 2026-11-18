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
    /** Stock launchers this app switched off (so they can be switched back on). */
    var disabledLaunchers: Set<String>
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
    /** Other apps that answer the Home button (the stock launcher of the firmware). */
    fun stockHomePackages(): List<String>
    /** Name of the app Home opens right now, if known. */
    fun currentHomeLabel(): String?
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
    /** Grants "All files access" through the box's own adbd (Android 11+). */
    fun grantFileAccess(): Boolean
    /** Makes this launcher the home app through adbd (role holder, falling back to set-home-activity). */
    fun setDefaultHome(): Boolean
    /** Disables or enables apps through adbd (`pm disable-user` / `pm enable`); true if all succeeded. */
    fun setLaunchersEnabled(packages: List<String>, enabled: Boolean): Boolean
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

interface DeviceInfoRepository {
    fun report(): DeviceReport
}

interface NetworkRepository {
    fun snapshot(): NetworkSnapshot
}

interface AppManagerRepository {
    fun list(includeSystem: Boolean): List<ManagedApp>
    fun perform(action: AppAction, packageName: String): ActionOutcome
}

interface LogsRepository {
    fun read(maxLines: Int): LogDump
    /** Writes the lines to a file and returns its path, or null. */
    fun save(lines: List<String>): String?
    fun clear(): Boolean
}
