package com.med.sleepmanager.integration

import android.content.Context
import android.content.Intent

object BasicSyncController {
    const val PACKAGE = "com.chiller3.basicsync"

    private const val ACTION_STOP = "$PACKAGE.STOP"
    private const val ACTION_AUTO_MODE = "$PACKAGE.AUTO_MODE"

    fun isInstalled(context: Context): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(PACKAGE, 0)
        }.isSuccess

    fun versionName(context: Context): String? =
        runCatching {
            context.packageManager.getPackageInfo(PACKAGE, 0).versionName
        }.getOrNull()

    fun open(context: Context): Boolean {
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(PACKAGE)
                ?: return false

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    fun sendStop(context: Context): Boolean =
        sendRemoteControl(context, ACTION_STOP)

    fun sendAutoMode(context: Context): Boolean =
        sendRemoteControl(context, ACTION_AUTO_MODE)

    private fun sendRemoteControl(context: Context, action: String): Boolean {
        if (!isInstalled(context)) return false

        return runCatching {
            context.sendBroadcast(
                Intent(action).setPackage(PACKAGE)
            )
            true
        }.getOrDefault(false)
    }
}
