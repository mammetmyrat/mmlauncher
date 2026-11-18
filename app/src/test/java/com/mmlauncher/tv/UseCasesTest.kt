package com.mmlauncher.tv

import com.mmlauncher.tv.domain.model.*
import com.mmlauncher.tv.domain.usecase.*
import org.junit.Assert.*
import org.junit.Test

class UseCasesTest {

    private val prefs = FakePrefs()
    private val apps = FakeApps(listOf(app("a"), app("b"), app("c")))

    @Test fun `hidden apps are removed from the list and reported separately`() {
        prefs.hidden = setOf("b")
        val home = LoadHomeApps(apps, prefs)()
        assertEquals(listOf("a", "c"), home.visible.real())
        assertEquals(listOf("b"), home.hidden.real())
    }

    private fun List<AppInfo>.real() = filter { it.builtIn == null }.map { it.packageName }

    @Test fun `the built-in Files card comes first, like an app`() {
        val home = LoadHomeApps(apps, prefs)()
        assertEquals(BuiltInApp.FILES, home.visible.first().builtIn)
        assertEquals(listOf("a", "b", "c"), home.visible.real())
    }

    @Test fun `Files can be a favorite or hidden like any app, and has no uninstall`() {
        prefs.favorites = listOf(AppInfo.FILES.packageName, "a")
        val home = LoadHomeApps(apps, prefs)()
        assertEquals(AppInfo.FILES.packageName, home.favorites.first().packageName)
        val menu = BuildAppMenu(ManageFavorites(prefs), apps)(AppInfo.FILES)
        assertTrue(menu.isBuiltIn); assertFalse(menu.canUninstall); assertTrue(menu.isFavorite)

        prefs.hidden = setOf(AppInfo.FILES.packageName)
        val hidden = LoadHomeApps(apps, prefs)()
        assertTrue(hidden.visible.none { it.builtIn != null })
        assertEquals(AppInfo.FILES.packageName, hidden.hidden.single().packageName)
    }

    @Test fun `favorites keep the user's order and skip apps that are gone or hidden`() {
        prefs.favorites = listOf("c", "gone", "a", "b")
        prefs.hidden = setOf("b")
        val home = LoadHomeApps(apps, prefs)()
        assertEquals(listOf("c", "a"), home.favorites.real())
    }

    @Test fun `move swaps neighbours and refuses to leave the list`() {
        prefs.favorites = listOf("a", "b", "c")
        val fav = ManageFavorites(prefs)
        assertTrue(fav.move("c", -1))
        assertEquals(listOf("a", "c", "b"), prefs.favorites)
        assertFalse(fav.move("a", -1))
        assertFalse(fav.move("b", +1))
        assertFalse(fav.move("missing", +1))
        assertEquals(listOf("a", "c", "b"), prefs.favorites)
    }

    @Test fun `add does not duplicate and remove works`() {
        val fav = ManageFavorites(prefs)
        fav.add("a"); fav.add("a"); fav.add("b")
        assertEquals(listOf("a", "b"), prefs.favorites)
        fav.remove("a")
        assertEquals(listOf("b"), prefs.favorites)
    }

    @Test fun `app menu knows favorite position and uninstall right`() {
        prefs.favorites = listOf("a", "b", "c")
        apps.uninstallable = setOf("b")
        val menu = BuildAppMenu(ManageFavorites(prefs), apps)(app("b"))
        assertTrue(menu.isFavorite); assertTrue(menu.canMoveLeft); assertTrue(menu.canMoveRight); assertTrue(menu.canUninstall)
        val first = BuildAppMenu(ManageFavorites(prefs), apps)(app("a"))
        assertFalse(first.canMoveLeft); assertTrue(first.canMoveRight); assertFalse(first.canUninstall)
        val plain = BuildAppMenu(ManageFavorites(prefs), apps)(app("zzz"))
        assertFalse(plain.isFavorite); assertFalse(plain.canMoveLeft); assertFalse(plain.canMoveRight)
    }

    @Test fun `autostart fires once per boot only`() {
        val system = FakeSystem()
        prefs.autostartPackage = "b"
        val resolve = ResolveAutostart(prefs, system)
        val plan = resolve(apps.installed())
        assertEquals("b", plan?.app?.packageName)
        assertEquals(3, plan?.delaySeconds)
        assertNull("second call in the same boot", resolve(apps.installed()))
        system.boot = 8 // rebooted
        assertNotNull(resolve(apps.installed()))
    }

    @Test fun `autostart needs a configured and installed app`() {
        val system = FakeSystem()
        val resolve = ResolveAutostart(prefs, system)
        assertFalse(resolve.isDue())
        prefs.autostartPackage = "not-installed"
        assertNull(resolve(apps.installed()))
    }

    @Test fun `choosing an autostart app does not fire it in the current boot`() {
        val system = FakeSystem()
        ConfigureAutostart(prefs, system).choose("a")
        assertNull(ResolveAutostart(prefs, system)(apps.installed()))
    }

    @Test fun `auto update check needs consent and runs once a day`() {
        val system = FakeSystem(); val updates = FakeUpdates()
        val auto = AutoCheckUpdates(prefs, updates, system)
        assertFalse(auto()); assertEquals(0, updates.checks)          // no consent
        prefs.autoUpdates = true
        assertTrue(auto()); assertEquals(1, updates.checks)
        prefs.lastUpdateCheck = system.time                            // just checked
        assertFalse(auto()); assertEquals(1, updates.checks)
        system.time += 25L * 60 * 60 * 1000                            // a day later
        assertTrue(auto()); assertEquals(2, updates.checks)
    }

