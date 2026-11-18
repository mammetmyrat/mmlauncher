package com.mmlauncher.tv

import com.mmlauncher.tv.core.ImmediateScheduler
import com.mmlauncher.tv.domain.model.*
import com.mmlauncher.tv.domain.usecase.*
import com.mmlauncher.tv.presentation.tools.*
import org.junit.Assert.*
import org.junit.Test

class ToolsTest {

    private val log = listOf(
        "11-16 10:00:00.000 D/Tag(1): debug line",
        "11-16 10:00:01.000 I/Tag(1): info line",
        "11-16 10:00:02.000 W/Tag(1): warn line",
        "11-16 10:00:03.000 E/Tag(1): error line",
        "\tat com.example.Foo.bar(Foo.java:1)",          // stack trace continues the error
        "11-16 10:00:04.000 I/Tag(1): later info",
    )

    @Test fun `log level is read from a logcat time line`() {
        assertEquals(LogLevel.ERROR, LogParser.levelOf(log[3]))
        assertEquals(LogLevel.WARN, LogParser.levelOf(log[2]))
        assertNull("a stack trace line has no level of its own", LogParser.levelOf(log[4]))
        assertEquals(LogLevel.ERROR, LogParser.levelOf("11-16 10:00:00.000 F/libc(1): fatal"))
    }

    @Test fun `filter keeps the chosen level and above, and keeps stack traces with their error`() {
        val f = FilterLogs()
        assertEquals(log, f(log, LogLevel.VERBOSE))
        assertEquals(listOf(log[1], log[2], log[3], log[4], log[5]), f(log, LogLevel.INFO))
        assertEquals(listOf(log[3], log[4]), f(log, LogLevel.ERROR))   // the trace stays, "later info" goes
    }

    private fun logsStore(dump: LogDump, repo: FakeLogs = FakeLogs(dump), effects: MutableList<LogsEffect> = ArrayList()): LogsStore {
        val s = LogsStore(ImmediateScheduler(), ReadLogs(repo), FilterLogs(), SaveLogs(repo), ClearLogs(repo))
        s.bind({}, { effects += it }); return s
    }

    @Test fun `logs store loads, filters by level and saves what is shown`() {
        val repo = FakeLogs(LogDump(log, viaAdb = true, failure = null))
        val store = logsStore(repo.dump, repo)
        store.dispatch(LogsIntent.Load)
        assertEquals(6, store.state.lines.size); assertTrue(store.state.viaAdb)
        store.dispatch(LogsIntent.SetLevel(LogLevel.WARN))
        assertEquals(listOf(log[2], log[3], log[4]), store.state.lines)
        store.dispatch(LogsIntent.Save)
        assertEquals(store.state.lines, repo.saved)
    }

    @Test fun `without adb the logs store says it only has its own log`() {
        val store = logsStore(LogDump(listOf(log[1]), viaAdb = false, failure = AdbFailure.NOT_LISTENING))
        store.dispatch(LogsIntent.Load)
        assertFalse(store.state.viaAdb)
        assertEquals(AdbFailure.NOT_LISTENING, store.state.failure)
    }

    // ---- app manager ---------------------------------------------------------------------------

    private class Rig(apps: List<ManagedApp>) {
        val manager = FakeAppManager(apps)
        val screens = FakeScreens()
        val coreApps = FakeApps()
        val effects = ArrayList<AppManagerEffect>()
        val store = AppManagerStore(ImmediateScheduler(), ListManagedApps(manager), AppActionsFor(coreApps), PerformAppAction(manager, screens))
            .also { it.bind({}, { e -> effects += e }) }
    }

    @Test fun `list is sorted by name and hides system apps until asked`() {
        val r = Rig(listOf(managed("zeta"), managed("alpha"), managed("sys", system = true)))
        r.store.dispatch(AppManagerIntent.Load)
        assertEquals(listOf("alpha", "zeta"), r.store.state.apps.map { it.packageName })
        r.store.dispatch(AppManagerIntent.ToggleSystem)
        assertEquals(3, r.store.state.apps.size)
        assertTrue(r.store.state.showSystem)
    }

    @Test fun `menu offers enable for a disabled app and uninstall only when allowed`() {
        val r = Rig(listOf(managed("a", enabled = false)))
        r.coreApps.uninstallable = setOf("a")
        r.store.dispatch(AppManagerIntent.AppClicked(managed("a", enabled = false)))
        val menu = (r.effects.single() as AppManagerEffect.ShowMenu).actions
        assertTrue(AppAction.ENABLE in menu); assertFalse(AppAction.DISABLE in menu); assertTrue(AppAction.UNINSTALL in menu)
        r.coreApps.uninstallable = emptySet()
        r.store.dispatch(AppManagerIntent.AppClicked(managed("b")))
        val menu2 = (r.effects.last() as AppManagerEffect.ShowMenu).actions
        assertFalse(AppAction.UNINSTALL in menu2); assertTrue(AppAction.DISABLE in menu2)
    }

