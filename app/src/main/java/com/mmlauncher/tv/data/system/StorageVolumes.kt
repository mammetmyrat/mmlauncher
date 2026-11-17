package com.mmlauncher.tv.data.system

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import com.mmlauncher.tv.R
import com.mmlauncher.tv.domain.model.Volume
import java.io.File

/** Internal storage and mounted removable volumes (USB sticks, SD cards). */
object StorageVolumes {

    fun internal(context: Context) =
        Volume(context.getString(R.string.internal_storage), Environment.getExternalStorageDirectory().path)

    fun removable(context: Context): List<Volume> {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager ?: return emptyList()
        return try {
            sm.storageVolumes.mapNotNull { v ->
                if (!v.isRemovable || v.state != Environment.MEDIA_MOUNTED) return@mapNotNull null
                val dir = directoryOf(v) ?: return@mapNotNull null
                Volume(v.getDescription(context) ?: context.getString(R.string.src_usb_plain), dir.path)
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
