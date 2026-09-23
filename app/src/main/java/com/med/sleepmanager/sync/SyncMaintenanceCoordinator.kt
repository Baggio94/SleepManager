package com.med.sleepmanager.sync

enum class SyncMaintenanceTrigger {
    PERIODIC_SLEEP,
    BEFORE_SLEEP,
    AFTER_WAKE
}

enum class SyncMaintenancePhase {
    IDLE,
    WAITING_FOR_NETWORK,
    WAITING_FOR_SYNC,
    FINISHED
}

enum class SyncMaintenanceOutcome {
    NONE,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    NO_TARGETS,
    COMPLETION_UNAVAILABLE,
    NETWORK_UNAVAILABLE,
    START_FAILED,
    SYNC_TIMEOUT,
    CANCELLED
}

data class SyncMaintenanceSnapshot(
    val trigger: SyncMaintenanceTrigger,
    val phase: SyncMaintenancePhase,
    val outcome: SyncMaintenanceOutcome,
    val startedProviderIds: Set<String>,
    val completedProviderIds: Set<String>,
    val failedProviderIds: Set<String>
)

/**
 * Pure synchronization-maintenance state machine.
 *
 * Android networking, Wi-Fi restoration, wake locks and scheduling deliberately
 * live outside this class. The runner feeds network readiness into the state
 * machine and calls [poll] on a bounded cadence while a maintenance session is
 * active.
 */
