package com.mmlauncher.tv.presentation.tools

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import com.mmlauncher.tv.R
import com.mmlauncher.tv.appContainer
import com.mmlauncher.tv.domain.model.*
import com.mmlauncher.tv.domain.usecase.LogParser
import com.mmlauncher.tv.presentation.common.*
import com.mmlauncher.tv.presentation.developer.DeveloperActivity

/** Maps domain keys to localized text. */
private object InfoText {
    fun section(id: InfoSectionId) = when (id) {
        InfoSectionId.DEVICE -> R.string.sec_device
        InfoSectionId.PROCESSOR -> R.string.sec_cpu
        InfoSectionId.DISPLAY -> R.string.sec_display
        InfoSectionId.DRM -> R.string.sec_drm
        InfoSectionId.CODECS -> R.string.sec_codecs
        InfoSectionId.SOFTWARE -> R.string.sec_software
    }

    fun key(k: InfoKey) = when (k) {
        InfoKey.MODEL -> R.string.k_model; InfoKey.MANUFACTURER -> R.string.k_manufacturer
        InfoKey.PRODUCT -> R.string.k_product; InfoKey.ANDROID -> R.string.k_android
        InfoKey.PATCH -> R.string.k_patch; InfoKey.BUILD -> R.string.k_build
        InfoKey.KERNEL -> R.string.k_kernel; InfoKey.ABIS -> R.string.k_abis
        InfoKey.CORES -> R.string.k_cores; InfoKey.RAM -> R.string.k_ram; InfoKey.STORAGE -> R.string.k_storage
        InfoKey.RESOLUTION -> R.string.k_resolution; InfoKey.DENSITY -> R.string.k_density
        InfoKey.REFRESH -> R.string.k_refresh; InfoKey.MODES -> R.string.k_modes; InfoKey.HDR -> R.string.k_hdr
        InfoKey.WIDEVINE -> R.string.k_widevine; InfoKey.WIDEVINE_VERSION -> R.string.k_widevine_ver
        InfoKey.HDCP -> R.string.k_hdcp; InfoKey.DRM_VENDOR -> R.string.k_vendor
        InfoKey.AVC -> R.string.k_avc; InfoKey.HEVC -> R.string.k_hevc; InfoKey.VP9 -> R.string.k_vp9
        InfoKey.AV1 -> R.string.k_av1; InfoKey.MPEG2 -> R.string.k_mpeg2
        InfoKey.WEBVIEW -> R.string.k_webview; InfoKey.LAUNCHER -> R.string.k_launcher
    }

    fun value(ctx: Context, v: String) = when (v) {
        "hardware" -> ctx.getString(R.string.v_hw)
        "software" -> ctx.getString(R.string.v_sw)
        "not supported" -> ctx.getString(R.string.v_unsupported)
        else -> v
    }
}

// ============================ Tools menu ============================

class ToolsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        val screen = ListScreen(this, R.string.q_tools)
        fun go(c: Class<*>) = startActivity(Intent(this, c))
        screen.setChips(listOf(
            Chip(getString(R.string.tool_device)) { go(DeviceInfoActivity::class.java) },
            Chip(getString(R.string.tool_network)) { go(NetworkActivity::class.java) },
            Chip(getString(R.string.tool_apps)) { go(AppManagerActivity::class.java) },
            Chip(getString(R.string.tool_logs)) { go(LogsActivity::class.java) },
        ))
    }
}

// ============================ Device info ============================

