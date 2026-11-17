package com.mmlauncher.tv.domain.model

/** Pure Kotlin domain models: no Android types here. */

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val isLeanback: Boolean,
)

data class TvInput(val id: String, val label: String)

data class Volume(val label: String, val path: String)

data class RamInfo(val totalMb: Int, val availMb: Int) {
    val usedMb get() = totalMb - availMb
    val usedPercent get() = if (totalMb > 0) usedMb * 100 / totalMb else 0
}

data class StorageSpace(val label: String, val totalBytes: Long, val freeBytes: Long) {
    val usedBytes get() = totalBytes - freeBytes
    val usedPercent get() = if (totalBytes > 0) (usedBytes * 100 / totalBytes).toInt() else 0
}

data class FileEntry(val name: String, val path: String, val isDirectory: Boolean, val sizeBytes: Long)

data class DeviceInfo(val release: String, val sdk: Int, val model: String, val build: String)

data class DeveloperStatus(
    val developerMode: Boolean,
    val usbDebugging: Boolean,
    val canWriteSecure: Boolean,
    val device: DeviceInfo,
)

/** One process (or package) with its memory in KB (PSS). */
data class ProcessMemory(val name: String, val packageName: String?, val label: String, val pssKb: Long)

/** One app with its size in bytes. */
data class AppSize(val packageName: String, val label: String, val bytes: Long)

enum class AdbFailure { NOT_LISTENING, WAITING_FOR_APPROVAL, UNEXPECTED, NO_DATA }

sealed interface Fetch<out T> {
    data class Ok<T>(val value: T) : Fetch<T>
    data class Failed(val reason: AdbFailure) : Fetch<Nothing>
}

enum class Language(val code: String) {
    SYSTEM("system"), RU("ru"), TK("tk"), EN("en");
    companion object { fun fromCode(code: String) = values().firstOrNull { it.code == code } ?: SYSTEM }
}

enum class Wallpaper(val key: String) {
    NONE("none"), NIGHT("night"), OCEAN("ocean"), VIOLET("violet"), SUNSET("sunset"), CUSTOM("custom");
    companion object { fun fromKey(key: String) = values().firstOrNull { it.key == key } ?: NONE }
}

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String,
)

sealed interface UpdateCheck {
    data class Available(val info: UpdateInfo) : UpdateCheck
    object UpToDate : UpdateCheck
    object Failed : UpdateCheck
}

enum class UpdateInstall { STARTED, NEEDS_PERMISSION, DOWNLOAD_FAILED }

/** Cards in the home screen's bottom row. */
sealed interface HomeSource {
    data class UpdateAvailable(val info: UpdateInfo) : HomeSource
    data class Input(val input: TvInput) : HomeSource
    data class Usb(val volume: Volume) : HomeSource
    object Files : HomeSource
}

data class AutostartPlan(val app: AppInfo, val delaySeconds: Int)

/** Keys of the system screens a button can open (see settings_chains.json). */
object SystemScreen {
    const val ALL = "all"
    const val ABOUT = "about"
    const val DEVELOPER = "developer"
    const val STORAGE = "storage"
    const val APPS = "apps"
}
