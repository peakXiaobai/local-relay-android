package com.peak.localrelay

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val latestVersionCode: Long,
    val latestVersionName: String,
    val apkUrl: String,
    val changelog: String,
    val forceUpdate: Boolean,
)

object UpdateChecker {
    suspend fun checkForUpdate(
        context: Context,
        updateInfoUrl: String,
    ): AppUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val currentVersionCode = getCurrentVersionCode(context)
            val payload = fetch(updateInfoUrl) ?: return@runCatching null
            val json = JSONObject(payload)

            val latestVersionCode = json.optLong("latestVersionCode", -1L)
            val latestVersionName = json.optString("latestVersionName", latestVersionCode.toString())
            val apkUrl = json.optString("apkUrl", "")
            val changelog = json.optString("changelog", "")
            val forceUpdate = json.optBoolean("forceUpdate", false)

            if (latestVersionCode <= currentVersionCode || apkUrl.isBlank()) {
                null
            } else {
                AppUpdateInfo(
                    latestVersionCode = latestVersionCode,
                    latestVersionName = latestVersionName,
                    apkUrl = apkUrl,
                    changelog = changelog,
                    forceUpdate = forceUpdate,
                )
            }
        }.getOrNull()
    }

    private fun fetch(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("Accept", "application/json")
        }

        return try {
            if (connection.responseCode !in 200..299) {
                null
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun getCurrentVersionCode(context: Context): Long {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return PackageInfoCompat.getLongVersionCode(packageInfo)
    }
}