class DeviceInfoActivity : BaseActivity() {
    private lateinit var store: DeviceInfoStore
    private lateinit var screen: ListScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        store = appContainer.deviceInfoStore()
        screen = ListScreen(this, R.string.tool_device)
        screen.setChips(listOf(Chip(getString(R.string.refresh)) { store.dispatch(DeviceInfoIntent.Load) }))
    }

    override fun onStart() { super.onStart(); store.bind(::render, ::onEffect); store.dispatch(DeviceInfoIntent.Load) }
    override fun onStop() { super.onStop(); store.unbind() }
    override fun onDestroy() { super.onDestroy(); store.clear() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (!screen.onBack()) super.onBackPressed() }

    private fun onEffect(e: DeviceInfoEffect) {
        when (e) {
            is DeviceInfoEffect.Toast -> Toast.makeText(this, e.msg.text(this), Toast.LENGTH_SHORT).show()
            DeviceInfoEffect.OpenDeveloperScreen -> startActivity(Intent(this, DeveloperActivity::class.java))
        }
    }

    private fun render(s: DeviceInfoState) {
        val report = s.report ?: return
        val items = ArrayList<InfoItem>()
        for (sec in report.sections) {
            items += InfoItem(InfoItem.Kind.HEADER, getString(InfoText.section(sec.id)))
            for (r in sec.rows) items += InfoItem(InfoItem.Kind.ROW, getString(InfoText.key(r.key)), InfoText.value(this, r.value), tag = r.key)
        }
        // 7 quick taps on the "Build" row switch developer mode on (like stock Android).
        screen.setItems(items) { item -> (item.tag as? InfoKey)?.let { store.dispatch(DeviceInfoIntent.RowClicked(it)) } }
    }
}

// ============================ Network & ADB ============================

class NetworkActivity : BaseActivity() {
    private lateinit var store: NetworkStore
    private lateinit var screen: ListScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        store = appContainer.networkStore()
        screen = ListScreen(this, R.string.tool_network)
        screen.setChips(listOf(Chip(getString(R.string.refresh)) { store.dispatch(NetworkIntent.Refresh) }))
    }

    override fun onStart() { super.onStart(); store.bind(::render) {}; store.dispatch(NetworkIntent.Refresh) }
    override fun onStop() { super.onStop(); store.unbind() }
    override fun onDestroy() { super.onDestroy(); store.clear() }

    private fun render(s: NetworkState) {
        val snap = s.snapshot ?: return
        val items = ArrayList<InfoItem>()
        if (snap.interfaces.isEmpty()) items += InfoItem(InfoItem.Kind.ROW, getString(R.string.net_none))
        for (n in snap.interfaces) {
            val kind = when (n.kind) { NetKind.WIFI -> R.string.net_wifi; NetKind.ETHERNET -> R.string.net_eth; NetKind.OTHER -> R.string.net_other }
            items += InfoItem(InfoItem.Kind.HEADER, "${getString(kind)} (${n.name})")
            n.ipv4?.let { items += InfoItem(InfoItem.Kind.ROW, getString(R.string.net_ip), it) }
            n.mac?.let { items += InfoItem(InfoItem.Kind.ROW, getString(R.string.net_mac), it) }
        }
        items += InfoItem(InfoItem.Kind.HEADER, getString(R.string.net_adb))
        items += InfoItem(InfoItem.Kind.ROW, getString(R.string.net_adb_status), getString(if (snap.adbListening) R.string.net_adb_on else R.string.net_adb_off))
        val ip = snap.interfaces.firstOrNull()?.ipv4
        if (snap.adbListening && ip != null) items += InfoItem(InfoItem.Kind.ROW, getString(R.string.net_cmd), "adb connect $ip:5555")
        screen.setItems(items)
        screen.setHint(if (snap.adbListening) null else getString(R.string.net_enable_hint, Hints.ENABLE_TCPIP))
    }
}

// ============================ App manager ============================

