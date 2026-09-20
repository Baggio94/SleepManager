package com.med.sleepmanager.update

import android.content.Context
import com.med.sleepmanager.BuildConfig
import com.med.sleepmanager.data.AppPreferences
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val releaseUrl: String
)

sealed class UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    data class UpToDate(val latestVersion: String) : UpdateCheckResult()
    data class Error(val cause: Throwable) : UpdateCheckResult()
    object Disabled : UpdateCheckResult()
    object NotDue : UpdateCheckResult()
}

object UpdateChecker {
    private const val RELEASE_API =
        "https://api.github.com/repos/Baggio94/SleepManager/releases/latest"
    private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000

    private val checkLock = Any()
    @Volatile
    private var checkRunning = false

    fun cachedUpdate(context: Context): UpdateInfo? {
        val version = AppPreferences.latestReleaseVersion(context) ?: return null
        val url = AppPreferences.latestReleaseUrl(context) ?: return null
        return if (VersionComparator.isNewer(version, BuildConfig.VERSION_NAME)) {
            UpdateInfo(version, url)
        } else {
            null
        }
    }

    fun simulateAvailableUpdate(
        context: Context,
        versionName: String = "0.5.2"
    ): UpdateInfo {
        val appContext = context.applicationContext
        val update = UpdateInfo(
            versionName = versionName,
            releaseUrl = "https://github.com/Baggio94/SleepManager/releases"
        )
        AppPreferences.setLatestRelease(
            appContext,
            update.versionName,
            update.releaseUrl
        )
        UpdateNotifier.notifyIfNeeded(appContext, update)
        return update
    }

    fun checkIfDueAsync(context: Context, notify: Boolean) {
        val appContext = context.applicationContext
        Thread {
            check(
                context = appContext,
                force = false,
                notify = notify
            )
        }.start()
    }

    fun check(
        context: Context,
        force: Boolean,
        notify: Boolean
    ): UpdateCheckResult {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()

        synchronized(checkLock) {
            if (checkRunning) return UpdateCheckResult.NotDue
            if (!force && !AppPreferences.automaticUpdateChecks(appContext)) {
                return UpdateCheckResult.Disabled
            }

            val lastAttempt = AppPreferences.lastUpdateCheckAttempt(appContext)
            if (!force && now - lastAttempt < CHECK_INTERVAL_MS) {
                return UpdateCheckResult.NotDue
            }

            checkRunning = true
            AppPreferences.setLastUpdateCheckAttempt(appContext, now)
        }

        return try {
            val release = fetchLatestStableRelease()
            AppPreferences.setLatestRelease(
                appContext,
                release.versionName,
                release.releaseUrl
            )

            if (VersionComparator.isNewer(release.versionName, BuildConfig.VERSION_NAME)) {
                if (notify) {
                    UpdateNotifier.notifyIfNeeded(appContext, release)
                }
                UpdateCheckResult.Available(release)
            } else {
                UpdateCheckResult.UpToDate(release.versionName)
            }
        } catch (t: Throwable) {
            UpdateCheckResult.Error(t)
        } finally {
            synchronized(checkLock) {
                checkRunning = false
            }
        }
    }

    private fun fetchLatestStableRelease(): UpdateInfo {
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "SleepManager/${BuildConfig.VERSION_NAME}")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }

        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("GitHub release check failed with HTTP $status")
            }

            val jsonText =
                connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonText)

            if (json.optBoolean("draft") || json.optBoolean("prerelease")) {
                throw IllegalStateException("Latest release is not stable")
            }

            val tag = json.getString("tag_name")
            val version = tag.removePrefix("v")
            val releaseUrl = json.getString("html_url")

            if (
                !releaseUrl.startsWith(
                    "https://github.com/Baggio94/SleepManager/releases/"
                )
            ) {
                throw IllegalStateException("Unexpected release URL")
            }

            return UpdateInfo(
                versionName = version,
                releaseUrl = releaseUrl
            )
        } finally {
            connection.disconnect()
        }
    }
}
