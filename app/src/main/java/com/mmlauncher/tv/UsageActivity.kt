package com.mmlauncher.tv

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

/** Shared layout for the RAM and Storage screens: summary and actions on the left, app list on the right. */
abstract class UsageActivity : Activity() {

    data class Usage(val label: String, val value: String, val fraction: Float, val pkg: String?)

    class Result(
        val summary: String,
        val percent: Int,
        val hint: String?,
        val listTitle: String,
        val items: List<Usage>,
    )

    protected abstract val titleRes: Int
    /** Runs on a background thread. */
    protected abstract fun compute(): Result
    protected abstract fun actions(): List<Pair<Int, () -> Unit>>
    protected abstract fun onItem(item: Usage)

    private val io = Executors.newSingleThreadExecutor()
    private lateinit var summary: TextView
    private lateinit var bar: ProgressBar
    private lateinit var hint: TextView
    private lateinit var listTitle: TextView
    private lateinit var list: RecyclerView
    private val adapter = UsageAdapter()
    private var first = true

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(LocaleHelper.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usage)
        findViewById<TextView>(R.id.u_title).setText(titleRes)
        summary = findViewById(R.id.u_summary)
        bar = findViewById(R.id.u_bar)
        hint = findViewById(R.id.u_hint)
        listTitle = findViewById(R.id.u_list_title)
        list = findViewById<RecyclerView>(R.id.u_list).apply {
            layoutManager = LinearLayoutManager(this@UsageActivity)
            adapter = this@UsageActivity.adapter
            itemAnimator = null
        }
        val row = findViewById<LinearLayout>(R.id.u_actions)
        for ((label, action) in actions()) {
            val v = LayoutInflater.from(this).inflate(R.layout.item_quick, row, false) as TextView
            v.setText(label)
            v.setOnClickListener { action() }
            row.addView(v)
        }
        Fonts.apply(window.decorView)
        row.getChildAt(0)?.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    protected fun refresh() {
        io.execute {
            val r = try { compute() } catch (e: Exception) { null }
            runOnUiThread {
                if (isDestroyed || r == null) return@runOnUiThread
                summary.text = r.summary
                bar.progress = r.percent
                hint.text = r.hint.orEmpty()
                hint.visibility = if (r.hint == null) View.GONE else View.VISIBLE
                listTitle.text = r.listTitle
                adapter.items = r.items
                adapter.notifyDataSetChanged()
                first = false
            }
        }
    }

    private inner class UsageAdapter : RecyclerView.Adapter<UsageAdapter.VH>() {
        var items: List<Usage> = emptyList()
        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_usage, parent, false)
            Fonts.apply(v)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val u = items[position]
            h.name.text = u.label
            h.value.text = u.value
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