class AppManagerActivity : BaseActivity() {
    private lateinit var store: AppManagerStore
    private lateinit var screen: ListScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        store = appContainer.appManagerStore()
        screen = ListScreen(this, R.string.tool_apps)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (!screen.onBack()) super.onBackPressed() }

    override fun onStart() { super.onStart(); store.bind(::render, ::onEffect); store.dispatch(AppManagerIntent.Load) }
    override fun onStop() { super.onStop(); store.unbind() }
    override fun onDestroy() { super.onDestroy(); store.clear() }

    private fun render(s: AppManagerState) {
        screen.setChips(listOf(
            Chip(getString(if (s.showSystem) R.string.apps_hide_system else R.string.apps_show_system)) { store.dispatch(AppManagerIntent.ToggleSystem) },
            Chip(getString(R.string.refresh)) { store.dispatch(AppManagerIntent.Load) },
        ))
        val byPackage = s.apps.associateBy { it.packageName }
        val items = s.apps.map { app ->
            val flags = listOfNotNull(
                if (app.isSystem) getString(R.string.apps_system) else null,
                if (!app.isEnabled) getString(R.string.apps_disabled) else null,
            )
            val size = android.text.format.Formatter.formatShortFileSize(this, app.apkBytes)
            InfoItem(InfoItem.Kind.ROW, app.label, (flags + size + app.packageName).joinToString(" · "), tag = app.packageName,
                color = if (app.isEnabled) null else Color.GRAY)
        }
        screen.setItems(items) { item -> byPackage[item.tag]?.let { store.dispatch(AppManagerIntent.AppClicked(it)) } }
    }

    private fun label(a: AppAction) = getString(when (a) {
        AppAction.OPEN_INFO -> R.string.act_info; AppAction.UNINSTALL -> R.string.uninstall
        AppAction.FORCE_STOP -> R.string.act_force_stop; AppAction.CLEAR_CACHE -> R.string.act_clear_cache
        AppAction.CLEAR_DATA -> R.string.act_clear_data; AppAction.DISABLE -> R.string.act_disable
        AppAction.ENABLE -> R.string.act_enable; AppAction.EXTRACT_APK -> R.string.act_extract
    })

    private fun onEffect(e: AppManagerEffect) {
        when (e) {
            is AppManagerEffect.ShowMenu -> AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(e.app.label)
                .setItems(e.actions.map { label(it) }.toTypedArray()) { _, i -> store.dispatch(AppManagerIntent.Perform(e.app, e.actions[i])) }
                .show()
            is AppManagerEffect.AskConfirm -> AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.act_confirm_title)
                .setMessage(getString(if (e.action == AppAction.CLEAR_DATA) R.string.act_confirm_data else R.string.act_confirm_disable, e.app.label))
                .setPositiveButton(R.string.yes) { _, _ -> store.dispatch(AppManagerIntent.Perform(e.app, e.action, confirmed = true)) }
                .setNegativeButton(R.string.no, null)
                .show()
            is AppManagerEffect.Toast -> Toast.makeText(this, e.msg.text(this), Toast.LENGTH_LONG).show()
        }
    }
}

// ============================ Logs ============================

class LogsActivity : BaseActivity() {
    private lateinit var store: LogsStore
    private lateinit var screen: ListScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        store = appContainer.logsStore()
        screen = ListScreen(this, R.string.tool_logs)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (!screen.onBack()) super.onBackPressed() }

    override fun onStart() { super.onStart(); store.bind(::render, ::onEffect); store.dispatch(LogsIntent.Load) }
    override fun onStop() { super.onStop(); store.unbind() }
    override fun onDestroy() { super.onDestroy(); store.clear() }

    private fun render(s: LogsState) {
        val chips = LogLevel.values().map { lvl -> Chip(lvl.letter.toString(), s.level == lvl) { store.dispatch(LogsIntent.SetLevel(lvl)) } } + listOf(
            Chip(getString(R.string.logs_top)) { screen.scrollToTop() },
            Chip(getString(R.string.logs_end)) { screen.scrollToEnd() },
            Chip(getString(R.string.refresh)) { store.dispatch(LogsIntent.Load) },
            Chip(getString(R.string.logs_save)) { store.dispatch(LogsIntent.Save) },
            Chip(getString(R.string.logs_clear)) { store.dispatch(LogsIntent.Clear) },
        )
        screen.setChips(chips)
        screen.setHint(when {
            s.loading -> null
            s.viaAdb -> null
            s.failure == AdbFailure.WAITING_FOR_APPROVAL -> getString(R.string.adb_denied)
            else -> getString(R.string.logs_own_only, Hints.ENABLE_TCPIP)
        })
        var last = Color.parseColor("#9AA6B8")
        val items = s.lines.map { line ->
            when (LogParser.levelOf(line)) {
                LogLevel.ERROR -> last = Color.parseColor("#FF6B6B")
                LogLevel.WARN -> last = Color.parseColor("#FFB454")
                LogLevel.INFO -> last = Color.parseColor("#E8EDF5")
                LogLevel.DEBUG, LogLevel.VERBOSE -> last = Color.parseColor("#9AA6B8")
                null -> Unit
            }
            InfoItem(InfoItem.Kind.LOG, line, color = last)
        }
        screen.setItems(items, scrollToEnd = true)
    }

    private fun onEffect(e: LogsEffect) {
        when (e) { is LogsEffect.Toast -> Toast.makeText(this, e.msg.text(this), Toast.LENGTH_LONG).show() }
    }
}
