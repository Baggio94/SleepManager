package com.med.sleepmanager.service

/**
 * A delayed Helper acknowledgement must not suppress a wake sync after the
 * firmware has restored Wi-Fi. This policy never treats the Helper's radio
 * transaction as complete; it only decides whether AFTER_WAKE may start.
 */
internal enum class WakeSyncFallbackDecision {
    READY,
    WAIT_FOR_WIFI,
    WAIT_FOR_REAL_WAKE,
    WAIT_FOR_CONNECTOR,
    CANCEL
}

internal object WakeSyncFallbackPolicy {
    fun evaluate(
        pending: Boolean,
        cycleMatches: Boolean,
        realWake: Boolean,
        wifiReportedEnabled: Boolean?,
        wifiNetworkPresent: Boolean,
        networkRestorePending: Boolean
    ): WakeSyncFallbackDecision =
        when {
            !pending || !cycleMatches -> WakeSyncFallbackDecision.CANCEL
            !realWake -> WakeSyncFallbackDecision.WAIT_FOR_REAL_WAKE
            networkRestorePending -> WakeSyncFallbackDecision.WAIT_FOR_CONNECTOR
            wifiReportedEnabled == true || wifiNetworkPresent -> WakeSyncFallbackDecision.READY
            else -> WakeSyncFallbackDecision.WAIT_FOR_WIFI
        }

    fun canRetry(checksCompleted: Int, maxChecks: Int): Boolean =
        checksCompleted > 0 && checksCompleted < maxChecks
}
