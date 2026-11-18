package com.mmlauncher.tv

import com.mmlauncher.tv.domain.model.*
import com.mmlauncher.tv.domain.repository.*

/** In-memory test doubles for the domain contracts. */

fun app(pkg: String, label: String = pkg) = AppInfo(label, pkg, "$pkg.Main", isLeanback = true)

class FakePrefs : PreferencesRepository {
    override var favorites: List<String> = emptyList()
    override var hidden: Set<String> = emptySet()
    override var language = Language.SYSTEM
    override var wallpaper = Wallpaper.NONE
    override var autostartPackage = ""
    override var autostartDelaySeconds = 3
    override var autostartDoneBoot = -1L
    override var wizardDone = true
    override var autoUpdates = false
    override var lastUpdateCheck = 0L
    override var disabledLaunchers: Set<String> = emptySet()
}

class FakeApps(var apps: List<AppInfo> = emptyList()) : AppsRepository {
    val launched = ArrayList<String>()
    var launchResult = true
    var uninstallable = setOf<String>()
    override fun installed() = apps
    override fun launch(app: AppInfo): Boolean { launched += app.packageName; return launchResult }
    override fun canUninstall(packageName: String) = packageName in uninstallable
    override fun label(packageName: String) = apps.firstOrNull { it.packageName == packageName }?.label
}

class FakeSystem : SystemRepository {
    var ram = RamInfo(2000, 500)
    var boot = 7L
    var time = 1_000_000_000L
    var inputs = emptyList<TvInput>()
    var volumes = emptyList<Volume>()
    var home = false
    override fun ram() = ram
    override fun boostMemory() = 42
    override fun internalStorage() = StorageSpace("Internal", 1000, 400)
    override fun removableVolumes() = volumes
    override fun spaceOf(volume: Volume) = StorageSpace(volume.label, 100, 50)
    override fun tvInputs() = inputs
    override fun bootId() = boot
    override fun isDefaultHome() = home
    override fun versionName() = "0.1.0"
    var stock: List<String> = listOf("com.google.android.tvlauncher")
    override fun stockHomePackages() = stock
    override fun currentHomeLabel(): String? = "Stock"
    override fun clearCaches() {}
    override fun now() = time
}

class FakeUpdates : UpdateRepository {
    var checks = 0
    var stored: UpdateInfo? = null
    var result: UpdateCheck = UpdateCheck.UpToDate
    var install = UpdateInstall.STARTED
    override fun check(): UpdateCheck { checks++; return result }
    override fun stored() = stored
    override fun downloadAndInstall(update: UpdateInfo) = install
}

class FakeScreens : SystemScreens {
    val opened = ArrayList<String>()
    var ok = true
    override fun open(screen: String): Boolean { opened += screen; return ok }
    override fun openTvInput(id: String) = ok.also { opened += "input:$id" }
    override fun openAppInfo(packageName: String) = ok.also { opened += "info:$packageName" }
    override fun uninstall(packageName: String) = ok.also { opened += "uninstall:$packageName" }
    override fun openHomeSettings() = ok
}

class FakeDiagnostics : DiagnosticsRepository {
    var memory: Fetch<List<ProcessMemory>> = Fetch.Failed(AdbFailure.NOT_LISTENING)
    var sizes: Fetch<List<AppSize>> = Fetch.Failed(AdbFailure.NOT_LISTENING)
    var apk = listOf(AppSize("a", "A", 10), AppSize("b", "B", 30))
    val stopped = ArrayList<String>()
    override fun processMemory() = memory
    override fun ownMemoryKb() = 5000L
    override fun appSizes() = sizes
    override fun apkSizes() = apk
    override fun stopBackground(packageName: String) { stopped += packageName }
    var onGrantFileAccess: () -> Unit = {}
    var onSetDefaultHome: () -> Unit = {}
    var onGrantWrite: () -> Unit = {}
    override fun grantWriteSecureSettings() = true.also { onGrantWrite() }
    override fun grantFileAccess() = true.also { onGrantFileAccess() }
    override fun setDefaultHome() = true.also { onSetDefaultHome() }
    var launchersResult = true
    var lastLaunchers: Pair<List<String>, Boolean>? = null
    override fun setLaunchersEnabled(packages: List<String>, enabled: Boolean): Boolean { lastLaunchers = packages to enabled; return launchersResult }
    override fun lastFailure() = (memory as? Fetch.Failed)?.reason
}

class FakeFiles(private val tree: Map<String, List<FileEntry>>) : FilesRepository {
    var access = true
    val opened = ArrayList<String>()
    override fun roots() = listOf(Volume("Internal", "/sdcard"))
    override fun list(path: String) = tree[path]
    override fun hasAccess() = access
    override fun open(path: String) = true.also { opened += path }
    override fun isImage(path: String) = path.endsWith(".png")
}

class FakeWallpapers : WallpaperRepository {
    var ok = true
    var last: String? = null
    override fun saveFromFile(path: String) = ok.also { last = path }
    override fun saveFromUri(uri: String) = ok.also { last = uri }
}

class FakeAppManager(var apps: List<ManagedApp> = emptyList()) : AppManagerRepository {
    val performed = ArrayList<Pair<AppAction, String>>()
    var outcome: ActionOutcome = ActionOutcome.Done
    override fun list(includeSystem: Boolean) = apps.filter { includeSystem || !it.isSystem }
    override fun perform(action: AppAction, packageName: String): ActionOutcome { performed += action to packageName; return outcome }
}

class FakeLogs(var dump: LogDump) : LogsRepository {
    var saved: List<String>? = null
    var cleared = false
    override fun read(maxLines: Int) = dump
    override fun save(lines: List<String>): String? { saved = lines; return "/sdcard/Download/log.txt" }
    override fun clear() = true.also { cleared = true }
}

fun managed(pkg: String, system: Boolean = false, enabled: Boolean = true) = ManagedApp(pkg, pkg.uppercase(), system, enabled, 1000)

class FakeDeveloper(var canWrite: Boolean = true) : DeveloperRepository {
    var dev = false; var usb = false
    override fun status() = DeveloperStatus(dev, usb, canWrite, DeviceInfo("13", 33, "tv", "b"))
    override fun setDeveloperMode(on: Boolean) = canWrite.also { if (it) dev = on }
    override fun setUsbDebugging(on: Boolean) = canWrite.also { if (it) usb = on }
}

class FakeDeviceInfo : DeviceInfoRepository {
    override fun report() = DeviceReport(listOf(InfoSection(InfoSectionId.DEVICE, listOf(InfoRow(InfoKey.BUILD, "X"), InfoRow(InfoKey.MODEL, "Y")))))
}
