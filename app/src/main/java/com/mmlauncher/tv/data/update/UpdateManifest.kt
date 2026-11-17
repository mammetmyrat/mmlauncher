package com.mmlauncher.tv.data.update

import com.mmlauncher.tv.domain.model.UpdateInfo
import org.json.JSONObject
import java.util.Locale

/**
 * update.json:
 * { "versionCode": 2, "versionName": "0.2.0", "apkUrl": "https://.../mm-launcher-0.2.0.apk",
 *   "sha256": "<hex>", "notes": {"en": "...", "ru": "...", "tk": "..."},
 *   "chains": {"version": 3, "url": "https://.../settings_chains.json"} }
 */
object UpdateManifest {

    data class Chains(val version: Int, val url: String)

    /** Returns the update if it is newer than [currentVersionCode]; null otherwise or if unusable. */
    fun parseUpdate(root: JSONObject, currentVersionCode: Int, language: String): UpdateInfo? {
        val code = root.optInt("versionCode", 0)
        if (code <= currentVersionCode) return null
        val apk = root.optString("apkUrl")
        if (apk.isEmpty()) return null
        return UpdateInfo(
            versionCode = code,
            versionName = root.optString("versionName"),
            apkUrl = apk,
            sha256 = root.optString("sha256").lowercase(Locale.ROOT),
            notes = pickNotes(root.optJSONObject("notes"), language),
        )
    }

    fun parseChains(root: JSONObject): Chains? =
        root.optJSONObject("chains")?.let { Chains(it.optInt("version", 0), it.optString("url")) }

    fun pickNotes(notes: JSONObject?, language: String): String {
        if (notes == null) return ""
        return notes.optString(language).ifEmpty { notes.optString("en") }
    }
}
