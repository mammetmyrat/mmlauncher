package com.mmlauncher.tv

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File

/** Finds internal storage and mounted removable volumes (USB sticks, SD cards). */
object Storage {

    data class Volume(val label: String, val dir: File)

    fun internal(context: Context): Volume =
        Volume(context.getString(R.string.internal_storage), Environment.getExternalStorageDirectory())

    fun removable(context: Context): List<Volume> {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager ?: return emptyList()
        return try {
            sm.storageVolumes.mapNotNull { v ->
                if (!v.isRemovable || v.state != Environment.MEDIA_MOUNTED) return@mapNotNull null
                val dir = directoryOf(v) ?: return@mapNotNull null
                Volume(v.getDescription(context) ?: context.getString(R.string.src_usb_plain), dir)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun directoryOf(v: StorageVolume): File? {
        if (Build.VERSION.SDK_INT >= 30) return v.directory
        return try { // StorageVolume.getPath() is hidden before API 30
            (StorageVolume::class.java.getMethod("getPath").invoke(v) as? String)?.let { File(it) }
        } catch (e: Throwable) {
            val uuid = v.uuid ?: return null
            File("/storage/$uuid").takeIf { it.exists() }
        }
    }
}
