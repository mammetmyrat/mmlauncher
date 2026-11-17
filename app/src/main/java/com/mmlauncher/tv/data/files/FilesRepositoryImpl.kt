package com.mmlauncher.tv.data.files

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.mmlauncher.tv.data.system.StorageVolumes
import com.mmlauncher.tv.domain.model.FileEntry
import com.mmlauncher.tv.domain.model.Volume
import com.mmlauncher.tv.domain.repository.FilesRepository
import com.mmlauncher.tv.domain.repository.WallpaperRepository
import java.io.File
import java.io.FileOutputStream

/** java.io.File based, read-only browsing. Android 11+ needs "All files access", older needs READ_EXTERNAL_STORAGE. */
class FilesRepositoryImpl(private val context: Context) : FilesRepository {

    override fun roots(): List<Volume> = listOf(StorageVolumes.internal(context)) + StorageVolumes.removable(context)

    override fun list(path: String): List<FileEntry>? =
        File(path).listFiles()?.map { FileEntry(it.name, it.path, it.isDirectory, if (it.isDirectory) 0 else it.length()) }

    override fun hasAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= 30 -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT >= 23 ->
            context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        else -> true
    }

    private fun mime(path: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(File(path).extension.lowercase()) ?: "*/*"

    override fun isImage(path: String) = mime(path).startsWith("image/")

    override fun open(path: String): Boolean = try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(path))
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime(path))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        false
    }
}

/** One user-chosen wallpaper image, downsampled and copied into private storage (no storage permission needed later). */
class WallpaperStorage(private val context: Context) : WallpaperRepository {

    private val file get() = File(context.filesDir, "wallpaper.jpg")

    /** Path the View decodes; null if no custom wallpaper was saved. */
    fun customPath(): String? = file.takeIf { it.exists() }?.path

    override fun saveFromFile(path: String) = saveFromUri(Uri.fromFile(File(path)).toString())

    override fun saveFromUri(uri: String): Boolean = try {
        val u = Uri.parse(uri)
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            false
        } else {
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_W && bounds.outHeight / (sample * 2) >= MAX_H) sample *= 2
            val opts = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            val bmp = context.contentResolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
            if (bmp == null) false else {
                FileOutputStream(file).use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
                bmp.recycle()
                true
            }
        }
    } catch (e: Throwable) {
        false
    }

    private companion object { const val MAX_W = 1920; const val MAX_H = 1080 }
}
