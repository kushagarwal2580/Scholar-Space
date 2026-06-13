package com.example.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    data class UpdateInfo(
        val isUpdateAvailable: Boolean,
        val latestVersion: String,
        val downloadUrl: String,
        val releaseNotes: String
    )

    suspend fun checkForUpdates(
        githubOwner: String,
        githubRepo: String,
        currentVersion: String
    ): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$githubOwner/$githubRepo/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                
                val tagName = json.getString("tag_name")
                val releaseNotes = json.optString("body", "Bug fixes and improvements.")
                
                val latestVersionRaw = tagName.removePrefix("v").trim()
                val currentVersionRaw = currentVersion.removePrefix("v").trim()
                
                Log.d("UpdateChecker", "Latest version on GitHub: $latestVersionRaw, Current: $currentVersionRaw")
                
                var downloadUrl = "https://github.com/$githubOwner/$githubRepo/releases/latest"
                
                // Find the .apk file in assets if available to download directly
                val assets = json.optJSONArray("assets")
                if (assets != null && assets.length() > 0) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.getString("browser_download_url")
                            break
                        }
                    }
                }
                
                // Compare versions. Very basic string check for simplification
                // In production, split by dot and parse integers (e.g., 1.0.1 > 1.0.0)
                val isUpdateAvailable = isVersionHigher(latestVersionRaw, currentVersionRaw)
                
                return@withContext UpdateInfo(
                    isUpdateAvailable = isUpdateAvailable,
                    latestVersion = latestVersionRaw,
                    downloadUrl = downloadUrl,
                    releaseNotes = releaseNotes
                )
            } else {
                Log.e("UpdateChecker", "GitHub API returned ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e("UpdateChecker", "Error checking for updates", e)
        }
        return@withContext null
    }

    private fun isVersionHigher(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val length = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until length) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
        } catch (e: Exception) {
            // fallback
            return latest != current
        }
        return false // identical or lower
    }
}
