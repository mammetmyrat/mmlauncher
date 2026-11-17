package com.mmlauncher.tv

import com.mmlauncher.tv.core.ImmediateScheduler
import com.mmlauncher.tv.domain.model.*
import com.mmlauncher.tv.domain.usecase.*
import com.mmlauncher.tv.presentation.files.*
import com.mmlauncher.tv.presentation.home.*
import com.mmlauncher.tv.presentation.usage.*
import org.junit.Assert.*
import org.junit.Test

/** Stores run synchronously with [ImmediateScheduler]; effects are collected into a list. */
class StoresTest {

    // ---- Home --------------------------------------------------------------------------------

    private class HomeRig {
        val scheduler = ImmediateScheduler()
        val prefs = FakePrefs()
        val apps = FakeApps(listOf(app("a"), app("b"), app("c")))
        val system = FakeSystem()
        val updates = FakeUpdates()
        val screens = FakeScreens()
        private val fav = ManageFavorites(prefs)
        val store = HomeStore(
            scheduler, LoadHomeApps(apps, prefs), LoadHomeSources(system, updates),
            LoadLauncherSettings(prefs, apps, system), ReadRam(system), LaunchApp(apps), fav, ManageHidden(prefs),
            BuildAppMenu(fav, apps), ResolveAutostart(prefs, system), AutoCheckUpdates(prefs, updates, system),
            InstallUpdate(updates), OpenSystemScreen(screens), OpenAppInfo(screens), UninstallApp(screens),
            OpenTvInput(screens), IsWizardDone(prefs),
        )
        val effects = ArrayList<HomeEffect>()
        init { store.bind({}, { effects += it }) }
    }

    @Test fun `start loads apps, ram and the files card`() {
        val r = HomeRig()
        r.store.dispatch(HomeIntent.Create(true)); r.store.dispatch(HomeIntent.Start)
        val s = r.store.state
        assertTrue(s.loaded)
        assertEquals(listOf("a", "b", "c"), s.apps.map { it.packageName })
        assertEquals(RamInfo(2000, 500), s.ram)
        assertEquals(listOf(HomeSource.Files), s.sources)
    }

    @Test fun `first run opens the wizard and does not autostart`() {
        val r = HomeRig()
        r.prefs.wizardDone = false
        r.prefs.autostartPackage = "b"
        r.store.dispatch(HomeIntent.Create(true)); r.store.dispatch(HomeIntent.Start)
        assertTrue(r.effects.contains(HomeEffect.Navigate(Screen.WIZARD)))
        assertNull(r.store.state.countdown)
        assertTrue(r.apps.launched.isEmpty())
    }

    @Test fun `favorites add, move and hide update state and ask to focus the card`() {
        val r = HomeRig()
        r.store.dispatch(HomeIntent.Create(true)); r.store.dispatch(HomeIntent.Start)
        r.store.dispatch(HomeIntent.AddFavorite("a")); r.store.dispatch(HomeIntent.AddFavorite("b"))
        assertEquals(listOf("a", "b"), r.store.state.favorites.map { it.packageName })
        assertEquals(HomeEffect.FocusApp("b", true), r.effects.last())

        r.store.dispatch(HomeIntent.MoveFavorite("b", -1))
        assertEquals(listOf("b", "a"), r.store.state.favorites.map { it.packageName })

        r.store.dispatch(HomeIntent.Hide("c"))
        assertEquals(listOf("a", "b"), r.store.state.apps.map { it.packageName })
        assertEquals(listOf("c"), r.store.state.hiddenApps.map { it.packageName })
        r.store.dispatch(HomeIntent.Unhide("c"))
        assertEquals(3, r.store.state.apps.size)
        assertTrue(r.store.state.hiddenApps.isEmpty())
    }

    @Test fun `autostart counts down once per second, then launches`() {
        val r = HomeRig()
        r.prefs.autostartPackage = "b"; r.prefs.autostartDelaySeconds = 2
        r.store.dispatch(HomeIntent.Create(true)); r.store.dispatch(HomeIntent.Start)
        assertEquals(2, r.store.state.countdown?.secondsLeft)
        r.scheduler.runDelayed()                       // one second passes (the RAM tick is also queued)
        assertEquals(1, r.store.state.countdown?.secondsLeft)
        r.scheduler.runDelayed()
        assertNull(r.store.state.countdown)
        assertEquals(listOf("b"), r.apps.launched)
    }

