package com.mmlauncher.tv.presentation.home

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mmlauncher.tv.R
import com.mmlauncher.tv.appContainer
import com.mmlauncher.tv.domain.model.AppInfo
import com.mmlauncher.tv.domain.model.HomeSource
import com.mmlauncher.tv.domain.model.Wallpaper
import com.mmlauncher.tv.domain.usecase.AppMenu
import com.mmlauncher.tv.presentation.common.AppAdapter
import com.mmlauncher.tv.presentation.common.BaseActivity
import com.mmlauncher.tv.presentation.common.BuiltInUi
import com.mmlauncher.tv.presentation.common.Fonts
import com.mmlauncher.tv.presentation.common.IconLoader
import com.mmlauncher.tv.presentation.common.Mouse
import com.mmlauncher.tv.presentation.common.QuickAdapter
import com.mmlauncher.tv.presentation.common.QuickItem
import com.mmlauncher.tv.presentation.developer.DeveloperActivity
import com.mmlauncher.tv.presentation.files.FileManagerActivity
import com.mmlauncher.tv.presentation.settings.SettingsActivity
import com.mmlauncher.tv.presentation.tools.ToolsActivity
import com.mmlauncher.tv.presentation.usage.RamActivity
import com.mmlauncher.tv.presentation.usage.StorageActivity
import com.mmlauncher.tv.presentation.wizard.WizardActivity
import java.util.concurrent.Executors

/**
 * Home screen. A "dumb" MVI View: it renders [HomeState], reports user actions as [HomeIntent]s and
 * performs one-shot [HomeEffect]s (navigation, dialogs). All decisions live in [HomeStore].
 */
class MainActivity : BaseActivity() {

    private lateinit var store: HomeStore
    private lateinit var icons: IconLoader
    private lateinit var allList: RecyclerView
    private lateinit var favList: RecyclerView
    private lateinit var sourcesList: RecyclerView
    private lateinit var favLabel: TextView
    private lateinit var hiddenBtn: TextView
    private lateinit var allAdapter: AppAdapter
    private lateinit var favAdapter: AppAdapter
    private lateinit var wallpaperView: ImageView
    private lateinit var scrim: View
    private lateinit var ramText: TextView
    private lateinit var ramBar: ProgressBar
    private val bitmapLoader = Executors.newSingleThreadExecutor()

    // What is currently drawn, so frequent state updates (the RAM tick) do not reset lists and focus.
    private var shown = HomeState()
    private var appliedLanguage = com.mmlauncher.tv.domain.model.Language.SYSTEM
    private var shownWallpaper: Wallpaper? = null
    private var countdownDialog: AlertDialog? = null

