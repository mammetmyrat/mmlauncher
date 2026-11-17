package com.mmlauncher.tv.presentation.common

import android.view.InputDevice
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView

/**
 * FR-32: mouse / air-mouse. Android scrolls a horizontal list only for a horizontal wheel axis,
 * but ordinary mice and air-mouse remotes only have a vertical wheel, so we map it to the row.
 * Hover highlight comes from state_hovered in card_bg.xml; clicks work as plain touch clicks.
 */
object Mouse {

    fun enableWheel(row: RecyclerView) {
        val step = (row.resources.displayMetrics.density * 90).toInt()
        row.setOnGenericMotionListener { _, e ->
            if (e.action == MotionEvent.ACTION_SCROLL && e.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) {
                val delta = e.getAxisValue(MotionEvent.AXIS_HSCROLL) - e.getAxisValue(MotionEvent.AXIS_VSCROLL)
                if (delta != 0f) {
                    row.scrollBy((delta * step).toInt(), 0)
                    return@setOnGenericMotionListener true
                }
            }
            false
        }
    }
}
