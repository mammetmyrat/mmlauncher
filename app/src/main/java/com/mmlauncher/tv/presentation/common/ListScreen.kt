package com.mmlauncher.tv.presentation.common

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mmlauncher.tv.R

/** One row of a list screen. [tag] comes back in the click callback. */
data class InfoItem(
    val kind: Kind,
    val label: String,
    val value: String = "",
    val tag: Any? = null,
    val color: Int? = null,
) {
    enum class Kind { HEADER, ROW, LOG }
}

class Chip(val label: String, val selected: Boolean = false, val onClick: () -> Unit)

/** Shared "title, action chips, hint, list" layout used by the Tools screens. */
class ListScreen(private val activity: Activity, titleRes: Int) {

    private val actions: LinearLayout = activity.findViewById(R.id.l_actions)
    private val hint: TextView = activity.findViewById(R.id.l_hint)
    private val list: RecyclerView = activity.findViewById(R.id.l_list)
    private val adapter = Adapter()
    private var shownItems: List<InfoItem>? = null
    private var shownChips: List<String>? = null

    init {
        activity.findViewById<TextView>(R.id.l_title).setText(titleRes)
        list.layoutManager = LinearLayoutManager(activity)
        list.adapter = adapter
        list.itemAnimator = null
        Fonts.apply(activity.window.decorView)
    }

    /** Rebuilds the chips only when they changed, keeping the focused position. */
    fun setChips(chips: List<Chip>) {
        val key = chips.map { "${it.label}|${it.selected}" }
        if (key == shownChips) return
        shownChips = key
        val focusedIdx = actions.indexOfChild(actions.focusedChild)
        actions.removeAllViews()
        for (c in chips) {
            val v = LayoutInflater.from(activity).inflate(R.layout.item_quick, actions, false) as TextView
            v.text = c.label
            v.isSelected = c.selected
            v.setOnClickListener { c.onClick() }
            Fonts.apply(v)
            actions.addView(v)
        }
        val idx = if (focusedIdx >= 0) focusedIdx.coerceAtMost(actions.childCount - 1) else 0
        if (shownItems == null || focusedIdx >= 0) actions.getChildAt(idx)?.requestFocus()
    }

    /**
     * BACK from somewhere inside a long list first returns to the chips row (so the level filter is
     * reachable again), and only a second BACK leaves the screen. Returns true if it moved focus.
     */
    fun onBack(): Boolean {
        if (!list.hasFocus()) return false
        actions.getChildAt(0)?.requestFocus() ?: return false
        return true
    }

    fun scrollToTop() { list.scrollToPosition(0); focusFirstRow(0) }

    fun scrollToEnd() {
        val n = adapter.itemCount
        if (n > 0) { list.scrollToPosition(n - 1); focusFirstRow(n - 1) }
    }

    private fun focusFirstRow(position: Int) {
        list.post { list.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus() }
    }

    fun setHint(text: String?) {
        hint.text = text.orEmpty()
        hint.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    fun setItems(items: List<InfoItem>, scrollToEnd: Boolean = false, onClick: ((InfoItem) -> Unit)? = null) {
        adapter.onClick = onClick
        if (items == shownItems) return
        shownItems = items
        adapter.items = items
        adapter.notifyDataSetChanged()
        if (scrollToEnd && items.isNotEmpty()) list.scrollToPosition(items.size - 1)
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        var items: List<InfoItem> = emptyList()
        var onClick: ((InfoItem) -> Unit)? = null

        override fun getItemCount() = items.size
        override fun getItemViewType(position: Int) = items[position].kind.ordinal

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val layout = when (InfoItem.Kind.values()[viewType]) {
                InfoItem.Kind.HEADER -> R.layout.item_info_header
                InfoItem.Kind.ROW -> R.layout.item_info
                InfoItem.Kind.LOG -> R.layout.item_log
            }
            val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
            Fonts.apply(v)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val item = items[position]
            h.label?.text = item.label
            h.value?.text = item.value
            item.color?.let { h.label?.setTextColor(it) }
            h.itemView.setOnClickListener { onClick?.invoke(item) }
            if (item.kind == InfoItem.Kind.HEADER) h.itemView.isFocusable = false
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val label: TextView? = (v as? TextView)?.takeIf { v.id == R.id.label || v.id == View.NO_ID } ?: v.findViewById(R.id.label)
            val value: TextView? = v.findViewById(R.id.value)
        }
    }
}