    @Test fun `autostart can be cancelled and is not repeated on recreate`() {
        val r = HomeRig()
        r.prefs.autostartPackage = "b"; r.prefs.autostartDelaySeconds = 5
        r.store.dispatch(HomeIntent.Create(true)); r.store.dispatch(HomeIntent.Start)
        r.store.dispatch(HomeIntent.CancelCountdown)
        assertNull(r.store.state.countdown)
        r.scheduler.runDelayed(); r.scheduler.runDelayed()
        assertTrue("cancelled: nothing launched", r.apps.launched.isEmpty())

        val again = HomeRig().also { it.prefs.autostartPackage = "b" }
        again.store.dispatch(HomeIntent.Create(freshStart = false)); again.store.dispatch(HomeIntent.Start)
        assertNull("rotation / language change must not autostart", again.store.state.countdown)
    }

    @Test fun `a failing launch becomes a toast, not a crash`() {
        val r = HomeRig(); r.apps.launchResult = false
        r.store.dispatch(HomeIntent.Launch(app("a")))
        assertTrue(r.effects.single() is HomeEffect.Toast)
    }

    @Test fun `quick actions navigate or open the system screen`() {
        val r = HomeRig()
        r.store.dispatch(HomeIntent.Quick(QuickAction.RAM))
        assertEquals(HomeEffect.Navigate(Screen.RAM), r.effects.last())
        r.store.dispatch(HomeIntent.Quick(QuickAction.SYSTEM_SETTINGS))
        assertEquals(listOf(SystemScreen.ALL), r.screens.opened)
        r.screens.ok = false
        r.store.dispatch(HomeIntent.Quick(QuickAction.SYSTEM_SETTINGS))
        assertTrue(r.effects.last() is HomeEffect.Toast)
    }

    @Test fun `an available update shows as the first card and opens its dialog`() {
        val r = HomeRig()
        val info = UpdateInfo(2, "0.2.0", "https://x", "h", "")
        r.updates.stored = info
        r.store.dispatch(HomeIntent.Start)
        assertEquals(HomeSource.UpdateAvailable(info), r.store.state.sources.first())
        r.store.dispatch(HomeIntent.SourceClicked(HomeSource.UpdateAvailable(info)))
        assertEquals(HomeEffect.ShowUpdateDialog(info), r.effects.last())
    }

    @Test fun `stop cancels timers and the countdown`() {
        val r = HomeRig()
        r.prefs.autostartPackage = "b"
        r.store.dispatch(HomeIntent.Create(true)); r.store.dispatch(HomeIntent.Start)
        r.store.dispatch(HomeIntent.Stop)
        assertNull(r.store.state.countdown)
        r.scheduler.runDelayed()
        assertTrue(r.apps.launched.isEmpty())
    }

    // ---- RAM / storage -------------------------------------------------------------------------

    @Test fun `ram screen without adb shows only the launcher itself and says why`() {
        val store = RamStore(ImmediateScheduler(), GetRamUsage(FakeSystem(), FakeDiagnostics()), BoostMemory(FakeSystem()), StopApp(FakeDiagnostics()), "self")
        store.dispatch(UsageIntent.Refresh)
        val s = store.state
        assertTrue(s.items.single().isSelf)
        assertEquals(UsageHint.NEED_ADB, s.hint)
        assertEquals(75, s.ram?.usedPercent)   // 1500 of 2000 MB in use
    }

    @Test fun `ram screen with adb lists processes and stops a tapped app`() {
        val diag = FakeDiagnostics().apply {
            memory = Fetch.Ok(listOf(ProcessMemory("p", "p", "P", 1000), ProcessMemory("self", "self", "Me", 500)))
        }
        val effects = ArrayList<UsageEffect>()
        val store = RamStore(ImmediateScheduler(), GetRamUsage(FakeSystem(), diag), BoostMemory(FakeSystem()), StopApp(diag), "self")
        store.bind({}, { effects += it })
        store.dispatch(UsageIntent.Refresh)
        assertEquals(listOf("P", "Me"), store.state.items.map { it.label })
        assertEquals(UsageHint.TAP_TO_STOP, store.state.hint)

        store.dispatch(UsageIntent.ItemClicked(store.state.items[0]))
        store.dispatch(UsageIntent.ItemClicked(store.state.items[1])) // the launcher itself is never stopped
        assertEquals(listOf("p"), diag.stopped)
    }

