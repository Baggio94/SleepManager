package com.med.sleepmanager.integration

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.med.sleepmanager.protocol.HelperProtocol

data class HelperWifiControlAccess(
    val permissionGranted: Boolean,
    val appOpMode: Int?,
    val appOpModeName: String,
    val effectivelyAllowed: Boolean?
)

object HelperController {
    @JvmField val PACKAGE = HelperProtocol.HELPER_PACKAGE
    @JvmField val PERMISSION = HelperProtocol.PERMISSION

    private val ACTION_SLEEP = HelperProtocol.ACTION_SLEEP
    private val ACTION_WAKE = HelperProtocol.ACTION_WAKE
    private val ACTION_RESTORE = HelperProtocol.ACTION_RESTORE
    private val ACTION_QUERY = HelperProtocol.ACTION_QUERY
    private val ACTION_FORGET_STATE = HelperProtocol.ACTION_FORGET_STATE
    private val ACTION_SET_TEMP_WIFI = HelperProtocol.ACTION_SET_TEMP_WIFI
    private val HELPER_RECEIVER =
        ComponentName(PACKAGE, HelperProtocol.HELPER_RECEIVER_CLASS)
    @JvmField val ACTION_STATE = HelperProtocol.ACTION_STATE
    @JvmField val ACTION_RESULT = HelperProtocol.ACTION_RESULT

    private val EXTRA_WIFI = HelperProtocol.EXTRA_WIFI
    private val EXTRA_BLUETOOTH = HelperProtocol.EXTRA_BLUETOOTH
    @JvmField val EXTRA_CYCLE_ID = HelperProtocol.EXTRA_CYCLE_ID
    @JvmField val EXTRA_WIFI_STATE = HelperProtocol.EXTRA_WIFI_STATE
    @JvmField val EXTRA_BLUETOOTH_STATE = HelperProtocol.EXTRA_BLUETOOTH_STATE
    @JvmField val EXTRA_PHASE = HelperProtocol.EXTRA_PHASE
    @JvmField val EXTRA_WIFI_MANAGED = HelperProtocol.EXTRA_WIFI_MANAGED
    @JvmField val EXTRA_WIFI_PREVIOUS = HelperProtocol.EXTRA_WIFI_PREVIOUS
    @JvmField val EXTRA_WIFI_CHANGED = HelperProtocol.EXTRA_WIFI_CHANGED
    @JvmField val EXTRA_WIFI_ATTEMPTED = HelperProtocol.EXTRA_WIFI_ATTEMPTED
    @JvmField val EXTRA_WIFI_ACTION = HelperProtocol.EXTRA_WIFI_ACTION
    @JvmField val EXTRA_WIFI_TOGGLE_SUCCESS = HelperProtocol.EXTRA_WIFI_TOGGLE_SUCCESS
    @JvmField val EXTRA_AIRPLANE_MODE = HelperProtocol.EXTRA_AIRPLANE_MODE
    @JvmField val EXTRA_BLUETOOTH_MANAGED = HelperProtocol.EXTRA_BLUETOOTH_MANAGED
    @JvmField val EXTRA_BLUETOOTH_PREVIOUS = HelperProtocol.EXTRA_BLUETOOTH_PREVIOUS
    @JvmField val EXTRA_BLUETOOTH_CHANGED = HelperProtocol.EXTRA_BLUETOOTH_CHANGED
    @JvmField val EXTRA_RESTORE_SUCCESS = HelperProtocol.EXTRA_RESTORE_SUCCESS
    @JvmField val EXTRA_STATUS = HelperProtocol.EXTRA_STATUS
    @JvmField val STATUS_OK = HelperProtocol.STATUS_OK
    @JvmField val STATUS_ALREADY_SLEEPING = HelperProtocol.STATUS_ALREADY_SLEEPING
    @JvmField val STATUS_ALREADY_RESTORED = HelperProtocol.STATUS_ALREADY_RESTORED
    @JvmField val STATUS_NO_ACTIVE_CYCLE = HelperProtocol.STATUS_NO_ACTIVE_CYCLE
    @JvmField val STATUS_RESTORE_FAILED = HelperProtocol.STATUS_RESTORE_FAILED
    @JvmField val STATUS_WIFI_TOGGLE_FAILED = HelperProtocol.STATUS_WIFI_TOGGLE_FAILED
    @JvmField val STATUS_CYCLE_MISMATCH = HelperProtocol.STATUS_CYCLE_MISMATCH
    @JvmField val PHASE_SLEEP = HelperProtocol.PHASE_SLEEP
    @JvmField val PHASE_WAKE = HelperProtocol.PHASE_WAKE
    @JvmField val PHASE_MAINTENANCE_WIFI = HelperProtocol.PHASE_MAINTENANCE_WIFI

    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Android 9+ exposes Wi-Fi control as the OP_CHANGE_WIFI_STATE app-op in
     * addition to the normal CHANGE_WIFI_STATE manifest permission. OEMs may
     * still impose an extra legacy confirmation even when this reports allowed,
     * so diagnostics keep the raw app-op mode instead of treating it as proof
     * that no OEM prompt can appear.
     */
    fun wifiControlAccess(context: Context): HelperWifiControlAccess {
        val packageManager = context.packageManager
        val permissionGranted =
            packageManager.checkPermission(
                Manifest.permission.CHANGE_WIFI_STATE,
                PACKAGE
            ) == PackageManager.PERMISSION_GRANTED

        if (!isInstalled(context)) {
            return HelperWifiControlAccess(
                permissionGranted = false,
                appOpMode = null,
                appOpModeName = "helper_not_installed",
                effectivelyAllowed = false
            )
        }

        val uid =
            runCatching {
                packageManager.getApplicationInfo(PACKAGE, 0).uid
            }.getOrNull()
        val appOps =
            context.getSystemService(Context.APP_OPS_SERVICE)
                as? AppOpsManager
        val mode =
            if (uid != null && appOps != null) {
                runCatching {
                    @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow(
                        "android:change_wifi_state",
                        uid,
                        PACKAGE
                    )
                }.getOrNull()
            } else {
                null
            }

        val allowed =
            when (mode) {
                AppOpsManager.MODE_ALLOWED,
                AppOpsManager.MODE_DEFAULT ->
                    permissionGranted
                AppOpsManager.MODE_IGNORED,
                AppOpsManager.MODE_ERRORED ->
                    false
                else ->
                    null
            }

        return HelperWifiControlAccess(
            permissionGranted = permissionGranted,
            appOpMode = mode,
            appOpModeName =
                when (mode) {
                    AppOpsManager.MODE_ALLOWED -> "allowed"
                    AppOpsManager.MODE_DEFAULT -> "default"
                    AppOpsManager.MODE_IGNORED -> "ignored"
                    AppOpsManager.MODE_ERRORED -> "errored"
                    null -> "unknown"
                    else -> "mode_$mode"
                },
            effectivelyAllowed = allowed
        )
    }

