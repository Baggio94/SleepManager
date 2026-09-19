package com.med.sleepmanager.integration.connector

import android.content.Context
import com.med.sleepmanager.integration.SyncthingController

object SyncthingConnector : AppConnector {
    private const val TOKEN_CONFIRMED_RUNNING_PREFIX = "confirmed:"
    private const val TOKEN_UNVERIFIED_PREFIX = "unverified:"

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

        val confirmedRunning = SyncthingController.healthProbeRunning()
        val sent = SyncthingController.sendStopTo(context, target.packageName)
        val token =
            if (!sent) {
                null
            } else if (confirmedRunning) {
                TOKEN_CONFIRMED_RUNNING_PREFIX + target.packageName
            } else {
                TOKEN_UNVERIFIED_PREFIX + target.packageName
            }

        return ConnectorSleepResult(
            attempted = true,
            changed = sent,
            restoreToken = token,
            detail = when {
                !sent -> "STOP not sent"
                confirmedRunning -> "STOP sent; running state was confirmed"
                else -> "STOP sent; previous state could not be confirmed"
            }
        )
    }

    override fun wake(context: Context, restoreToken: String?): ConnectorWakeResult {
        val packageName = restoreTargetPackage(restoreToken)
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

    fun restoreTargetPackage(restoreToken: String?): String? =
        when {
            restoreToken.isNullOrBlank() -> null
            restoreToken.startsWith(TOKEN_CONFIRMED_RUNNING_PREFIX) ->
                restoreToken.removePrefix(TOKEN_CONFIRMED_RUNNING_PREFIX)
            restoreToken.startsWith(TOKEN_UNVERIFIED_PREFIX) ->
                restoreToken.removePrefix(TOKEN_UNVERIFIED_PREFIX)
            else -> restoreToken
        }

    /**
     * Returns false only when STOP can be proven to have failed.
     * Null means the previous state was not verifiable, so legacy behavior is kept.
     */
    fun verifyStopAfterGrace(
        restoreToken: String?
    ): Boolean? {
        if (
            restoreToken == null ||
            !restoreToken.startsWith(TOKEN_CONFIRMED_RUNNING_PREFIX)
        ) {
            return null
        }

        return !SyncthingController.healthProbeRunning()
    }
}