    // FR-6: remember what was focused, and in which row (pure UI memory).
    private var lastPkg: String? = null
    private var lastInFav = false

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = store.dispatch(HomeIntent.PackagesChanged)
    }
    private val storageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = store.dispatch(HomeIntent.VolumesChanged)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = appContainer.homeStore()
        appliedLanguage = appContainer.preferences.language

        wallpaperView = findViewById(R.id.wallpaper)
        scrim = findViewById(R.id.scrim)
        favLabel = findViewById(R.id.fav_label)
        hiddenBtn = findViewById(R.id.hidden_btn)
        ramText = findViewById(R.id.ram_text)
        ramBar = findViewById(R.id.ram_bar)
        Fonts.apply(window.decorView)
        icons = IconLoader(packageManager)

        allAdapter = AppAdapter(icons, { store.dispatch(HomeIntent.Launch(it)) }, { store.dispatch(HomeIntent.MenuRequested(it)) }) {
            lastPkg = it.packageName; lastInFav = false
        }
        favAdapter = AppAdapter(icons, { store.dispatch(HomeIntent.Launch(it)) }, { store.dispatch(HomeIntent.MenuRequested(it)) }) {
            lastPkg = it.packageName; lastInFav = true
        }
        allList = setupRow(R.id.apps, allAdapter)
        favList = setupRow(R.id.favs, favAdapter)
        hiddenBtn.setOnClickListener { showHiddenDialog() }
        findViewById<View>(R.id.ram_btn).setOnClickListener { store.dispatch(HomeIntent.Quick(QuickAction.RAM)) }

        findViewById<RecyclerView>(R.id.quick).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
            Mouse.enableWheel(this)
            itemAnimator = null
            adapter = QuickAdapter(
                listOf(
                    QuickItem("developer", getString(R.string.q_developer)),
                    QuickItem("system", getString(R.string.q_system)),
                    QuickItem("ram", getString(R.string.q_ram)),
                    QuickItem("storage", getString(R.string.q_storage)),
                    QuickItem("tools", getString(R.string.q_tools)),
                    QuickItem("launcher", getString(R.string.q_launcher)),
                ),
            ) { item ->
                val action = when (item.key) {
                    "developer" -> QuickAction.DEVELOPER
                    "ram" -> QuickAction.RAM
                    "storage" -> QuickAction.STORAGE
                    "tools" -> QuickAction.TOOLS
                    "launcher" -> QuickAction.LAUNCHER_SETTINGS
                    else -> QuickAction.SYSTEM_SETTINGS
                }
                store.dispatch(HomeIntent.Quick(action))
            }
        }
        sourcesList = findViewById<RecyclerView>(R.id.sources).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
            itemAnimator = null
            Mouse.enableWheel(this)
        }

        lastPkg = savedInstanceState?.getString("pkg")
        lastInFav = savedInstanceState?.getBoolean("fav") ?: false
        store.dispatch(HomeIntent.Create(freshStart = savedInstanceState == null))
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
        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        })
        registerReceiver(storageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addDataScheme("file")
        })
        shownWallpaper = null // reload the image after being in the background
        store.bind(::render, ::onEffect)
        store.dispatch(HomeIntent.Start)
    }

    override fun onStop() {
        super.onStop()
        store.dispatch(HomeIntent.Stop)
        store.unbind()
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(storageReceiver) } catch (_: Exception) {}
        countdownDialog?.dismiss(); countdownDialog = null
        wallpaperView.setImageDrawable(null) // free the bitmap while we are in the background
    }

    override fun onDestroy() {
        super.onDestroy()
        store.clear()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("pkg", lastPkg)
        outState.putBoolean("fav", lastInFav)
    }

    // FR-33: Home press while already open -> back to the first card.
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        store.dispatch(HomeIntent.HomePressed)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) icons.clear()
    }

    // Menu key on the remote opens the same menu as a long press on OK.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                (currentFocus?.tag as? AppInfo)?.let { store.dispatch(HomeIntent.MenuRequested(it)) }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    // Home launcher must not close on Back.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* stay on home */ }

    // ---- rendering --------------------------------------------------------------------------

    private fun render(state: HomeState) {
        // Language was changed in launcher settings: rebuild with the new locale.
        if (state.language != appliedLanguage && state.loaded) { recreate(); return }

        val listsChanged = state.apps != shown.apps || state.favorites != shown.favorites
        if (state.apps != shown.apps) allAdapter.submit(state.apps)
        if (state.favorites != shown.favorites) {
            favAdapter.submit(state.favorites)
            val show = if (state.favorites.isNotEmpty()) View.VISIBLE else View.GONE
            favLabel.visibility = show
            favList.visibility = show
        }
        if (state.hiddenApps != shown.hiddenApps) {
            hiddenBtn.visibility = if (state.hiddenApps.isNotEmpty()) View.VISIBLE else View.GONE
            hiddenBtn.text = getString(R.string.hidden_apps, state.hiddenApps.size)
        }
        state.ram?.let { r ->
            ramText.text = getString(R.string.ram_fmt, r.usedMb, r.totalMb)
            ramBar.progress = r.usedPercent
        }
        if (state.sources != shown.sources) renderSources(state.sources)
        if (state.wallpaper != shownWallpaper) { shownWallpaper = state.wallpaper; showWallpaper(state.wallpaper) }
        renderCountdown(state.countdown)
        shown = state
        if (listsChanged && state.loaded) focusApp()
    }

    private fun renderSources(sources: List<HomeSource>) {
        val items = sources.map { s ->
            when (s) {
                is HomeSource.UpdateAvailable -> QuickItem("update", getString(R.string.upd_available, s.info.versionName))
                is HomeSource.Input -> QuickItem("input:${s.input.id}", s.input.label)
                is HomeSource.Usb -> QuickItem("usb:${s.volume.path}", getString(R.string.src_usb, s.volume.label))
            }
        }
        sourcesList.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE // only the USB cards live here now
        sourcesList.adapter = QuickAdapter(items) { clicked ->
            val index = items.indexOf(clicked)
            sources.getOrNull(index)?.let { store.dispatch(HomeIntent.SourceClicked(it)) }
        }
        Fonts.apply(sourcesList)
    }

    /** Built-in wallpapers are tiny gradients; a custom image is decoded off the UI thread. */
    private fun showWallpaper(wallpaper: Wallpaper) {
        val builtIn = when (wallpaper) {
            Wallpaper.NIGHT -> R.drawable.wall_night
            Wallpaper.OCEAN -> R.drawable.wall_ocean
            Wallpaper.VIOLET -> R.drawable.wall_violet
            Wallpaper.SUNSET -> R.drawable.wall_sunset
            else -> null
        }
        when {
            builtIn != null -> { wallpaperView.setImageResource(builtIn); setWallpaperVisible(true) }
            wallpaper == Wallpaper.CUSTOM -> {
                setWallpaperVisible(true)
                val path = appContainer.wallpaperStorage.customPath()
                bitmapLoader.execute {
                    val bmp = path?.let { BitmapFactory.decodeFile(it, BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 }) }
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

    private fun renderCountdown(c: Countdown?) {
        if (c == null) { countdownDialog?.dismiss(); countdownDialog = null; return }
        val text = getString(R.string.as_starting, c.app.label, c.secondsLeft)
        val existing = countdownDialog
        if (existing != null && existing.isShowing) { existing.setMessage(text); return }
        countdownDialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(c.app.label)
            .setMessage(text)
            .setNegativeButton(R.string.cancel) { _, _ -> store.dispatch(HomeIntent.CancelCountdown) }
            .setOnCancelListener { store.dispatch(HomeIntent.CancelCountdown) }
            .show()
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
            if (shown.favorites.isNotEmpty()) { row = favList; idx = 0 }
            else if (shown.apps.isNotEmpty()) { row = allList; idx = 0 }
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

    // ---- effects ----------------------------------------------------------------------------

    private fun onEffect(effect: HomeEffect) {
        when (effect) {
            is HomeEffect.Navigate -> navigate(effect)
            is HomeEffect.Toast -> Toast.makeText(this, effect.msg.text(this), Toast.LENGTH_SHORT).show()
            is HomeEffect.FocusApp -> { lastPkg = effect.packageName; lastInFav = effect.inFavorites; focusApp() }
            is HomeEffect.ShowAppMenu -> showMenu(effect.menu)
            is HomeEffect.ShowUpdateDialog -> showUpdateDialog(effect)
        }
    }

    private fun navigate(effect: HomeEffect.Navigate) {
        val target = when (effect.screen) {
            Screen.DEVELOPER -> DeveloperActivity::class.java
            Screen.RAM -> RamActivity::class.java
            Screen.STORAGE -> StorageActivity::class.java
            Screen.TOOLS -> ToolsActivity::class.java
            Screen.SETTINGS -> SettingsActivity::class.java
            Screen.FILES -> FileManagerActivity::class.java
            Screen.WIZARD -> WizardActivity::class.java
        }
        val intent = Intent(this, target)
        effect.path?.let { intent.putExtra(FileManagerActivity.EXTRA_PATH, it) }
        startActivity(intent)
    }

    private fun showMenu(menu: AppMenu) {
        val pkg = menu.app.packageName
        val labels = ArrayList<String>()
        val actions = ArrayList<HomeIntent>()
        fun add(res: Int, intent: HomeIntent) { labels += getString(res); actions += intent }

        if (menu.isFavorite) {
            if (menu.canMoveLeft) add(R.string.move_left, HomeIntent.MoveFavorite(pkg, -1))
            if (menu.canMoveRight) add(R.string.move_right, HomeIntent.MoveFavorite(pkg, +1))
            add(R.string.remove_favorite, HomeIntent.RemoveFavorite(pkg))
        } else {
            add(R.string.add_favorite, HomeIntent.AddFavorite(pkg))
        }
        add(R.string.hide, HomeIntent.Hide(pkg))
        if (!menu.isBuiltIn) add(R.string.app_info, HomeIntent.ShowAppInfo(pkg))
        if (menu.canUninstall) add(R.string.uninstall, HomeIntent.Uninstall(pkg))

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(displayName(menu.app))
            .setItems(labels.toTypedArray()) { _, which -> store.dispatch(actions[which]) }
            .show()
    }

    /** Name shown to the user: the launcher's own cards are localized here. */
    private fun displayName(app: AppInfo): String =
        BuiltInUi.of(app)?.let { getString(it.label) } ?: app.label

    private fun showHiddenDialog() {
        val hidden = shown.hiddenApps
        if (hidden.isEmpty()) return
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.unhide_title)
            .setItems(hidden.map { displayName(it) }.toTypedArray()) { _, which ->
                store.dispatch(HomeIntent.Unhide(hidden[which].packageName))
            }
            .show()
    }

    private fun showUpdateDialog(effect: HomeEffect.ShowUpdateDialog) {
        val info = effect.info
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(getString(R.string.upd_title, info.versionName))
            .setMessage(info.notes.ifEmpty { getString(R.string.upd_available, info.versionName) })
            .setPositiveButton(R.string.upd_install) { _, _ -> store.dispatch(HomeIntent.InstallUpdate(info)) }
            .setNegativeButton(R.string.upd_later, null)
            .show()
    }
}
