package com.mmlauncher.tv.domain.usecase

import com.mmlauncher.tv.domain.model.*
import com.mmlauncher.tv.domain.repository.*

class GetDeviceReport(private val repo: DeviceInfoRepository) {
    operator fun invoke() = repo.report()
}

class GetNetworkSnapshot(private val repo: NetworkRepository) {
    operator fun invoke() = repo.snapshot()
}

class ListManagedApps(private val repo: AppManagerRepository) {
    /** Sorted by name; the caller decides whether system apps are included. */
    operator fun invoke(includeSystem: Boolean): List<ManagedApp> =
        repo.list(includeSystem).sortedBy { it.label.lowercase() }
}

/** Actions offered for an app in the manager's menu. */
class AppActionsFor(private val apps: AppsRepository) {
    operator fun invoke(app: ManagedApp): List<AppAction> = buildList {
        add(AppAction.OPEN_INFO)
        add(AppAction.FORCE_STOP)
        add(AppAction.CLEAR_CACHE)
        add(AppAction.CLEAR_DATA)
        add(if (app.isEnabled) AppAction.DISABLE else AppAction.ENABLE)
        add(AppAction.EXTRACT_APK)
        if (apps.canUninstall(app.packageName)) add(AppAction.UNINSTALL)
    }
}

/** Actions that destroy something ask the user to confirm first. */
fun AppAction.needsConfirmation() = this == AppAction.CLEAR_DATA || this == AppAction.DISABLE

class PerformAppAction(
    private val manager: AppManagerRepository,
    private val screens: SystemScreens,
) {
    operator fun invoke(action: AppAction, packageName: String): ActionOutcome = when (action) {
        AppAction.OPEN_INFO -> if (screens.openAppInfo(packageName)) ActionOutcome.Done else ActionOutcome.Failed
        AppAction.UNINSTALL -> if (screens.uninstall(packageName)) ActionOutcome.Done else ActionOutcome.Failed
        else -> manager.perform(action, packageName)
    }
}

// ---- logs ------------------------------------------------------------------------------------

object LogParser {
    private val LEVEL = Regex("""^\d\d-\d\d \d\d:\d\d:\d\d\.\d+\s+([VDIWEF])/""")

    /** Level of a `logcat -v time` line; null for continuation lines (stack traces...). */
    fun levelOf(line: String): LogLevel? {
        val c = LEVEL.find(line)?.groupValues?.get(1)?.single() ?: return null
        return when (c) {
            'V' -> LogLevel.VERBOSE; 'D' -> LogLevel.DEBUG; 'I' -> LogLevel.INFO
            'W' -> LogLevel.WARN; else -> LogLevel.ERROR // 'E' and 'F' (fatal)
        }
    }
}

class FilterLogs {
    /** Keeps lines at [min] or more severe; continuation lines follow the line before them. */
    operator fun invoke(lines: List<String>, min: LogLevel): List<String> {
        if (min == LogLevel.VERBOSE) return lines
        var current = LogLevel.VERBOSE
        val out = ArrayList<String>()
        for (line in lines) {
            LogParser.levelOf(line)?.let { current = it }
            if (current.ordinal >= min.ordinal) out += line
        }
        return out
    }
}

class ReadLogs(private val repo: LogsRepository) {
    operator fun invoke(maxLines: Int = 600) = repo.read(maxLines)
}

class SaveLogs(private val repo: LogsRepository) {
    operator fun invoke(lines: List<String>) = repo.save(lines)
}

class ClearLogs(private val repo: LogsRepository) {
    operator fun invoke() = repo.clear()
}