    @Test fun `boost reports the freed memory`() {
        val effects = ArrayList<UsageEffect>()
        val store = RamStore(ImmediateScheduler(), GetRamUsage(FakeSystem(), FakeDiagnostics()), BoostMemory(FakeSystem()), StopApp(FakeDiagnostics()), "self")
        store.bind({}, { effects += it })
        store.dispatch(UsageIntent.Boost)
        assertTrue(effects.single() is UsageEffect.Toast)
    }

    // ---- Files ---------------------------------------------------------------------------------

    private val tree = mapOf(
        "/sdcard" to listOf(FileEntry("Music", "/sdcard/Music", true, 0), FileEntry("a.png", "/sdcard/a.png", false, 5)),
        "/sdcard/Music" to listOf(FileEntry("s.mp3", "/sdcard/Music/s.mp3", false, 9)),
    )

    private fun filesStore(files: FakeFiles = FakeFiles(tree), effects: MutableList<FilesEffect> = ArrayList()): FilesStore {
        val s = FilesStore(ImmediateScheduler(), HasFileAccess(files), ListDirectory(files), OpenFile(files), IsImageFile(files),
            FileRoots(files), ChangeWallpaper(FakePrefs(), FakeWallpapers()))
        s.bind({}, { effects += it })
        return s
    }

    @Test fun `files start at the storage roots and go down and back up with focus on the folder left`() {
        val store = filesStore()
        store.dispatch(FilesIntent.Init(null))
        assertNull(store.state.path)
        store.dispatch(FilesIntent.Open(store.state.entries.single()))          // Internal
        assertEquals("/sdcard", store.state.path)
        store.dispatch(FilesIntent.Open(store.state.entries.first { it.isDirectory }))   // Music
        assertEquals("/sdcard/Music", store.state.path)
        store.dispatch(FilesIntent.Back)
        assertEquals("/sdcard", store.state.path)
        assertEquals("/sdcard/Music", store.state.focusPath)
        store.dispatch(FilesIntent.Back)                                         // root of a volume -> roots list
        assertNull(store.state.path)
    }

    @Test fun `back on the roots list leaves the screen`() {
        val effects = ArrayList<FilesEffect>()
        val store = filesStore(effects = effects)
        store.dispatch(FilesIntent.Init(null)); store.dispatch(FilesIntent.Back)
        assertEquals(FilesEffect.Finish, effects.last())
    }

    @Test fun `without access the store asks for it and recovers after the grant`() {
        val files = FakeFiles(tree).apply { access = false }
        val effects = ArrayList<FilesEffect>()
        val store = filesStore(files, effects)
        store.dispatch(FilesIntent.Init(null))
        assertEquals(FilesEffect.RequestAccess, effects.single())
        assertFalse(store.state.ready)
        files.access = true
        store.dispatch(FilesIntent.Recheck)
        assertTrue(store.state.ready)
    }

    @Test fun `coming back from the permission screen without access is reported, not silent`() {
        val files = FakeFiles(tree).apply { access = false }
        val effects = ArrayList<FilesEffect>()
        val store = filesStore(files, effects)
        store.dispatch(FilesIntent.Init(null)); store.dispatch(FilesIntent.Recheck)
        assertTrue(effects.contains(FilesEffect.Finish))
    }

    @Test fun `long press offers wallpaper only for images`() {
        val effects = ArrayList<FilesEffect>()
        val store = filesStore(effects = effects)
        store.dispatch(FilesIntent.LongPress(FileEntry("a.png", "/sdcard/a.png", false, 5)))
        store.dispatch(FilesIntent.LongPress(FileEntry("s.mp3", "/sdcard/s.mp3", false, 5)))
        store.dispatch(FilesIntent.LongPress(FileEntry("Music", "/sdcard/Music", true, 0)))
        val menus = effects.filterIsInstance<FilesEffect.ShowFileMenu>()
        assertEquals(listOf(true, false), menus.map { it.isImage }) // the folder has no menu
    }
}
