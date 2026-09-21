package com.med.sleepmanager.integration.connector

import android.content.Context
import com.med.sleepmanager.integration.BasicSyncController

object BasicSyncConnector : AppConnector {
    override val id: String = "basicsync"
    override val wakeRequiresNetwork: Boolean = false

    const val TOKEN_AUTO_MODE = "auto_mode"

    override fun isInstalled(context: Context): Boolean =
        BasicSyncController.isInstalled(context)

    override fun availability(context: Context): ConnectorAvailability =
        if (isInstalled(context)) {
            ConnectorAvailability.Available
        } else {
            ConnectorAvailability.Unavailable("BasicSync is not installed")
        }

    // BasicSync exposes remote-control broadcasts for START/STOP/AUTO_MODE/
    // MANUAL_MODE, but no public state-query API for other normal apps.
    override fun currentState(context: Context): ConnectorState =
        ConnectorState.UNKNOWN

    override fun sleep(context: Context): ConnectorSleepResult {
        if (!isInstalled(context)) {
            return ConnectorSleepResult(
                attempted = false,
                changed = false,
                detail = "BasicSync is not installed"
            )
        }

        val sent = BasicSyncController.sendStop(context)
        return ConnectorSleepResult(
            attempted = sent,
            changed = sent,
            restoreToken = if (sent) TOKEN_AUTO_MODE else null,
            detail =
                if (sent) {
                    "STOP sent; BasicSync remote control must be enabled"
                } else {
                    "STOP not sent"
                }
        )
    }

    override fun wake(
        context: Context,
        restoreToken: String?
    ): ConnectorWakeResult {
        if (restoreToken != TOKEN_AUTO_MODE) {
            return ConnectorWakeResult(
                attempted = false,
                success = false,
                detail = "Missing BasicSync restore token"
            )
        }

        val sent = BasicSyncController.sendAutoMode(context)
        return ConnectorWakeResult(
            attempted = sent,
            success = sent,
            detail =
                if (sent) {
                    "AUTO_MODE sent"
                } else {
                    "AUTO_MODE not sent"
                }
        )
    }
}
