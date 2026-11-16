package com.mmlauncher.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/** Built-in gradient wallpapers (tiny, no copyright issues) and one user-chosen image. */
object Wallpapers {

    /** key -> drawable resource */
    val builtIn = linkedMapOf(
        "night" to R.drawable.wall_night,
        "ocean" to R.drawable.wall_ocean,
        "violet" to R.drawable.wall_violet,
        "sunset" to R.drawable.wall_sunset,
    )

    private const val MAX_W = 1920
    private const val MAX_H = 1080

    private fun customFile(context: Context) = File(context.filesDir, "wallpaper.jpg")

    /** Decodes the saved custom wallpaper at screen size, RGB_565 to keep RAM low. Call off the UI thread. */
    fun loadCustom(context: Context): Bitmap? {
        val f = customFile(context)
        if (!f.exists()) return null
        return try {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(f.path, opts)
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Reads an image from [uri], downsamples to at most 1920x1080 and stores it in the app's
     * private storage, so no storage permission is needed afterwards. Call off the UI thread.
     */
    fun saveCustom(context: Context, uri: Uri): Boolean = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            false
        } else {
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_W && bounds.outHeight / (sample * 2) >= MAX_H) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            if (bmp == null) {
                false
            } else {
                FileOutputStream(customFile(context)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                bmp.recycle()
                true
            }
        }
    } catch (e: Throwable) {
        false
    }
}
