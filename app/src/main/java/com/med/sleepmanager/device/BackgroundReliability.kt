package com.med.sleepmanager.device

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import java.util.concurrent.TimeUnit

object BackgroundReliability {
    enum class Status {
        OK,
        NEEDS_ATTENTION,
        UNAVAILABLE,
        UNKNOWN
    }

    data class Snapshot(
        val batteryOptimization: Status,
        val unusedAppRestrictions: Status
    )

    fun snapshot(context: Context): Snapshot =
        Snapshot(
            batteryOptimization = batteryOptimizationStatus(context),
            unusedAppRestrictions = unusedAppRestrictionsStatus(context)
        )

    private fun batteryOptimizationStatus(context: Context): Status {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return Status.UNAVAILABLE
        }

        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: return Status.UNKNOWN

        return if (
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        ) {
            Status.OK
        } else {
            Status.NEEDS_ATTENTION
        }
    }

    private fun unusedAppRestrictionsStatus(context: Context): Status =
        runCatching {
            when (
                PackageManagerCompat
                    .getUnusedAppRestrictionsStatus(context)
                    .get(2, TimeUnit.SECONDS)
            ) {
                UnusedAppRestrictionsConstants.DISABLED ->
                    Status.OK

                UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE ->
                    Status.UNAVAILABLE

                UnusedAppRestrictionsConstants.API_30_BACKPORT,
                UnusedAppRestrictionsConstants.API_30,
                UnusedAppRestrictionsConstants.API_31 ->
                    Status.NEEDS_ATTENTION

                else ->
                    Status.UNKNOWN
            }
        }.getOrDefault(Status.UNKNOWN)
}