    @Test fun `destructive actions ask first and run only after confirmation`() {
        val r = Rig(listOf(managed("a")))
        val app = managed("a")
        r.store.dispatch(AppManagerIntent.Perform(app, AppAction.CLEAR_DATA))
        assertTrue(r.effects.last() is AppManagerEffect.AskConfirm)
        assertTrue("nothing ran yet", r.manager.performed.isEmpty())
        r.store.dispatch(AppManagerIntent.Perform(app, AppAction.CLEAR_DATA, confirmed = true))
        assertEquals(listOf(AppAction.CLEAR_DATA to "a"), r.manager.performed)
        assertTrue(r.effects.last() is AppManagerEffect.Toast)
    }

    @Test fun `harmless actions run at once and failures become messages`() {
        val r = Rig(listOf(managed("a")))
        r.store.dispatch(AppManagerIntent.Perform(managed("a"), AppAction.FORCE_STOP))
        assertEquals(listOf(AppAction.FORCE_STOP to "a"), r.manager.performed)
        r.manager.outcome = ActionOutcome.NeedsAdb(AdbFailure.NOT_LISTENING)
        r.store.dispatch(AppManagerIntent.Perform(managed("a"), AppAction.CLEAR_CACHE))
        assertTrue(r.effects.last() is AppManagerEffect.Toast)
        r.manager.outcome = ActionOutcome.Saved("/sdcard/Download/a.apk")
        r.store.dispatch(AppManagerIntent.Perform(managed("a"), AppAction.EXTRACT_APK))
        assertTrue(r.effects.last() is AppManagerEffect.Toast)
    }

    @Test fun `opening app info goes through the system screens, not the manager`() {
        val r = Rig(listOf(managed("a")))
        r.store.dispatch(AppManagerIntent.Perform(managed("a"), AppAction.OPEN_INFO))
        assertEquals(listOf("info:a"), r.screens.opened)
        assertTrue(r.manager.performed.isEmpty())
        assertTrue("no toast when the screen opened", r.effects.none { it is AppManagerEffect.Toast })
    }

    @Test fun `only clear data and disable need confirmation`() {
        assertEquals(setOf(AppAction.CLEAR_DATA, AppAction.DISABLE), AppAction.values().filter { it.needsConfirmation() }.toSet())
    }

    // ---- 7 taps on "Build" ------------------------------------------------------------------------

    private class UnlockRig(canWrite: Boolean, grantGivesPermission: Boolean = false) {
        val scheduler = ImmediateScheduler()
        val dev = FakeDeveloper(canWrite)
        val diag = FakeDiagnostics()
        val effects = ArrayList<DeviceInfoEffect>()
        val store = DeviceInfoStore(scheduler, GetDeviceReport(FakeDeviceInfo()), UnlockDeveloperMode(dev, diag))
            .also { it.bind({}, { e -> effects += e }) }
        init { if (grantGivesPermission) diag.onGrantWrite = { dev.canWrite = true } }
        fun tapBuild(times: Int) = repeat(times) { store.dispatch(DeviceInfoIntent.RowClicked(InfoKey.BUILD)) }
    }

    @Test fun `seven taps on Build switch developer mode on`() {
        val r = UnlockRig(canWrite = true)
        r.tapBuild(6); assertFalse(r.dev.dev)
        r.tapBuild(1)
        assertTrue(r.dev.dev)
        assertTrue(r.effects.last() is DeviceInfoEffect.Toast)
    }

    @Test fun `a countdown appears after the third tap`() {
        val r = UnlockRig(canWrite = true)
        r.tapBuild(2); assertTrue(r.effects.isEmpty())
        r.tapBuild(1); assertEquals(1, r.effects.size)
    }

    @Test fun `taps on other rows do nothing`() {
        val r = UnlockRig(canWrite = true)
        repeat(10) { r.store.dispatch(DeviceInfoIntent.RowClicked(InfoKey.MODEL)) }
        assertFalse(r.dev.dev); assertTrue(r.effects.isEmpty())
    }

    @Test fun `slow taps start over`() {
        val r = UnlockRig(canWrite = true)
        r.tapBuild(6)
        r.scheduler.runDelayed()          // 2.5 s without a tap
        r.tapBuild(1)
        assertFalse("the seventh tap came too late", r.dev.dev)
    }

    @Test fun `without permission the screen with the options is opened`() {
        val r = UnlockRig(canWrite = false)
        r.tapBuild(7)
        assertFalse(r.dev.dev)
        assertTrue(r.effects.contains(DeviceInfoEffect.OpenDeveloperScreen))
    }

    @Test fun `the permission is fetched through local adb when possible`() {
        val r = UnlockRig(canWrite = false, grantGivesPermission = true)
        r.tapBuild(7)
        assertTrue(r.dev.dev)
        assertFalse(r.effects.contains(DeviceInfoEffect.OpenDeveloperScreen))
    }

    @Test fun `already on is reported, not repeated`() {
        val r = UnlockRig(canWrite = true).also { it.dev.dev = true }
        assertEquals(UnlockResult.ALREADY_ON, UnlockDeveloperMode(r.dev, r.diag)())
    }
}