    @Test fun `ToggleUsbDebugging turns developer mode on first`() {
        val calls = ArrayList<String>()
        val dev = object : com.mmlauncher.tv.domain.repository.DeveloperRepository {
            var d = false; var u = false
            override fun status() = DeveloperStatus(d, u, true, DeviceInfo("14", 34, "m", "b"))
            override fun setDeveloperMode(on: Boolean) = true.also { d = on; calls += "dev=$on" }
            override fun setUsbDebugging(on: Boolean) = true.also { u = on; calls += "usb=$on" }
        }
        assertTrue(ToggleUsbDebugging(dev)())
        assertEquals(listOf("dev=true", "usb=true"), calls)
    }

    @Test fun `ram usage falls back to own memory when adb is unavailable`() {
        val diag = FakeDiagnostics()
        val usage = GetRamUsage(FakeSystem(), diag)()
        assertNull(usage.processes)
        assertEquals(AdbFailure.NOT_LISTENING, usage.failure)
        assertEquals(5000L, usage.ownKb)

        diag.memory = Fetch.Ok(listOf(ProcessMemory("x", "x", "X", 100), ProcessMemory("y", "y", "Y", 900)))
        val ok = GetRamUsage(FakeSystem(), diag)()
        assertEquals(listOf("y", "x"), ok.processes?.map { it.name }) // biggest first
    }

    @Test fun `storage usage shows apk sizes when per-app sizes are missing`() {
        val diag = FakeDiagnostics().apply { sizes = Fetch.Failed(AdbFailure.NO_DATA) }
        val usage = GetStorageUsage(FakeSystem(), diag)()
        assertEquals(AdbFailure.NO_DATA, usage.failure)
        assertEquals(listOf("b", "a"), usage.apps.map { it.packageName })
        assertEquals(1, usage.spaces.size)
    }

    @Test fun `directory listing puts folders first and hides dot files`() {
        val tree = mapOf("/x" to listOf(
            FileEntry("zeta.txt", "/x/zeta.txt", false, 1), FileEntry(".hidden", "/x/.hidden", false, 1),
            FileEntry("beta", "/x/beta", true, 0), FileEntry("Alpha", "/x/Alpha", true, 0),
        ))
        val listing = ListDirectory(FakeFiles(tree))("/x")!!
        assertEquals(listOf("Alpha", "beta", "zeta.txt"), listing.entries.map { it.name })
        assertNull(ListDirectory(FakeFiles(tree))("/nope"))
        assertEquals(listOf("/sdcard"), ListDirectory(FakeFiles(tree))(null)!!.entries.map { it.path })
    }

    @Test fun `default home is reported from the system after the adb command`() {
        val system = FakeSystem()
        val diag = FakeDiagnostics().apply { onSetDefaultHome = { system.home = true } }
        assertFalse(system.isDefaultHome())
        assertTrue(SetDefaultHomeViaAdb(diag, system)())
        assertTrue(system.isDefaultHome())
        val stuck = FakeSystem() // the command ran but the firmware ignored it
        assertFalse(SetDefaultHomeViaAdb(FakeDiagnostics(), stuck)())
    }

    @Test fun `launcher settings tell whether it is the home screen`() {
        val system = FakeSystem().apply { home = true }
        assertTrue(LoadLauncherSettings(prefs, apps, system)().isDefaultHome)
    }

    @Test fun `changing to the same language is not a change`() {
        val change = ChangeLanguage(prefs)
        assertFalse(change(Language.SYSTEM))
        assertTrue(change(Language.TK)); assertEquals(Language.TK, prefs.language)
    }

    @Test fun `custom wallpaper is selected only if saving worked`() {
        val w = FakeWallpapers()
        val change = ChangeWallpaper(prefs, w)
        w.ok = false
        assertFalse(change.fromUri("content://x")); assertEquals(Wallpaper.NONE, prefs.wallpaper)
        w.ok = true
        assertTrue(change.fromUri("content://x")); assertEquals(Wallpaper.CUSTOM, prefs.wallpaper)
    }

    @Test fun replaceStockLauncherDisablesAndRemembers() {
        val prefs = FakePrefs(); val diag = FakeDiagnostics(); val sys = FakeSystem()
        ReplaceStockLauncher(diag, sys, prefs)()
        assertEquals(listOf("com.google.android.tvlauncher") to false, diag.lastLaunchers)
        assertEquals(setOf("com.google.android.tvlauncher"), prefs.disabledLaunchers)
    }

    @Test fun replaceStockLauncherKeepsNothingOnFailure() {
        val prefs = FakePrefs(); val diag = FakeDiagnostics().apply { launchersResult = false }
        ReplaceStockLauncher(diag, FakeSystem(), prefs)()
        assertTrue(prefs.disabledLaunchers.isEmpty())
    }

    @Test fun restoreStockLauncherEnablesAndClears() {
        val prefs = FakePrefs().apply { disabledLaunchers = setOf("a.b") }; val diag = FakeDiagnostics()
        assertTrue(RestoreStockLauncher(diag, prefs)())
        assertEquals(listOf("a.b") to true, diag.lastLaunchers)
        assertTrue(prefs.disabledLaunchers.isEmpty())
        assertFalse(RestoreStockLauncher(diag, prefs)())
    }
}
