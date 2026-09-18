package com.med.sleepmanager.integration

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

object HelperController {
    const val PACKAGE = "com.med.sleepmanager.helper"
    const val PERMISSION = "com.med.sleepmanager.permission.CONTROL_HELPER"

    private const val ACTION_SLEEP = "com.med.sleepmanager.helper.action.SLEEP"
    private const val ACTION_WAKE = "com.med.sleepmanager.helper.action.WAKE"
    private const val ACTION_RESTORE = "com.med.sleepmanager.helper.action.RESTORE"
    private const val ACTION_QUERY = "com.med.sleepmanager.helper.action.QUERY_STATE"
    const val ACTION_STATE = "com.med.sleepmanager.helper.action.STATE"
    const val ACTION_RESULT = "com.med.sleepmanager.helper.action.RESULT"

    private const val EXTRA_WIFI = "wifi"
    private const val EXTRA_BLUETOOTH = "bluetooth"
    const val EXTRA_WIFI_STATE = "wifi_state"
    const val EXTRA_BLUETOOTH_STATE = "bluetooth_state"
    const val EXTRA_PHASE = "phase"
    const val EXTRA_WIFI_MANAGED = "wifi_managed"
    const val EXTRA_WIFI_PREVIOUS = "wifi_previous"
    const val EXTRA_WIFI_CHANGED = "wifi_changed"
    const val EXTRA_BLUETOOTH_MANAGED = "bluetooth_managed"
    const val EXTRA_BLUETOOTH_PREVIOUS = "bluetooth_previous"
    const val EXTRA_BLUETOOTH_CHANGED = "bluetooth_changed"
    const val EXTRA_RESTORE_SUCCESS = "restore_success"
    const val PHASE_SLEEP = "sleep"
    const val PHASE_WAKE = "wake"

    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun sendSleep(context: Context, wifi: Boolean, bluetooth: Boolean): Boolean {
        if (!isInstalled(context)) return false
        val intent = Intent(ACTION_SLEEP)
            .setPackage(PACKAGE)
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            .putExtra(EXTRA_WIFI, wifi)
            .putExtra(EXTRA_BLUETOOTH, bluetooth)
        context.sendBroadcast(intent, PERMISSION)
        Log.i("SleepManager", "Helper sleep request: wifi=$wifi bluetooth=$bluetooth")
        return true
    }

    fun sendWake(context: Context): Boolean {
        if (!isInstalled(context)) return false
        context.sendBroadcast(
            Intent(ACTION_WAKE)
                .setPackage(PACKAGE)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES),
            PERMISSION
        )
        Log.i("SleepManager", "Helper wake request")
        return true
    }


    fun requestState(context: Context): Boolean {
        if (!isInstalled(context)) return false
        context.sendBroadcast(
            Intent(ACTION_QUERY)
                .setPackage(PACKAGE)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES),
            PERMISSION
        )
        Log.i("SleepManager", "Helper state query")
        return true
    }

    fun restoreNow(context: Context): Boolean {
        if (!isInstalled(context)) return false
        context.sendBroadcast(
            Intent(ACTION_RESTORE)
                .setPackage(PACKAGE)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES),
            PERMISSION
        )
        Log.i("SleepManager", "Helper restore request")
        return true
    }
}
