package com.med.sleepmanager.integration

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.integration.connector.SyncthingConnector
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

object SyncthingController {
    const val PACKAGE_CURRENT = "com.github.catfriend1.syncthingfork"
    const val PACKAGE_CURRENT_DEBUG = "com.github.catfriend1.syncthingfork.debug"
    const val PACKAGE_LEGACY = "com.github.catfriend1.syncthingandroid"
    const val PACKAGE_LEGACY_DEBUG = "com.github.catfriend1.syncthingandroid.debug"

    private const val DEFAULT_GUI_HOST = "127.0.0.1"
    private const val DEFAULT_GUI_PORT = 8384
    private const val HEALTH_TIMEOUT_MS = 500

    enum class RuntimeState {
        RUNNING,
        STOPPED,
        UNKNOWN
    }

    private val supported = listOf(
        PACKAGE_CURRENT,
        PACKAGE_CURRENT_DEBUG,
        PACKAGE_LEGACY,
        PACKAGE_LEGACY_DEBUG
    )

    data class Target(val packageName: String, val displayName: String)

    fun installedTargets(context: Context): List<Target> =
        supported
            .filter { isInstalled(context, it) }
            .map { Target(it, displayName(context, it)) }

    fun selectedTarget(context: Context): Target? {
        val installed = installedTargets(context)
        if (installed.isEmpty()) return null

        val preferred = AppPreferences.getSelectedSyncthing(context)
        val target =
            installed.firstOrNull { it.packageName == preferred }
                ?: installed.first()

        if (target.packageName != preferred) {
            AppPreferences.setSelectedSyncthing(
                context,
                target.packageName
            )
        }

        return target
    }

    fun select(context: Context, packageName: String) {
        if (
            supported.contains(packageName) &&
            isInstalled(context, packageName)
        ) {
            AppPreferences.setSelectedSyncthing(context, packageName)
        }
    }

    fun runtimeState(context: Context): RuntimeState {
        if (selectedTarget(context) == null) {
            return RuntimeState.UNKNOWN
        }

        if (
            SleepCycleStore.hasConnectorChange(
                context,
                SyncthingConnector.id
            )
        ) {
            return RuntimeState.STOPPED
        }

        return if (healthCheckDefaultGui()) {
            RuntimeState.RUNNING
        } else {
            RuntimeState.UNKNOWN
        }
    }

    fun sendStop(context: Context) =
        send(
            context,
            selectedTarget(context)?.packageName,
            ".action.STOP",
            "STOP"
        )

    fun sendStopTo(context: Context, packageName: String) =
        send(context, packageName, ".action.STOP", "STOP")

    fun sendFollow(context: Context) =
        send(
            context,
            selectedTarget(context)?.packageName,
            ".action.FOLLOW",
            "FOLLOW"
        )

    fun sendFollowTo(context: Context, packageName: String) =
        send(context, packageName, ".action.FOLLOW", "FOLLOW")

    fun open(context: Context): Boolean {
        val target = selectedTarget(context) ?: return false
        val launch =
            context.packageManager
                .getLaunchIntentForPackage(target.packageName)
                ?: return false

        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return true
    }

    private fun healthCheckDefaultGui(): Boolean =
        runCatching {
            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(
                        DEFAULT_GUI_HOST,
                        DEFAULT_GUI_PORT
                    ),
                    HEALTH_TIMEOUT_MS
                )
                socket.soTimeout = HEALTH_TIMEOUT_MS

                BufferedWriter(
                    OutputStreamWriter(socket.getOutputStream())
                ).use { writer ->
                    writer.write(
                        "GET /rest/noauth/health HTTP/1.1\r\n" +
                            "Host: $DEFAULT_GUI_HOST:$DEFAULT_GUI_PORT\r\n" +
                            "Connection: close\r\n\r\n"
                    )
                    writer.flush()

                    BufferedReader(
                        InputStreamReader(socket.getInputStream())
                    ).use { reader ->
                        val statusLine = reader.readLine()
                            ?: return@runCatching false
                        statusLine.contains(" 200 ")
                    }
                }
            }
        }.getOrDefault(false)

    private fun send(
        context: Context,
        packageName: String?,
        suffix: String,
        label: String
    ): Boolean {
        if (
            packageName == null ||
            !isInstalled(context, packageName)
        ) {
            return false
        }

        return try {
            context.sendBroadcast(
                Intent(packageName + suffix)
                    .setPackage(packageName)
            )
            Log.i(
                "SleepManager",
                "Sent Syncthing $label to $packageName"
            )
            true
        } catch (t: Throwable) {
            Log.e(
                "SleepManager",
                "Unable to send Syncthing $label to $packageName",
                t
            )
            false
        }
    }

    private fun isInstalled(
        context: Context,
        packageName: String
    ): Boolean =
        try {
            context.packageManager.getPackageInfo(
                packageName,
                0
            )
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private fun displayName(
        context: Context,
        packageName: String
    ): String {
        val base = when (packageName) {
            PACKAGE_CURRENT -> "Syncthing-Fork"
            PACKAGE_CURRENT_DEBUG ->
                "Syncthing-Fork Debug / Root"
            PACKAGE_LEGACY ->
                "Syncthing-Fork Legacy"
            PACKAGE_LEGACY_DEBUG ->
                "Syncthing-Fork Legacy Debug"
            else -> "Syncthing-Fork"
        }

        return try {
            val version =
                context.packageManager
                    .getPackageInfo(packageName, 0)
                    .versionName

            if (version.isNullOrBlank()) {
                base
            } else {
                "$base • $version"
            }
        } catch (_: PackageManager.NameNotFoundException) {
            base
        }
    }
}
