package com.mmlauncher.tv.presentation.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mmlauncher.tv.R
import com.mmlauncher.tv.domain.model.AppInfo

class AppAdapter(
    private val icons: IconLoader,
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo) -> Unit,
    private val onFocused: (AppInfo) -> Unit,
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var items: List<AppInfo> = emptyList()

    fun submit(list: List<AppInfo>) {
        items = list
        notifyDataSetChanged()
    }

    fun indexOf(packageName: String) = items.indexOfFirst { it.packageName == packageName }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        Fonts.apply(v)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        holder.itemView.tag = app // used by the Menu key handler
        holder.label.text = app.label
        holder.icon.tag = app.packageName
        holder.icon.setImageDrawable(null)
        icons.get(app) { d ->
            if (holder.icon.tag == app.packageName) holder.icon.setImageDrawable(d)
        }
        holder.itemView.setOnClickListener { onClick(app) }
        // Holding OK on the remote fires a long click.
        holder.itemView.setOnLongClickListener { onLongClick(app); true }
        holder.itemView.setOnFocusChangeListener { v, has ->
            v.animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f)
                .setDuration(120).start()
            if (has) onFocused(app)
        }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val label: TextView = v.findViewById(R.id.label)
    }
}
