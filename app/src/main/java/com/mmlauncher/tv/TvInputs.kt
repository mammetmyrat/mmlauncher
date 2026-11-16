package com.mmlauncher.tv

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/** FR-14 / FR-15: pass-through TV inputs (HDMI etc.) via TvInputManager. */
object TvInputs {

    data class Input(val id: String, val label: String)

    /** Empty on boxes without the TV input framework, so the row simply has no HDMI cards. */
    fun list(context: Context): List<Input> {
        if (!context.packageManager.hasSystemFeature("android.software.live_tv")) return emptyList()
        return try {
            val tim = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
            tim.tvInputList
                .filter { it.isPassthroughInput }
                .map { Input(it.id, labelOf(context, it)) }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private fun labelOf(context: Context, info: TvInputInfo): String {
        val custom = info.loadCustomLabel(context)?.toString()?.takeIf { it.isNotBlank() }
        if (custom != null) return custom
        val label = info.loadLabel(context)?.toString()?.takeIf { it.isNotBlank() }
        return label ?: context.getString(R.string.input_fallback)
    }

    fun open(context: Context, inputId: String): Boolean = try {
        val uri = TvContract.buildChannelUriForPassthroughInput(inputId)
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}
