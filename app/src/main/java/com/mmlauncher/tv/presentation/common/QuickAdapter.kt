package com.mmlauncher.tv.presentation.common

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mmlauncher.tv.R

data class QuickItem(val key: String, val label: String)

class QuickAdapter(
    private val items: List<QuickItem>,
    private val onClick: (QuickItem) -> Unit,
) : RecyclerView.Adapter<QuickAdapter.VH>() {

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_quick, parent, false) as TextView
        Fonts.apply(v)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.text.text = item.label
        holder.text.setOnClickListener { onClick(item) }
        holder.text.setOnFocusChangeListener { v, has ->
            v.animate().scaleX(if (has) 1.06f else 1f).scaleY(if (has) 1.06f else 1f).setDuration(120).start()
        }
    }

    class VH(val text: TextView) : RecyclerView.ViewHolder(text)
}