    private fun commandIntent(action: String): Intent =
        Intent(action)
            .setComponent(HELPER_RECEIVER)
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)

    fun sendSleep(
        context: Context,
        wifi: Boolean,
        bluetooth: Boolean,
        cycleId: Long
    ): Boolean {
        if (!isInstalled(context)) return false
        val intent = commandIntent(ACTION_SLEEP)
            .putExtra(EXTRA_WIFI, wifi)
            .putExtra(EXTRA_BLUETOOTH, bluetooth)
            .putExtra(EXTRA_CYCLE_ID, cycleId)
        context.sendBroadcast(intent, PERMISSION)
        Log.i("SleepManager", "Helper sleep request: wifi=$wifi bluetooth=$bluetooth")
        return true
    }

    fun sendWake(context: Context, cycleId: Long): Boolean {
        if (!isInstalled(context)) return false
        context.sendBroadcast(
            commandIntent(ACTION_WAKE)
                .putExtra(EXTRA_CYCLE_ID, cycleId),
            PERMISSION
        )
        Log.i("SleepManager", "Helper wake request")
        return true
    }


    fun setTemporaryWifi(context: Context, enabled: Boolean): Boolean {
        if (!isInstalled(context)) return false
        context.sendBroadcast(
            commandIntent(ACTION_SET_TEMP_WIFI)
                .putExtra(EXTRA_WIFI, enabled),
            PERMISSION
        )
        Log.i("SleepManager", "Helper temporary Wi-Fi request: enabled=$enabled")
        return true
    }

    fun requestState(context: Context): Boolean {
        if (!isInstalled(context)) return false
        context.sendBroadcast(
            commandIntent(ACTION_QUERY),
            PERMISSION
        )
        Log.i("SleepManager", "Helper state query")
        return true
    }

    fun forgetPendingState(context: Context): Boolean {
        if (!isInstalled(context)) return false
        context.sendBroadcast(
            commandIntent(ACTION_FORGET_STATE),
            PERMISSION
        )
        Log.i("SleepManager", "Helper pending state forget request")
        return true
    }

    fun restoreNow(context: Context, cycleId: Long): Boolean {
        if (!isInstalled(context)) return false
        context.sendBroadcast(
            commandIntent(ACTION_RESTORE)
                .putExtra(EXTRA_CYCLE_ID, cycleId),
            PERMISSION
        )
        Log.i("SleepManager", "Helper restore request")
        return true
    }
}
