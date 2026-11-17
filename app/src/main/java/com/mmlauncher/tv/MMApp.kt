package com.mmlauncher.tv

import android.app.Application
import android.content.Context
import com.mmlauncher.tv.di.AppContainer

class MMApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Convenience for Activities: `appContainer` from any Context. */
val Context.appContainer: AppContainer get() = (applicationContext as MMApp).container
