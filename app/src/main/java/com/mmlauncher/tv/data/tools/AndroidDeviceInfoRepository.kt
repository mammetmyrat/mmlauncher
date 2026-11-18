package com.mmlauncher.tv.data.tools

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.media.MediaDrm
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.webkit.WebView
import com.mmlauncher.tv.domain.model.*
import com.mmlauncher.tv.domain.repository.DeviceInfoRepository
import java.util.UUID

/** Facts about the box that matter to TV developers and sellers: chip, display, Widevine, codecs. */
class AndroidDeviceInfoRepository(private val context: Context) : DeviceInfoRepository {

    override fun report() = DeviceReport(
        listOf(device(), processor(), display(), drm(), codecs(), software()),
    )

    private fun device() = InfoSection(InfoSectionId.DEVICE, listOf(
        InfoRow(InfoKey.MODEL, Build.MODEL),
        InfoRow(InfoKey.MANUFACTURER, Build.MANUFACTURER),
        InfoRow(InfoKey.PRODUCT, Build.PRODUCT),
        InfoRow(InfoKey.ANDROID, "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
        InfoRow(InfoKey.PATCH, Build.VERSION.SECURITY_PATCH.orEmpty().ifEmpty { "-" }),
        InfoRow(InfoKey.BUILD, Build.DISPLAY),
        InfoRow(InfoKey.KERNEL, System.getProperty("os.version").orEmpty().ifEmpty { "-" }),
    ))

    private fun processor(): InfoSection {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val data = try { StatFs(Environment.getDataDirectory().path).let { it.blockCountLong * it.blockSizeLong } } catch (e: Exception) { 0L }
        return InfoSection(InfoSectionId.PROCESSOR, listOf(
            InfoRow(InfoKey.ABIS, Build.SUPPORTED_ABIS.joinToString(", ")),
            InfoRow(InfoKey.CORES, Runtime.getRuntime().availableProcessors().toString()),
            InfoRow(InfoKey.RAM, "${mi.totalMem / MB} MB"),
            InfoRow(InfoKey.STORAGE, "${data / MB} MB"),
        ))
    }

    @Suppress("DEPRECATION")
    private fun display(): InfoSection {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val d = dm.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val m = DisplayMetrics().also { d.getRealMetrics(it) }
        val modes = d.supportedModes.map { "${it.physicalWidth}x${it.physicalHeight}@${it.refreshRate.toInt()}" }.distinct()
        val hdr = d.hdrCapabilities?.supportedHdrTypes?.toList()?.mapNotNull {
            when (it) { 1 -> "Dolby Vision"; 2 -> "HDR10"; 3 -> "HLG"; 4 -> "HDR10+"; else -> null }
        }.orEmpty()
        return InfoSection(InfoSectionId.DISPLAY, listOf(
            InfoRow(InfoKey.RESOLUTION, "${m.widthPixels} x ${m.heightPixels}"),
            InfoRow(InfoKey.DENSITY, "${m.densityDpi} dpi"),
            InfoRow(InfoKey.REFRESH, "${d.refreshRate.toInt()} Hz"),
            InfoRow(InfoKey.MODES, modes.joinToString(", ").ifEmpty { "-" }),
            InfoRow(InfoKey.HDR, hdr.joinToString(", ").ifEmpty { "-" }),
        ))
    }

    /** Widevine L1 = full HD/4K streaming allowed; L3 = software only (many services cap quality). */
    private fun drm(): InfoSection {
        val rows = ArrayList<InfoRow>()
        try {
            val drm = MediaDrm(WIDEVINE)
            try {
                rows += InfoRow(InfoKey.WIDEVINE, drm.getPropertyString("securityLevel"))
                rows += InfoRow(InfoKey.WIDEVINE_VERSION, drm.getPropertyString("version"))
                rows += InfoRow(InfoKey.DRM_VENDOR, drm.getPropertyString("vendor"))
                if (Build.VERSION.SDK_INT >= 28) rows += InfoRow(InfoKey.HDCP, hdcp(drm.connectedHdcpLevel))
            } finally {
                if (Build.VERSION.SDK_INT >= 28) drm.close() else @Suppress("DEPRECATION") drm.release()
            }
        } catch (e: Throwable) {
            rows += InfoRow(InfoKey.WIDEVINE, UNSUPPORTED)
        }
        return InfoSection(InfoSectionId.DRM, rows)
    }

    private fun hdcp(level: Int) = when (level) {
        MediaDrm.HDCP_NONE -> "none"; MediaDrm.HDCP_V1 -> "1.x"; MediaDrm.HDCP_V2 -> "2.0"
        MediaDrm.HDCP_V2_1 -> "2.1"; MediaDrm.HDCP_V2_2 -> "2.2"; MediaDrm.HDCP_V2_3 -> "2.3"
        MediaDrm.HDCP_NO_DIGITAL_OUTPUT -> "no digital output"; else -> "?"
    }

    private fun codecs(): InfoSection {
        val infos = try { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder } } catch (e: Throwable) { emptyList() }
        fun support(mime: String): String {
            val matches = infos.filter { i -> i.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
            if (matches.isEmpty()) return "-"
            val hw = matches.any { if (Build.VERSION.SDK_INT >= 29) it.isHardwareAccelerated else !it.name.startsWith("OMX.google") && !it.name.startsWith("c2.android") }
            return if (hw) "hardware" else "software"
        }
        return InfoSection(InfoSectionId.CODECS, listOf(
            InfoRow(InfoKey.AVC, support("video/avc")),
            InfoRow(InfoKey.HEVC, support("video/hevc")),
            InfoRow(InfoKey.VP9, support("video/x-vnd.on2.vp9")),
            InfoRow(InfoKey.AV1, support("video/av01")),
            InfoRow(InfoKey.MPEG2, support("video/mpeg2")),
        ))
    }

    private fun software(): InfoSection {
        val webview = try {
            if (Build.VERSION.SDK_INT >= 26) WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" } else null
        } catch (e: Throwable) { null }
        val launcher = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() } catch (e: Exception) { "" }
        return InfoSection(InfoSectionId.SOFTWARE, listOf(
            InfoRow(InfoKey.WEBVIEW, webview ?: "-"),
            InfoRow(InfoKey.LAUNCHER, launcher),
        ))
    }

    private companion object {
        const val MB = 1024L * 1024L
        const val UNSUPPORTED = "not supported"
        val WIDEVINE = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)
    }
}
