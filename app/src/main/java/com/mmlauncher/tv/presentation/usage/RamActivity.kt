package com.mmlauncher.tv.presentation.usage

import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import com.mmlauncher.tv.R
import com.mmlauncher.tv.appContainer
import com.mmlauncher.tv.presentation.common.BaseActivity

/** Total / used / available RAM and, when the box's adbd answers, how much each app uses. */
class RamActivity : BaseActivity() {

    private lateinit var store: RamStore
    private lateinit var view: UsageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usage)
        store = appContainer.ramStore()
        view = UsageView(
            this, R.string.q_ram,
            listOf(
                R.string.ram_free_btn to { store.dispatch(UsageIntent.Boost) },
                R.string.refresh to { store.dispatch(UsageIntent.Refresh) },
            ),
        ) { store.dispatch(UsageIntent.ItemClicked(it)) }
    }

    override fun onStart() {
        super.onStart()
        store.bind(::render, ::onEffect)
    }

    override fun onResume() {
        super.onResume()
        store.dispatch(UsageIntent.Refresh)
    }

    override fun onStop() {
        super.onStop()
        store.unbind()
    }

    override fun onDestroy() {
        super.onDestroy()
        store.clear()
    }

    private fun render(s: RamScreenState) {
        val ram = s.ram ?: return
        val mb = { v: Int -> Formatter.formatFileSize(this, v * 1024L * 1024L) }
        val summary = getString(R.string.ram_total, mb(ram.totalMb)) + "\n" +
            getString(R.string.ram_used, mb(ram.usedMb), ram.usedPercent) + "\n" +
            getString(R.string.ram_free, mb(ram.availMb))
        view.show(summary, ram.usedPercent, view.hintText(s.hint, R.string.ram_tap_stop, R.string.ram_need_dump), getString(R.string.ram_apps), s.items)
    }

    private fun onEffect(effect: UsageEffect) {
        when (effect) { is UsageEffect.Toast -> Toast.makeText(this, effect.msg.text(this), Toast.LENGTH_SHORT).show() }
    }
}
