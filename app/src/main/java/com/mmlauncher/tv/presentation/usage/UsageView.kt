package com.mmlauncher.tv.presentation.usage

import android.app.Activity
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mmlauncher.tv.R
import com.mmlauncher.tv.presentation.common.Fonts
import com.mmlauncher.tv.presentation.common.Hints

/** The shared layout of the RAM and Storage screens: summary and actions left, usage list right. */
class UsageView(private val activity: Activity, titleRes: Int, actions: List<Pair<Int, () -> Unit>>, onItem: (UsageItem) -> Unit) {

    private val summary: TextView = activity.findViewById(R.id.u_summary)
    private val bar: ProgressBar = activity.findViewById(R.id.u_bar)
    private val hint: TextView = activity.findViewById(R.id.u_hint)
    private val listTitle: TextView = activity.findViewById(R.id.u_list_title)
    private val adapter = Adapter(onItem)
    private var shownItems: List<UsageItem>? = null

    init {
        activity.findViewById<TextView>(R.id.u_title).setText(titleRes)
        activity.findViewById<RecyclerView>(R.id.u_list).apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = this@UsageView.adapter
            itemAnimator = null
        }
        val row = activity.findViewById<LinearLayout>(R.id.u_actions)
        for ((label, action) in actions) {
            val v = LayoutInflater.from(activity).inflate(R.layout.item_quick, row, false) as TextView
            v.setText(label)
            v.setOnClickListener { action() }
            row.addView(v)
        }
        Fonts.apply(activity.window.decorView)
        row.getChildAt(0)?.requestFocus()
    }

    fun show(summaryText: String, percent: Int, hintText: String?, listTitleText: String, items: List<UsageItem>) {
        summary.text = summaryText
        bar.progress = percent
        hint.text = hintText.orEmpty()
        hint.visibility = if (hintText == null) View.GONE else View.VISIBLE
        listTitle.text = listTitleText
        if (items != shownItems) { shownItems = items; adapter.items = items; adapter.notifyDataSetChanged() }
    }

    /** Sentence for a [UsageHint]; the two screens differ only in the "need ADB" wording. */
    fun hintText(hint: UsageHint, tapText: Int, needAdbText: Int): String? = when (hint) {
        UsageHint.NONE -> null
        UsageHint.TAP_TO_STOP, UsageHint.TAP_FOR_STORAGE -> activity.getString(tapText)
        UsageHint.NEED_ADB -> activity.getString(needAdbText, Hints.ENABLE_TCPIP)
        UsageHint.WAITING_FOR_APPROVAL -> activity.getString(R.string.adb_denied)
        UsageHint.UNEXPECTED -> activity.getString(R.string.adb_failed)
        UsageHint.NO_DATA -> activity.getString(R.string.adb_nodata)
    }

    private inner class Adapter(private val onItem: (UsageItem) -> Unit) : RecyclerView.Adapter<Adapter.VH>() {
        var items: List<UsageItem> = emptyList()
        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_usage, parent, false)
            Fonts.apply(v)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val u = items[position]
            h.name.text = if (u.isSelf) activity.getString(R.string.ram_own) else u.label
            h.value.text = Formatter.formatShortFileSize(activity, u.bytes)
            h.bar.progress = (u.fraction * 100).toInt().coerceIn(0, 100)
            h.itemView.setOnClickListener { onItem(u) }
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.name)
            val value: TextView = v.findViewById(R.id.value)
            val bar: ProgressBar = v.findViewById(R.id.bar)
        }
    }
}
