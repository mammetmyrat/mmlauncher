package com.mmlauncher.tv

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.text.Collator
import java.util.Locale

/** Reads launchable apps: both LEANBACK_LAUNCHER and plain LAUNCHER (many boxes ship TV-less apps). */
object AppRepository {

    fun load(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val result = LinkedHashMap<String, AppInfo>()

        fun collect(category: String, leanback: Boolean) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            val list = try {
                pm.queryIntentActivities(intent, 0)
            } catch (e: Exception) {
                emptyList()
            }
            for (ri in list) {
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName) continue
                // Leanback entry wins over the plain launcher entry for the same package.
                if (result.containsKey(pkg) && !leanback) continue
                result[pkg] = AppInfo(
                    label = ri.loadLabel(pm).toString(),
                    packageName = pkg,
                    activityName = ri.activityInfo.name,
                    isLeanback = leanback,
                )
            }
        }

        collect(Intent.CATEGORY_LAUNCHER, false)
        collect("android.intent.category.LEANBACK_LAUNCHER", true)

        val collator = Collator.getInstance(Locale.getDefault())
        return result.values.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    /** FR-4: leanback intent first, then regular launch intent. Never throws. */
    fun launch(context: Context, app: AppInfo): Boolean {
        val pm: PackageManager = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: pm.getLaunchIntentForPackage(app.packageName)
            ?: return false
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
