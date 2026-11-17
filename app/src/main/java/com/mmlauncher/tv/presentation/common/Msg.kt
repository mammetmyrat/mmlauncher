package com.mmlauncher.tv.presentation.common

import android.content.Context
import androidx.annotation.StringRes

/** A message the View turns into text; stores stay free of Context and of string lookups. */
data class Msg(@StringRes val res: Int, val args: List<Any> = emptyList()) {
    fun text(context: Context): String =
        if (args.isEmpty()) context.getString(res) else context.getString(res, *args.toTypedArray())
}

fun msg(@StringRes res: Int, vararg args: Any) = Msg(res, args.toList())

/** Commands shown to the user (typed on a computer). */
object Hints {
    const val ENABLE_TCPIP = "adb tcpip 5555"
    const val GRANT_SECURE = "adb shell pm grant com.mmlauncher.tv android.permission.WRITE_SECURE_SETTINGS"
    const val GRANT_FILES = "adb shell appops set com.mmlauncher.tv MANAGE_EXTERNAL_STORAGE allow"
}
