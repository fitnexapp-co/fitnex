package com.fitnex.app.update

import android.content.Context
import android.content.pm.ApplicationInfo
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A newer FITNEX published on GitHub Releases. [downloadUrl] is the APK itself when attached. */
data class AvailableUpdate(val version: String, val downloadUrl: String)

/**
 * "Hay una nueva versión": while FITNEX is installed from an APK (not from Google Play), it looks at
 * the public list of versions on GitHub once per app start. Nothing about the user is sent — it is
 * the same public page anyone can open (GitHub sees the IP address, like any website).
 */
object UpdateChecker {
    private const val LATEST = "https://api.github.com/repos/fitnexapp-co/fitnex/releases/latest"
    private const val PREFS = "fitnex_updates"
    private const val DISMISSED = "dismissed_version"

    /** Debug builds (and tests) never call the network. */
    fun enabled(context: Context): Boolean = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0

    fun installedVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** The newer version to offer, or null (up to date, already dismissed, offline, any error). */
    suspend fun check(context: Context): AvailableUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val json = fetch(LATEST)
            val release = parseRelease(json) ?: return@runCatching null
            val installed = installedVersion(context)
            val dismissed = prefs(context).getString(DISMISSED, null)
            release.takeIf { isNewer(it.version, installed) && it.version != dismissed }
        }.getOrNull()
    }

    /** "Ahora no": this version is not offered again; the next one will be. */
    fun dismiss(context: Context, version: String) {
        prefs(context).edit().putString(DISMISSED, version).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun fetch(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "FITNEX-Android")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Release JSON → version ("v1.7.2" → "1.7.2") and the APK download (or the release page). */
    internal fun parseRelease(json: String): AvailableUpdate? {
        val root = JSONObject(json)
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        val version = root.optString("tag_name").removePrefix("v").removePrefix("V").ifBlank { return null }
        val assets = root.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0))
            .map { assets!!.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            ?.optString("browser_download_url")
        val url = apk?.takeIf { it.isNotBlank() } ?: root.optString("html_url").ifBlank { return null }
        return AvailableUpdate(version, url)
    }

    /** "1.7.10" is newer than "1.7.9"; anything unparseable is never offered. */
    internal fun isNewer(candidate: String, installed: String): Boolean {
        val a = parts(candidate) ?: return false
        val b = parts(installed) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(version: String): List<Int>? =
        version.substringBefore('-').split('.').map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }
}
