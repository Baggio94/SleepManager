package com.med.sleepmanager.integration.connector

import android.content.Context
import com.med.sleepmanager.integration.TailscaleController

object TailscaleConnector : AppConnector {
    override val id: String = "tailscale"
    override val wakeRequiresNetwork: Boolean = true

    const val TOKEN_VERIFY_DISCONNECT = "verify_disconnect"
    const val TOKEN_RESTORE = "restore"

    override fun isInstalled(context: Context): Boolean =
        TailscaleController.isInstalled(context)

    override fun availability(context: Context): ConnectorAvailability =
        if (isInstalled(context)) {
            ConnectorAvailability.Available
        } else {
            ConnectorAvailability.Unavailable("Tailscale is not installed")
        }

    override fun currentState(context: Context): ConnectorState {
        if (!isInstalled(context)) return ConnectorState.UNKNOWN

        return if (TailscaleController.hasAnyVpnTransport(context)) {
            // Android exposes that a VPN transport exists, but does not expose
            // another app's VPN owner to ordinary third-party apps. Treat this
            // as unknown until the disconnect request is verified.
            ConnectorState.UNKNOWN
        } else {
            ConnectorState.INACTIVE
        }
    }

    override fun sleep(context: Context): ConnectorSleepResult {
        if (!isInstalled(context)) {
            return ConnectorSleepResult(
                attempted = false,
                changed = false,
                detail = "Tailscale not installed"
            )
        }

        if (!TailscaleController.hasAnyVpnTransport(context)) {
            return ConnectorSleepResult(
                attempted = false,
                changed = false,
                detail = "No VPN active"
            )
        }

        val sent = TailscaleController.sendDisconnect(context)
        return ConnectorSleepResult(
            attempted = sent,
            changed = false,
            restoreToken = if (sent) TOKEN_VERIFY_DISCONNECT else null,
            detail = if (sent) {
                "DISCONNECT sent; verification pending"
            } else {
                "DISCONNECT not sent"
            }
        )
    }

    override fun wake(
        context: Context,
        restoreToken: String?
    ): ConnectorWakeResult {
        if (restoreToken != TOKEN_RESTORE) {
            return ConnectorWakeResult(
                attempted = false,
                success = false,
                detail = "Tailscale disconnect was not verified"
            )
        }

        val sent = TailscaleController.sendConnect(context)
        return ConnectorWakeResult(
            attempted = sent,
            success = sent,
            detail = if (sent) "CONNECT sent" else "CONNECT not sent"
        )
    }
}
