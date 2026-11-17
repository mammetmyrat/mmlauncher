package com.mmlauncher.tv.presentation.common

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.mmlauncher.tv.domain.model.AppInfo
import java.util.concurrent.Executors

/** Tiny async icon loader with a small cache — keeps RAM low on 1 GB boxes. */
class IconLoader(private val pm: PackageManager) {

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun get(app: AppInfo, onLoaded: (Drawable?) -> Unit) {
        cache.get(app.packageName)?.let { onLoaded(it); return }
        executor.execute {
            val icon = try {
                pm.getApplicationIcon(app.packageName)
            } catch (e: Exception) {
                null
            }
            if (icon != null) cache.put(app.packageName, icon)
            main.post { onLoaded(icon) }
        }
    }

    fun clear() = clearAll()

    companion object {
        private val cache = LruCache<String, Drawable>(48)
        /** FR-22: drop every cached icon. */
        fun clearAll() = cache.evictAll()
    }
}
