package com.mmlauncher.tv.presentation.usage

import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import com.mmlauncher.tv.R
import com.mmlauncher.tv.appContainer
import com.mmlauncher.tv.domain.model.StorageSpace
import com.mmlauncher.tv.domain.model.SystemScreen
import com.mmlauncher.tv.presentation.common.BaseActivity

/** Total / used / free storage (ROM) and the size of every app (data and cache too when adbd answers). */
class StorageActivity : BaseActivity() {

    private lateinit var store: StorageStore
    private lateinit var view: UsageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usage)
        store = appContainer.storageStore()
        view = UsageView(
            this, R.string.q_storage,
            listOf(
                R.string.refresh to { store.dispatch(UsageIntent.Refresh) },
                R.string.storage_settings to { store.dispatch(UsageIntent.OpenSystem(SystemScreen.STORAGE)) },
                R.string.apps_settings to { store.dispatch(UsageIntent.OpenSystem(SystemScreen.APPS)) },
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

    private fun line(s: StorageSpace) = getString(
        R.string.st_line, s.label,
        Formatter.formatFileSize(this, s.usedBytes), Formatter.formatFileSize(this, s.totalBytes), s.usedPercent,
        Formatter.formatFileSize(this, s.freeBytes),
    )

    private fun render(s: StorageScreenState) {
        if (s.loading) return
        val percent = s.spaces.firstOrNull()?.usedPercent ?: 0
        view.show(
            s.spaces.joinToString("\n\n") { line(it) }, percent,
            view.hintText(s.hint, R.string.st_tap, R.string.st_need_dump), getString(R.string.st_apps), s.items,
        )
    }

    private fun onEffect(effect: UsageEffect) {
        when (effect) { is UsageEffect.Toast -> Toast.makeText(this, effect.msg.text(this), Toast.LENGTH_SHORT).show() }
    }
}
