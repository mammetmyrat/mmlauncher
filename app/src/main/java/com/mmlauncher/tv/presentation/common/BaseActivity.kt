package com.mmlauncher.tv.presentation.common

import android.app.Activity
import android.content.Context
import android.widget.Toast

/** Applies the in-app language to every screen. */
abstract class BaseActivity : Activity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(LocaleHelper.wrap(newBase))

    protected fun toast(msg: Msg) = Toast.makeText(this, msg.text(this), Toast.LENGTH_SHORT).show()
    protected fun toastLong(msg: Msg) = Toast.makeText(this, msg.text(this), Toast.LENGTH_LONG).show()
}
