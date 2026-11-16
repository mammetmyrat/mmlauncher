package com.mmlauncher.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var icons: IconLoader
    private lateinit var allList: RecyclerView
    private lateinit var favList: RecyclerView
    private lateinit var favLabel: TextView
    private lateinit var hiddenBtn: TextView
    private lateinit var allAdapter: AppAdapter
    private lateinit var favAdapter: AppAdapter
    private val loader = Executors.newSingleThreadExecutor()
    private val net = Executors.newSingleThreadExecutor() // network never shares the app-loading thread

    private var installed: List<AppInfo> = emptyList()   // everything launchable
    private var visible: List<AppInfo> = emptyList()      // minus hidden
    private var favApps: List<AppInfo> = emptyList()      // ordered favorites

    // FR-6: remember what was focused, and in which row.
    private var lastPkg: String? = null
    private var lastInFav = false

    // FR-3: refresh on install / remove / update.
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reload()
    }

    private lateinit var sourcesList: RecyclerView
    private lateinit var wallpaperView: ImageView
    private lateinit var scrim: View
    private var appliedLanguage = "system"

    private lateinit var ramText: TextView
    private lateinit var ramBar: ProgressBar
    private val ui = Handler(Looper.getMainLooper())
    private var boosting = false
    private var updateCheckRunning = false
    private var pendingAutostart = false
    private var countdown: AlertDialog? = null

    // FR-20: refresh the RAM indicator every few seconds while the launcher is visible.
    private val ramTick = object : Runnable {
        override fun run() {
            updateRam()
            ui.postDelayed(this, 4000)
        }
    }

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(LocaleHelper.wrap(newBase))

    // FR-16: USB stick plugged in / removed -> refresh the sources row.
    private val storageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshSources()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        appliedLanguage = prefs.language
        wallpaperView = findViewById(R.id.wallpaper)
        scrim = findViewById(R.id.scrim)
        Fonts.apply(window.decorView)
        icons = IconLoader(packageManager)

        allAdapter = AppAdapter(icons, ::launch, ::showMenu) { lastPkg = it.packageName; lastInFav = false }
        favAdapter = AppAdapter(icons, ::launch, ::showMenu) { lastPkg = it.packageName; lastInFav = true }

        allList = setupRow(R.id.apps, allAdapter)
        favList = setupRow(R.id.favs, favAdapter)
        favLabel = findViewById(R.id.fav_label)
        hiddenBtn = findViewById(R.id.hidden_btn)
        hiddenBtn.setOnClickListener { showHiddenDialog() }

        ramText = findViewById(R.id.ram_text)
        ramBar = findViewById(R.id.ram_bar)
        findViewById<View>(R.id.ram_btn).setOnClickListener { boost() }

        // First run: short wizard (language, wallpaper, updates, default launcher).
        val firstRun = !prefs.wizardDone && savedInstanceState == null
        if (firstRun) startActivity(Intent(this, WizardActivity::class.java))

        // FR-19: only on a fresh start of the launcher, and only once per device boot.
        pendingAutostart = !firstRun && savedInstanceState == null && Autostart.isDue(this, prefs)

        findViewById<RecyclerView>(R.id.quick).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
            Mouse.enableWheel(this)
            adapter = QuickAdapter(
                listOf(
                    QuickItem("wifi", getString(R.string.q_wifi)),
                    QuickItem("network", getString(R.string.q_network)),
                    QuickItem("about", getString(R.string.q_about)),
                    QuickItem("developer", getString(R.string.q_developer)),
                    QuickItem("all", getString(R.string.q_all)),
                    QuickItem("datetime", getString(R.string.q_datetime)),
                    QuickItem("language", getString(R.string.q_language)),
                    QuickItem("display_sound", getString(R.string.q_display_sound)),
                    QuickItem("launcher", getString(R.string.q_launcher)),
                ),
                ::onQuick,
            )
            itemAnimator = null
        }

        sourcesList = findViewById<RecyclerView>(R.id.sources).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
            itemAnimator = null
            Mouse.enableWheel(this)
        }

        lastPkg = savedInstanceState?.getString("pkg")
        lastInFav = savedInstanceState?.getBoolean("fav") ?: false
    }

    private fun setupRow(id: Int, adapter: AppAdapter): RecyclerView =
        findViewById<RecyclerView>(id).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
            this.adapter = adapter
            itemAnimator = null
            setItemViewCacheSize(4)
            Mouse.enableWheel(this)
        }

    override fun onStart() {
        super.onStart()
        // Language was changed in launcher settings: rebuild with the new locale.
        if (prefs.language != appliedLanguage) { recreate(); return }
        loadWallpaper()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, f)
        val st = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addDataScheme("file")
        }
        registerReceiver(storageReceiver, st)
        refreshSources()
        ui.post(ramTick)
        maybeAutoCheckUpdates()
        reload()
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(storageReceiver) } catch (_: Exception) {}
        ui.removeCallbacks(ramTick)
        countdown?.dismiss()
        wallpaperView.setImageDrawable(null) // free the bitmap while we are in the background
    }

    /** Built-in wallpapers are tiny gradients; a custom image is decoded off the UI thread. */
    private fun loadWallpaper() {
        val key = prefs.wallpaper
        val builtIn = Wallpapers.builtIn[key]
        when {
            builtIn != null -> {
                wallpaperView.setImageResource(builtIn)
                setWallpaperVisible(true)
            }
            key == "custom" -> {
                setWallpaperVisible(true)
                loader.execute {
                    val bmp = Wallpapers.loadCustom(this)
                    runOnUiThread {
                        if (isDestroyed) return@runOnUiThread
                        if (bmp != null) wallpaperView.setImageBitmap(bmp) else setWallpaperVisible(false)
                    }
                }
            }
            else -> setWallpaperVisible(false)
        }
    }

    private fun setWallpaperVisible(on: Boolean) {
        wallpaperView.visibility = if (on) View.VISIBLE else View.GONE
        scrim.visibility = if (on) View.VISIBLE else View.GONE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("pkg", lastPkg)
        outState.putBoolean("fav", lastInFav)
    }

    // FR-33: Home press while already open -> back to the first card.
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        lastPkg = null
        focusApp()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) icons.clear()
    }

    // Menu key on the remote opens the same menu as a long press on OK.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                (currentFocus?.tag as? AppInfo)?.let { showMenu(it) }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    // ---- data -----------------------------------------------------------------------------

    private fun reload() {
        loader.execute {
            val loaded = AppRepository.load(this)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                installed = loaded
                render()
                focusApp()
                if (pendingAutostart) { pendingAutostart = false; startAutostart() }
            }
        }
    }

    /** Rebuild both rows from installed apps + prefs. Does not touch focus. */
    private fun render() {
        val hidden = prefs.hidden
        visible = installed.filter { it.packageName !in hidden }
        val byPkg = visible.associateBy { it.packageName }
        favApps = prefs.favorites.mapNotNull { byPkg[it] }

        allAdapter.submit(visible)
        favAdapter.submit(favApps)

        val showFavs = favApps.isNotEmpty()
        favLabel.visibility = if (showFavs) android.view.View.VISIBLE else android.view.View.GONE
        favList.visibility = if (showFavs) android.view.View.VISIBLE else android.view.View.GONE

        val hiddenCount = installed.count { it.packageName in hidden }
        hiddenBtn.visibility = if (hiddenCount > 0) android.view.View.VISIBLE else android.view.View.GONE
        hiddenBtn.text = getString(R.string.hidden_apps, hiddenCount)
    }

    /** Put focus on lastPkg in its row; fall back to the first favorite or first app. */
    private fun focusApp() {
        val pkg = lastPkg
        var row = if (lastInFav) favList else allList
        var adapter = if (lastInFav) favAdapter else allAdapter
        var idx = if (pkg != null) adapter.indexOf(pkg) else -1
        if (idx < 0 && pkg != null) { // the card may live only in the other row
            row = allList; adapter = allAdapter; idx = adapter.indexOf(pkg)
        }
        if (idx < 0) {
            if (favApps.isNotEmpty()) { row = favList; idx = 0 }
            else if (visible.isNotEmpty()) { row = allList; idx = 0 }
            else return
        }
        row.scrollToPosition(idx)
        requestFocusAt(row, idx, retries = 3)
    }

    private fun requestFocusAt(row: RecyclerView, idx: Int, retries: Int) {
        row.post {
            val v = row.findViewHolderForAdapterPosition(idx)?.itemView
            if (v != null) v.requestFocus() else if (retries > 0) requestFocusAt(row, idx, retries - 1)
        }
    }

    // ---- actions --------------------------------------------------------------------------

    private fun launch(app: AppInfo) {
        if (!AppRepository.launch(this, app)) toast(R.string.cant_open_app)
    }

    private fun showMenu(app: AppInfo) {
        val favs = prefs.favorites.toMutableList()
        val idx = favs.indexOf(app.packageName)
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        fun add(res: Int, action: () -> Unit) { labels += getString(res); actions += action }

        if (idx >= 0) {
            if (idx > 0) add(R.string.move_left) { moveFavorite(app, -1) }
            if (idx < favs.size - 1) add(R.string.move_right) { moveFavorite(app, +1) }
            add(R.string.remove_favorite) {
                prefs.favorites = favs - app.packageName
                lastPkg = app.packageName; lastInFav = false
                render(); focusApp()
            }
        } else {
            add(R.string.add_favorite) {
                prefs.favorites = favs + app.packageName
                lastPkg = app.packageName; lastInFav = true
                render(); focusApp()
            }
        }
        add(R.string.hide) {
            prefs.hidden = prefs.hidden + app.packageName
            lastPkg = null
            render(); focusApp()
        }
        add(R.string.app_info) { openAppInfo(app) }
        if (canUninstall(app)) add(R.string.uninstall) { uninstall(app) }

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(app.label)
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .show()
    }

    private fun moveFavorite(app: AppInfo, delta: Int) {
        val favs = prefs.favorites.toMutableList()
        val from = favs.indexOf(app.packageName)
        val to = from + delta
        if (from < 0 || to !in favs.indices) return
        favs[from] = favs[to].also { favs[to] = favs[from] }
        prefs.favorites = favs
        lastPkg = app.packageName; lastInFav = true
        render(); focusApp()
    }

    private fun showHiddenDialog() {
        val hiddenApps = installed.filter { it.packageName in prefs.hidden }
        if (hiddenApps.isEmpty()) return
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.unhide_title)
            .setItems(hiddenApps.map { it.label }.toTypedArray()) { _, which ->
                val app = hiddenApps[which]
                prefs.hidden = prefs.hidden - app.packageName
                lastPkg = app.packageName; lastInFav = false
                render(); focusApp()
            }
            .show()
    }

    // ---- updates (stage 7) -----------------------------------------------------------------

    /** Only with the user's consent, at most once a day, and never on the loader thread. */
    private fun maybeAutoCheckUpdates() {
        if (!prefs.autoUpdates) return
        if (System.currentTimeMillis() - prefs.lastUpdateCheck < 24L * 60 * 60 * 1000) return
        if (updateCheckRunning) return // onStart can fire twice in a row; one request is enough
        updateCheckRunning = true
        net.execute {
            UpdateChecker.check(this, prefs)
            runOnUiThread {
                updateCheckRunning = false
                if (!isDestroyed) refreshSources()
            }
        }
    }

    private fun showUpdateDialog() {
        val update = UpdateChecker.stored(this, prefs) ?: return
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(getString(R.string.upd_title, update.versionName))
            .setMessage(update.notes.ifEmpty { getString(R.string.upd_available, update.versionName) })
            .setPositiveButton(R.string.upd_install) { _, _ -> downloadAndInstall(update) }
            .setNegativeButton(R.string.upd_later, null)
            .show()
    }

    private fun downloadAndInstall(update: UpdateChecker.Update) {
        toast(R.string.upd_downloading)
        net.execute {
            val file = UpdateChecker.download(this, update)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (file == null) { toast(R.string.upd_dl_failed); return@runOnUiThread }
                try {
                    if (!UpdateChecker.install(this, file)) toast(R.string.upd_allow)
                } catch (e: Exception) {
                    toast(R.string.upd_dl_failed)
                }
            }
        }
    }

    // ---- RAM monitor and boost -------------------------------------------------------------

    private fun updateRam() {
        val r = Memory.ram(this)
        ramText.text = getString(R.string.ram_fmt, r.usedMb, r.totalMb)
        ramBar.progress = r.usedPercent
    }

    /** FR-21: ask the system to stop background apps, then report what was actually freed. */
    private fun boost() {
        if (boosting) return
        boosting = true
        loader.execute {
            val freed = Memory.boost(this)
            runOnUiThread {
                boosting = false
                if (isDestroyed) return@runOnUiThread
                updateRam()
                Toast.makeText(
                    this,
                    if (freed > 0) getString(R.string.boost_freed, freed) else getString(R.string.boost_none),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // ---- autostart (FR-17..19) -------------------------------------------------------------

    private fun startAutostart() {
        val app = installed.firstOrNull { it.packageName == prefs.autostartPkg } ?: return
        prefs.autostartBoot = Autostart.bootId(this) // mark as done for this boot right away
        val delay = prefs.autostartDelay
        if (delay <= 0) { launch(app); return }

        var left = delay
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(app.label)
            .setMessage(getString(R.string.as_starting, app.label, left))
            .setNegativeButton(R.string.cancel) { d, _ -> d.dismiss() }
            .create()
        countdown = dialog
        val tick = object : Runnable {
            override fun run() {
                if (!dialog.isShowing || isDestroyed) return
                left--
                if (left <= 0) {
                    dialog.dismiss()
                    launch(app)
                } else {
                    dialog.setMessage(getString(R.string.as_starting, app.label, left))
                    ui.postDelayed(this, 1000)
                }
            }
        }
        dialog.show()
        ui.postDelayed(tick, 1000)
    }

    // ---- sources: TV inputs (HDMI), USB, files ---------------------------------------------

    private fun refreshSources() {
        val items = ArrayList<QuickItem>()
        UpdateChecker.stored(this, prefs)?.let { items += QuickItem("update", getString(R.string.upd_available, it.versionName)) }
        // FR-14 / FR-15: HDMI and other pass-through inputs; hidden on boxes without a TV input framework.
        for (input in TvInputs.list(this)) items += QuickItem("input:${input.id}", input.label)
        // FR-16: every mounted USB / SD volume gets its own card.
        for (v in Storage.removable(this)) {
            items += QuickItem("usb:${v.dir.path}", getString(R.string.src_usb, v.label))
        }
        items += QuickItem("files", getString(R.string.src_files))
        sourcesList.adapter = QuickAdapter(items, ::onSource)
        Fonts.apply(sourcesList)
    }

    private fun onSource(item: QuickItem) {
        when {
            item.key == "update" -> showUpdateDialog()
            item.key == "files" -> startActivity(Intent(this, FileManagerActivity::class.java))
            item.key.startsWith("usb:") -> startActivity(
                Intent(this, FileManagerActivity::class.java).putExtra(FileManagerActivity.EXTRA_PATH, item.key.removePrefix("usb:"))
            )
            item.key.startsWith("input:") -> if (!TvInputs.open(this, item.key.removePrefix("input:"))) toast(R.string.cant_open_screen)
        }
    }

    // ---- quick settings -------------------------------------------------------------------

    private fun onQuick(item: QuickItem) {
        if (item.key == "developer") { openDeveloperMode(); return }
        if (item.key == "launcher") { startActivity(Intent(this, SettingsActivity::class.java)); return }
        if (!SettingsChains.open(this, item.key)) toast(R.string.cant_open_screen)
    }

    /** FR-9: if developer mode is off, explain how to turn it on and open "About". */
    private fun openDeveloperMode() {
        if (SettingsChains.isDeveloperModeOn(this)) {
            if (!SettingsChains.open(this, "developer")) toast(R.string.cant_open_screen)
            return
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.q_developer)
            .setMessage(R.string.dev_hint)
            .setPositiveButton(R.string.open) { _, _ ->
                if (!SettingsChains.open(this, "about")) toast(R.string.cant_open_screen)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openAppInfo(app: AppInfo) {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            toast(R.string.cant_open_screen)
        }
    }

    private fun canUninstall(app: AppInfo): Boolean = try {
        val flags = packageManager.getApplicationInfo(app.packageName, 0).flags
        flags and ApplicationInfo.FLAG_SYSTEM == 0 || flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
    } catch (e: Exception) {
        false
    }

    private fun uninstall(app: AppInfo) {
        try {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
        } catch (e: Exception) {
            toast(R.string.cant_open_screen)
        }
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    // Home launcher must not close on Back.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* stay on home */ }
}
