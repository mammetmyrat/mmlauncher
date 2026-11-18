package com.mmlauncher.tv.data.tools

import android.content.Context
import android.os.Environment
import com.mmlauncher.tv.data.diagnostics.LocalAdb
import com.mmlauncher.tv.data.diagnostics.failure
import com.mmlauncher.tv.domain.model.LogDump
import com.mmlauncher.tv.domain.repository.LogsRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The system log through the box's adbd; without it, only this launcher's own log (Android hides the rest). */
class LogsRepositoryImpl(private val context: Context) : LogsRepository {

    override fun read(maxLines: Int): LogDump {
        val viaAdb = LocalAdb.shell(context, "logcat -d -v time -t $maxLines")
        if (viaAdb != null) return LogDump(viaAdb.lines().filter { it.isNotBlank() }, true, null)
        val failure = LocalAdb.failure()
        return LogDump(ownLog(maxLines), false, failure)
    }

    private fun ownLog(maxLines: Int): List<String> = try {
        val p = ProcessBuilder("logcat", "-d", "-v", "time", "-t", maxLines.toString()).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().use { it.readText() }
        p.destroy()
        text.lines().filter { it.isNotBlank() }
    } catch (e: Exception) {
        emptyList()
    }

    override fun save(lines: List<String>): String? {
        val name = "mmlauncher-log-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        val targets = listOfNotNull(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name),
            context.getExternalFilesDir(null)?.let { File(it, name) },
        )
        return targets.firstOrNull { f ->
            try { f.parentFile?.mkdirs(); f.writeText(lines.joinToString("\n")); true } catch (e: Exception) { false }
        }?.path
    }

    override fun clear(): Boolean = LocalAdb.shell(context, "logcat -c") != null

}
