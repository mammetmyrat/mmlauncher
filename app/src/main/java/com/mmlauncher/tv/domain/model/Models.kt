package com.mmlauncher.tv.domain.model

/** Pure Kotlin domain models: no Android types here. */

/** Cards the launcher provides itself, shown in the app list like ordinary apps. */
enum class BuiltInApp { FILES }

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val isLeanback: Boolean,
    /** Non-null for the launcher's own cards (the View shows a localized label and its own icon). */
    val builtIn: BuiltInApp? = null,
) {
    companion object {
        /** The file manager as a card in the app list. */
        val FILES = AppInfo("Files", "mm.builtin.files", "", true, BuiltInApp.FILES)
    }
}

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
}

data class AutostartPlan(val app: AppInfo, val delaySeconds: Int)

/** Keys of the system screens a button can open (see settings_chains.json). */
object SystemScreen {
    const val ALL = "all"
    const val ABOUT = "about"
    /** The unmodified Android TV Settings screens, when the vendor left them on the device. */
    const val ABOUT_STOCK = "about_stock"
    const val DEVELOPER_STOCK = "developer_stock"
    const val DEVELOPER = "developer"
    const val STORAGE = "storage"
    const val APPS = "apps"
}

// ---- tools: device info, network, app manager, logs ----------------------------------------------

enum class InfoSectionId { DEVICE, PROCESSOR, DISPLAY, DRM, CODECS, SOFTWARE }

enum class InfoKey {
    MODEL, MANUFACTURER, PRODUCT, ANDROID, PATCH, BUILD, KERNEL, ABIS,
    CORES, RAM, STORAGE,
    RESOLUTION, DENSITY, REFRESH, MODES, HDR,
    WIDEVINE, WIDEVINE_VERSION, HDCP, DRM_VENDOR,
    AVC, HEVC, VP9, AV1, MPEG2,
    WEBVIEW, LAUNCHER,
}

data class InfoRow(val key: InfoKey, val value: String)
data class InfoSection(val id: InfoSectionId, val rows: List<InfoRow>)
data class DeviceReport(val sections: List<InfoSection>)

enum class NetKind { WIFI, ETHERNET, OTHER }
data class NetInterface(val name: String, val kind: NetKind, val ipv4: String?, val mac: String?)
data class NetworkSnapshot(val interfaces: List<NetInterface>, val adbListening: Boolean)

data class ManagedApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val apkBytes: Long,
)

enum class AppAction { OPEN_INFO, UNINSTALL, FORCE_STOP, CLEAR_CACHE, CLEAR_DATA, DISABLE, ENABLE, EXTRACT_APK }

sealed interface ActionOutcome {
    object Done : ActionOutcome
    data class Saved(val path: String) : ActionOutcome
    /** The action needs the box's own adbd and it did not answer. */
    data class NeedsAdb(val reason: AdbFailure) : ActionOutcome
    object Failed : ActionOutcome
}

enum class LogLevel(val letter: Char) { VERBOSE('V'), DEBUG('D'), INFO('I'), WARN('W'), ERROR('E') }

/** [viaAdb] false means only this launcher's own log could be read ([failure] says why not more). */
data class LogDump(val lines: List<String>, val viaAdb: Boolean, val failure: AdbFailure?)
