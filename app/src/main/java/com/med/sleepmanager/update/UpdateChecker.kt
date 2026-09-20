package com.med.sleepmanager.update

import android.content.Context
import com.med.sleepmanager.BuildConfig
import com.med.sleepmanager.data.AppPreferences
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val versionCode: Long? = null,
    val releaseUrl: String,
    val apkUrl: String? = null,
    val sha256: String? = null
) {
    val directInstallAvailable: Boolean
        get() = apkUrl != null && sha256 != null
}

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
    private const val RELEASE_MANIFEST =
        "https://github.com/Baggio94/SleepManager/releases/latest/download/update.json"
    private const val UPDATER_V2_TEST_MANIFEST =
        "https://github.com/Baggio94/SleepManager/releases/download/v0.5.2-updater-test/update.json"
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
            UpdateInfo(
                versionName = version,
                versionCode = AppPreferences.latestReleaseVersionCode(context),
                releaseUrl = url,
                apkUrl = AppPreferences.latestReleaseApkUrl(context),
                sha256 = AppPreferences.latestReleaseSha256(context)
            )
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
        cacheRelease(appContext, update)
        UpdateNotifier.notifyIfNeeded(appContext, update)
        return update
    }

    fun loadUpdaterV2Test(context: Context): UpdateInfo {
        val appContext = context.applicationContext
        val update = fetchReleaseManifest(UPDATER_V2_TEST_MANIFEST)
        cacheRelease(appContext, update)
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
            cacheRelease(appContext, release)

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

    private fun cacheRelease(context: Context, release: UpdateInfo) {
        AppPreferences.setLatestRelease(
            context = context,
            version = release.versionName,
            versionCode = release.versionCode,
            url = release.releaseUrl,
            apkUrl = release.apkUrl,
            sha256 = release.sha256
        )
    }

    private fun fetchLatestStableRelease(): UpdateInfo =
        runCatching { fetchReleaseManifest(RELEASE_MANIFEST) }
            .getOrElse { fetchLatestStableReleaseFromApi() }

    private fun fetchReleaseManifest(url: String): UpdateInfo {
        val connection = openJsonConnection(url)
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException(
                    "Release manifest check failed with HTTP $status"
                )
            }

            val jsonText =
                connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonText)

            val versionName = json.getString("versionName")
            val versionCode = json.getLong("versionCode")
            val releaseUrl = json.getString("releaseUrl")
            val apkUrl = json.getString("apkUrl")
            val sha256 = normalizeSha256(json.getString("sha256"))

            validateReleaseUrl(releaseUrl)
            validateApkUrl(apkUrl)
            require(versionCode > 0L) { "Invalid release version code" }
            require(sha256.length == 64) { "Invalid release SHA-256" }

            return UpdateInfo(
                versionName = versionName,
                versionCode = versionCode,
                releaseUrl = releaseUrl,
                apkUrl = apkUrl,
                sha256 = sha256
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchLatestStableReleaseFromApi(): UpdateInfo {
        val connection = openJsonConnection(RELEASE_API)
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException(
                    "GitHub release check failed with HTTP $status"
                )
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
            validateReleaseUrl(releaseUrl)

            var apkUrl: String? = null
            var sha256: String? = null
            val expectedApkName = "SleepManager-$version.apk"
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    if (asset.optString("name") != expectedApkName) continue

                    val candidateUrl = asset.optString("browser_download_url")
                    runCatching { validateApkUrl(candidateUrl) }
                        .onSuccess { apkUrl = candidateUrl }

                    val digest = asset.optString("digest")
                    if (digest.startsWith("sha256:", ignoreCase = true)) {
                        val candidateSha =
                            normalizeSha256(digest.substringAfter(':'))
                        if (candidateSha.length == 64) {
                            sha256 = candidateSha
                        }
                    }
                    break
                }
            }

            return UpdateInfo(
                versionName = version,
                releaseUrl = releaseUrl,
                apkUrl = apkUrl,
                sha256 = sha256
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun openJsonConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "SleepManager/${BuildConfig.VERSION_NAME}")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }

    private fun validateReleaseUrl(url: String) {
        require(
            url.startsWith(
                "https://github.com/Baggio94/SleepManager/releases/"
            )
        ) { "Unexpected release URL" }
    }

    private fun validateApkUrl(url: String) {
        require(
            url.startsWith(
                "https://github.com/Baggio94/SleepManager/releases/download/"
            )
        ) { "Unexpected APK URL" }
    }

    private fun normalizeSha256(value: String): String =
        value
            .lowercase()
            .replace(":", "")
            .filter { it in '0'..'9' || it in 'a'..'f' }
}
