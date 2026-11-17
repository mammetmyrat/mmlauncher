package com.mmlauncher.tv.data.system

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.mmlauncher.tv.R
import org.json.JSONObject
import java.io.File

/**
 * FR-8 / FR-10: each quick-settings button is a chain of intents tried in order.
 * The chains live in JSON: built-in res/raw/settings_chains.json, optionally overridden by
 * filesDir/settings_chains.json (stage 7 will download updates into that file).
 */
object SettingsChains {

    private data class Step(val action: String?, val pkg: String?, val cls: String?)

    private const val OVERRIDE_FILE = "settings_chains.json"
    private var cache: Map<String, List<Step>>? = null

    private fun chains(context: Context): Map<String, List<Step>> {
        cache?.let { return it }
        val override = File(context.filesDir, OVERRIDE_FILE)
        val parsed = (if (override.exists()) runCatching { parse(override.readText()) }.getOrNull() else null)
            ?: parse(context.resources.openRawResource(R.raw.settings_chains).bufferedReader().use { it.readText() })
        cache = parsed
        return parsed
    }

    /** Stage 7: validates a downloaded config and stores it as the override. Returns false if invalid. */
    fun applyOverride(context: Context, json: String): Boolean {
        val parsed = runCatching { parse(json) }.getOrNull()
        if (parsed == null || parsed.isEmpty()) return false
        File(context.filesDir, OVERRIDE_FILE).writeText(json)
        cache = parsed
        return true
    }

    private fun parse(json: String): Map<String, List<Step>> {
        val root = JSONObject(json).getJSONObject("chains")
        val out = HashMap<String, List<Step>>()
        for (key in root.keys()) {
            val arr = root.getJSONArray(key)
            out[key] = (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Step(o.optString("action").ifEmpty { null }, o.optString("pkg").ifEmpty { null }, o.optString("cls").ifEmpty { null })
            }
        }
        return out
    }

    /** Tries each step; returns true if one opened. Never throws. */
    fun open(context: Context, key: String): Boolean {
        for (step in chains(context)[key].orEmpty()) {
            val intent = when {
                step.action != null -> Intent(step.action)
                step.pkg != null && step.cls != null -> Intent().setClassName(step.pkg, step.cls)
                else -> continue
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // not present on this device / not exported: try the next one
            }
        }
        return false
    }

    fun isDeveloperModeOn(context: Context): Boolean = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
    } catch (e: Exception) {
        false
    }
}
