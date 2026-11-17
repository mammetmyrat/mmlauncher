package com.mmlauncher.tv.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.mmlauncher.tv.domain.model.AppInfo
import com.mmlauncher.tv.domain.repository.AppsRepository
import java.text.Collator
import java.util.Locale

/** Reads launchable apps: both LEANBACK_LAUNCHER and plain LAUNCHER (many boxes ship TV-less apps). */
class PackageAppsRepository(private val context: Context) : AppsRepository {

    private val pm get() = context.packageManager

    override fun installed(): List<AppInfo> {
        val result = LinkedHashMap<String, AppInfo>()

        fun collect(category: String, leanback: Boolean) {
            val list = try {
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
            } catch (e: Exception) {
                emptyList()
            }
            for (ri in list) {
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName) continue
                if (result.containsKey(pkg) && !leanback) continue // leanback entry wins
                result[pkg] = AppInfo(ri.loadLabel(pm).toString(), pkg, ri.activityInfo.name, leanback)
            }
        }
        collect(Intent.CATEGORY_LAUNCHER, false)
        collect("android.intent.category.LEANBACK_LAUNCHER", true)

        val collator = Collator.getInstance(Locale.getDefault())
        return result.values.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    /** FR-4: leanback intent first, then the regular launch intent. Never throws. */
    override fun launch(app: AppInfo): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: pm.getLaunchIntentForPackage(app.packageName)
            ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun canUninstall(packageName: String): Boolean = try {
        val flags = pm.getApplicationInfo(packageName, 0).flags
        flags and ApplicationInfo.FLAG_SYSTEM == 0 || flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
    } catch (e: Exception) {
        false
    }

    override fun label(packageName: String): String? = try {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: Exception) {
        null
    }
}
