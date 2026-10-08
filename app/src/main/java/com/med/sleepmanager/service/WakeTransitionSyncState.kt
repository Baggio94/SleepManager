package com.med.sleepmanager.service

/**
 * Tracks whether a wake-triggered sync-and-stop maintenance run is pending.
 *
 * Persistent arming remains in SyncTransitionStore. This class only owns the
 * transient in-process pending flag after a real wake has been evaluated.
 */
internal class WakeTransitionSyncState {
    var isPending: Boolean = false
        private set

    fun arm(
        wakeSyncWasArmed: Boolean,
        syncThenStopAvailable: Boolean
    ) {
        isPending = wakeSyncWasArmed && syncThenStopAvailable
    }

    /**
     * A real wake can run completion-aware sync even while the Helper is
     * awaiting acknowledgement, provided Wi-Fi is already enabled and
     * no network-dependent connector restore is waiting for the Helper.
     * The sync runner independently waits for a validated network.
     */
    fun mayStartWithHelperPending(
        realWake: Boolean,
        wifiEnabled: Boolean?,
        networkRestorePending: Boolean
    ): Boolean =
        isPending &&
            realWake &&
            wifiEnabled == true &&
            !networkRestorePending

    fun clear() {
        isPending = false
    }
}
