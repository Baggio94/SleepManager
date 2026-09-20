package com.med.sleepmanager.integration.connector

import android.content.Context
import com.med.sleepmanager.integration.JamesDspController

object JamesDspConnector : AppConnector {
    override val id: String = "jamesdsp"
    override val wakeRequiresNetwork: Boolean = false

    override fun isInstalled(context: Context): Boolean =
        JamesDspController.isInstalled(context)

    override fun availability(context: Context): ConnectorAvailability =
        if (isInstalled(context)) {
            ConnectorAvailability.Available
        } else {
            ConnectorAvailability.Unavailable("JamesDSP is not installed")
        }

    // JamesDSP exposes a clean exported ON/OFF receiver, but it does not expose
    // a public state-query API to other normal apps. Keep UNKNOWN explicit.
    override fun currentState(context: Context): ConnectorState =
        ConnectorState.UNKNOWN

    override fun sleep(context: Context): ConnectorSleepResult {
        val target =
            JamesDspController.selectedTarget(context)
                ?: return ConnectorSleepResult(
                    attempted = false,
                    changed = false,
                    detail = "JamesDSP is not installed"
                )

        val sent =
            JamesDspController.setPowered(
                context = context,
                packageName = target.packageName,
                enabled = false
            )

        return ConnectorSleepResult(
            attempted = sent,
            changed = sent,
            restoreToken = if (sent) target.packageName else null,
            detail =
                if (sent) {
                    "Power OFF sent; previous state could not be queried"
                } else {
                    "Power OFF not sent"
                }
        )
    }

    override fun wake(
        context: Context,
        restoreToken: String?
    ): ConnectorWakeResult {
        val packageName =
            restoreToken?.takeIf { it.isNotBlank() }
                ?: return ConnectorWakeResult(
                    attempted = false,
                    success = false,
                    detail = "Missing JamesDSP restore target"
                )

        val sent =
            JamesDspController.setPowered(
                context = context,
                packageName = packageName,
                enabled = true
            )

        return ConnectorWakeResult(
            attempted = sent,
            success = sent,
            detail = if (sent) "Power ON sent" else "Power ON not sent"
        )
    }
}
