package com.med.sleepmanager.integration.connector

import android.content.Context
import com.med.sleepmanager.integration.SyncthingController

object SyncthingConnector : AppConnector {
    override val id: String = "syncthing"
    override val wakeRequiresNetwork: Boolean = true

    override fun isInstalled(context: Context): Boolean =
        SyncthingController.installedTargets(context).isNotEmpty()

    override fun availability(context: Context): ConnectorAvailability =
        if (SyncthingController.selectedTarget(context) != null) {
            ConnectorAvailability.Available
        } else {
            ConnectorAvailability.Unavailable("Syncthing-Fork is not installed")
        }

    // Syncthing-Fork exposes STOP/FOLLOW control broadcasts, but no reliable
    // public state query that SleepManager can use to distinguish running from
    // already-paused. Keep this explicit rather than pretending the state is known.
    override fun currentState(context: Context): ConnectorState =
        ConnectorState.UNKNOWN

    override fun sleep(context: Context): ConnectorSleepResult {
        val target = SyncthingController.selectedTarget(context)
            ?: return ConnectorSleepResult(
                attempted = false,
                changed = false,
                detail = "No Syncthing target installed"
            )

        val sent = SyncthingController.sendStopTo(context, target.packageName)
        return ConnectorSleepResult(
            attempted = true,
            changed = sent,
            restoreToken = if (sent) target.packageName else null,
            detail = if (sent) "STOP sent" else "STOP not sent"
        )
    }

    override fun wake(context: Context, restoreToken: String?): ConnectorWakeResult {
        val packageName = restoreToken
            ?: return ConnectorWakeResult(
                attempted = false,
                success = false,
                detail = "Missing restore target"
            )

        val sent = SyncthingController.sendFollowTo(context, packageName)
        return ConnectorWakeResult(
            attempted = true,
            success = sent,
            detail = if (sent) "FOLLOW sent" else "FOLLOW not sent"
        )
    }
}
