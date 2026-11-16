package com.mmlauncher.tv

import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat

/**
 * FR-30: we ship our own font so Turkmen letters (ä ç ž ň ö ş ü ý) render even on boxes with
 * stripped system fonts. API 26+ gets it from the theme (android:fontFamily); on API 24-25 we
 * apply the typeface manually.
 */
object Fonts {

    private var typeface: Typeface? = null

    fun apply(view: View) {
        if (Build.VERSION.SDK_INT >= 26) return
        val tf = typeface ?: ResourcesCompat.getFont(view.context, R.font.roboto_static)?.also { typeface = it } ?: return
        walk(view, tf)
    }

    private fun walk(v: View, tf: Typeface) {
        if (v is TextView) v.setTypeface(tf, v.typeface?.style ?: Typeface.NORMAL)
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), tf)
    }
}