class SyncMaintenanceCoordinator(
    private val trigger: SyncMaintenanceTrigger,
    providers: List<SyncCompletionProvider>,
    private val syncTimeoutMs: Long = DEFAULT_SYNC_TIMEOUT_MS,
    private val idleSyncedStabilityMs: Long = DEFAULT_IDLE_SYNC_STABILITY_MS
) {
    private data class ProviderSession(
        val provider: SyncCompletionProvider,
        var seenSyncing: Boolean = false,
        var syncedSinceMs: Long? = null,
        var completed: Boolean = false,
        var startFailed: Boolean = false,
        var stopFailed: Boolean = false
    )

    private val sessions = providers
        .distinctBy { it.id }
        .map(::ProviderSession)

    private var phase = SyncMaintenancePhase.IDLE
    private var outcome = SyncMaintenanceOutcome.NONE
    private var syncStartedAtMs: Long? = null

    fun begin(
        networkReady: Boolean,
        nowMs: Long
    ): SyncMaintenanceSnapshot {
        if (phase != SyncMaintenancePhase.IDLE) return snapshot()

        if (sessions.isEmpty()) {
            finish(SyncMaintenanceOutcome.NO_TARGETS)
            return snapshot()
        }

        if (sessions.any { !it.provider.completionStateAvailable }) {
            finish(SyncMaintenanceOutcome.COMPLETION_UNAVAILABLE)
            return snapshot()
        }

        if (!networkReady) {
            phase = SyncMaintenancePhase.WAITING_FOR_NETWORK
            return snapshot()
        }

        startProviders(nowMs)
        return snapshot()
    }

    fun onNetworkReady(nowMs: Long): SyncMaintenanceSnapshot {
        if (phase != SyncMaintenancePhase.WAITING_FOR_NETWORK) return snapshot()
        startProviders(nowMs)
        return snapshot()
    }

    fun onNetworkUnavailable(): SyncMaintenanceSnapshot {
        if (phase == SyncMaintenancePhase.WAITING_FOR_NETWORK) {
            finish(SyncMaintenanceOutcome.NETWORK_UNAVAILABLE)
        }
        return snapshot()
    }

    fun poll(nowMs: Long): SyncMaintenanceSnapshot {
        if (phase != SyncMaintenancePhase.WAITING_FOR_SYNC) return snapshot()

        val startedAt = syncStartedAtMs ?: nowMs
        if (nowMs - startedAt >= syncTimeoutMs) {
            stopStartedProviders()
            finish(SyncMaintenanceOutcome.SYNC_TIMEOUT)
            return snapshot()
        }

        sessions
            .filter { !it.startFailed && !it.completed }
            .forEach { session ->
                when (
                    runCatching { session.provider.currentSyncState() }
                        .getOrDefault(SyncCompletionState.UNKNOWN)
                ) {
                    SyncCompletionState.SYNCING -> {
                        session.seenSyncing = true
                        session.syncedSinceMs = null
                    }

                    SyncCompletionState.SYNCED -> {
                        if (session.seenSyncing) {
                            session.completed = true
                        } else {
                            val since = session.syncedSinceMs
                            if (since == null) {
                                session.syncedSinceMs = nowMs
                            } else if (nowMs - since >= idleSyncedStabilityMs) {
                                // A provider that starts already idle must remain
                                // stably SYNCED long enough to avoid accepting a
                                // stale pre-scan state immediately after START.
                                session.completed = true
                            }
                        }
                    }

                    SyncCompletionState.UNKNOWN -> {
                        // UNKNOWN is never interpreted as success.
                        session.syncedSinceMs = null
                    }
                }
            }

        if (
            sessions
                .filter { !it.startFailed }
                .all { it.completed }
        ) {
            stopStartedProviders()
            finish(
                if (sessions.any { it.startFailed || it.stopFailed }) {
                    SyncMaintenanceOutcome.COMPLETED_WITH_ERRORS
                } else {
                    SyncMaintenanceOutcome.COMPLETED
                }
            )
        }

        return snapshot()
    }

    fun cancel(): SyncMaintenanceSnapshot {
        if (phase == SyncMaintenancePhase.FINISHED) return snapshot()

        if (phase == SyncMaintenancePhase.WAITING_FOR_SYNC) {
            stopStartedProviders()
        }
        finish(SyncMaintenanceOutcome.CANCELLED)
        return snapshot()
    }

    fun snapshot(): SyncMaintenanceSnapshot =
        SyncMaintenanceSnapshot(
            trigger = trigger,
            phase = phase,
            outcome = outcome,
            startedProviderIds = sessions
                .filter { !it.startFailed && syncStartedAtMs != null }
                .mapTo(linkedSetOf()) { it.provider.id },
            completedProviderIds = sessions
                .filter { it.completed }
                .mapTo(linkedSetOf()) { it.provider.id },
            failedProviderIds = sessions
                .filter { it.startFailed || it.stopFailed }
                .mapTo(linkedSetOf()) { it.provider.id }
        )

    private fun startProviders(nowMs: Long) {
        sessions.forEach { session ->
            val result = runCatching { session.provider.startSync() }
                .getOrElse {
                    SyncControlResult(
                        attempted = true,
                        success = false,
                        detail = it.message ?: "START failed"
                    )
                }
            session.startFailed = !result.success
        }

        if (sessions.all { it.startFailed }) {
            finish(SyncMaintenanceOutcome.START_FAILED)
            return
        }

        syncStartedAtMs = nowMs
        phase = SyncMaintenancePhase.WAITING_FOR_SYNC
    }

    private fun stopStartedProviders() {
        sessions
            .filter { !it.startFailed }
            .forEach { session ->
                val result = runCatching { session.provider.stopSync() }
                    .getOrElse {
                        SyncControlResult(
                            attempted = true,
                            success = false,
                            detail = it.message ?: "STOP failed"
                        )
                    }
                if (!result.success) {
                    session.stopFailed = true
                }
            }
    }

    private fun finish(finalOutcome: SyncMaintenanceOutcome) {
        phase = SyncMaintenancePhase.FINISHED
        outcome = finalOutcome
    }

    companion object {
        const val DEFAULT_SYNC_TIMEOUT_MS = 30L * 60L * 1000L
        const val DEFAULT_IDLE_SYNC_STABILITY_MS = 3_000L
    }
}
