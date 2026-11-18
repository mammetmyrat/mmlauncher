package com.mmlauncher.tv.presentation.common

import android.app.Activity
import android.app.role.RoleManager
import android.os.Build

/**
 * Asks Android to make this launcher the default home app through the system role dialog (Android 10+).
 * Unlike "Always" in the home picker, a role holder normally survives reboots. Returns false when the
 * firmware has no such dialog (many TV builds), so the caller can offer another way.
 */
object HomeRole {
    fun request(activity: Activity, requestCode: Int): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return try {
            val rm = activity.getSystemService(RoleManager::class.java) ?: return false
            if (!rm.isRoleAvailable(RoleManager.ROLE_HOME)) return false
            if (rm.isRoleHeld(RoleManager.ROLE_HOME)) return false
            activity.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), requestCode)
            true
        } catch (e: Exception) {
            false
        }
    }
}
