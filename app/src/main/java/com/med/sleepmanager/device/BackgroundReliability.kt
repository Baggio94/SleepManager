package com.med.sleepmanager.device

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import java.util.concurrent.TimeUnit

object BackgroundReliability {
    data class Snapshot(
        val batteryOptimizationExempt: Boolean,
        val unusedAppRestrictionsActive: Boolean?
    ) {
        val needsAttention: Boolean
            get() = !batteryOptimizationExempt ||
                unusedAppRestrictionsActive == true
    }

    fun snapshot(context: Context): Snapshot =
        Snapshot(
            batteryOptimizationExempt = batteryOptimizationExempt(context),
            unusedAppRestrictionsActive =
                unusedAppRestrictionsActive(context)
        )

    private fun batteryOptimizationExempt(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: return true
        return powerManager.isIgnoringBatteryOptimizations(
            context.packageName
        )
    }

    private fun unusedAppRestrictionsActive(context: Context): Boolean? =
        runCatching {
            val status =
                PackageManagerCompat
                    .getUnusedAppRestrictionsStatus(context)
                    .get(2, TimeUnit.SECONDS)

            when (status) {
                UnusedAppRestrictionsConstants.DISABLED,
                UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE -> false

                UnusedAppRestrictionsConstants.API_30_BACKPORT,
                UnusedAppRestrictionsConstants.API_30,
                UnusedAppRestrictionsConstants.API_31 -> true

                else -> null
            }
        }.getOrNull()
}
