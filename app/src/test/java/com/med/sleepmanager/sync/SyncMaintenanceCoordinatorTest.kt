package com.med.sleepmanager.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMaintenanceCoordinatorTest {
    private class FakeProvider(
        override val id: String,
        states: List<SyncCompletionState>,
        private val startSucceeds: Boolean = true,
        private val stopSucceeds: Boolean = true
    ) : SyncCompletionProvider {
        override val displayName: String = id
        private val queue = ArrayDeque(states)
        var starts = 0
        var stops = 0

        override fun startSync(): SyncControlResult {
            starts++
            return SyncControlResult(
                attempted = true,
                success = startSucceeds,
                detail = if (startSucceeds) "started" else "start failed"
            )
        }

        override fun stopSync(): SyncControlResult {
            stops++
            return SyncControlResult(
                attempted = true,
                success = stopSucceeds,
                detail = if (stopSucceeds) "stopped" else "stop failed"
            )
        }

        override fun currentSyncState(): SyncCompletionState =
            if (queue.isEmpty()) {
                SyncCompletionState.UNKNOWN
            } else {
                queue.removeFirst()
            }
    }

    @Test
    fun noTargetsFinishesWithoutWork() {
        val coordinator = SyncMaintenanceCoordinator(
            SyncMaintenanceTrigger.PERIODIC_SLEEP,
            emptyList()
        )

        val result = coordinator.begin(networkReady = true, nowMs = 0L)

        assertEquals(SyncMaintenancePhase.FINISHED, result.phase)
        assertEquals(SyncMaintenanceOutcome.NO_TARGETS, result.outcome)
    }

    @Test
    fun waitsForValidatedNetworkBeforeStartingProviders() {
        val provider = FakeProvider("one", listOf(SyncCompletionState.SYNCING))
        val coordinator = SyncMaintenanceCoordinator(
            SyncMaintenanceTrigger.AFTER_WAKE,
            listOf(provider)
        )

        assertEquals(
            SyncMaintenancePhase.WAITING_FOR_NETWORK,
            coordinator.begin(networkReady = false, nowMs = 0L).phase
        )
        assertEquals(0, provider.starts)

        assertEquals(
            SyncMaintenancePhase.WAITING_FOR_SYNC,
            coordinator.onNetworkReady(nowMs = 100L).phase
        )
        assertEquals(1, provider.starts)
    }

    @Test
    fun networkTimeoutAbortsWithoutStartingClients() {
        val provider = FakeProvider("one", listOf(SyncCompletionState.SYNCING))
        val coordinator = SyncMaintenanceCoordinator(
            SyncMaintenanceTrigger.BEFORE_SLEEP,
            listOf(provider)
        )

        coordinator.begin(networkReady = false, nowMs = 0L)
        val result = coordinator.onNetworkUnavailable()

        assertEquals(SyncMaintenanceOutcome.NETWORK_UNAVAILABLE, result.outcome)
        assertEquals(0, provider.starts)
        assertEquals(0, provider.stops)
    }

    @Test
    fun syncingThenSyncedStopsClientAndCompletes() {
        val provider = FakeProvider(
            "one",
            listOf(
                SyncCompletionState.SYNCING,
                SyncCompletionState.SYNCED
            )
        )
        val coordinator = SyncMaintenanceCoordinator(
            SyncMaintenanceTrigger.AFTER_WAKE,
            listOf(provider)
        )

        coordinator.begin(networkReady = true, nowMs = 0L)
        coordinator.poll(nowMs = 100L)
        val result = coordinator.poll(nowMs = 200L)

        assertEquals(SyncMaintenanceOutcome.COMPLETED, result.outcome)
        assertEquals(1, provider.starts)
        assertEquals(1, provider.stops)
        assertTrue("one" in result.completedProviderIds)
    }

    @Test
    fun alreadySyncedMustRemainStableBeforeBeingAccepted() {
        val provider = FakeProvider(
            "one",
            listOf(
                SyncCompletionState.SYNCED,
                SyncCompletionState.SYNCED
            )
        )
        val coordinator = SyncMaintenanceCoordinator(
            trigger = SyncMaintenanceTrigger.PERIODIC_SLEEP,
            providers = listOf(provider),
            idleSyncedStabilityMs = 3_000L
        )

        coordinator.begin(networkReady = true, nowMs = 0L)
        val early = coordinator.poll(nowMs = 1_000L)
        assertEquals(SyncMaintenancePhase.WAITING_FOR_SYNC, early.phase)

        val done = coordinator.poll(nowMs = 4_000L)
        assertEquals(SyncMaintenanceOutcome.COMPLETED, done.outcome)
        assertEquals(1, provider.stops)
    }

    @Test
    fun unknownNeverCountsAsCompletedAndTimesOut() {
        val provider = FakeProvider(
            "one",
            listOf(
                SyncCompletionState.UNKNOWN,
                SyncCompletionState.UNKNOWN
            )
        )
        val coordinator = SyncMaintenanceCoordinator(
            trigger = SyncMaintenanceTrigger.PERIODIC_SLEEP,
            providers = listOf(provider),
            syncTimeoutMs = 1_000L
        )

        coordinator.begin(networkReady = true, nowMs = 0L)
        coordinator.poll(nowMs = 500L)
        val result = coordinator.poll(nowMs = 1_000L)

        assertEquals(SyncMaintenanceOutcome.SYNC_TIMEOUT, result.outcome)
        assertEquals(1, provider.stops)
    }

    @Test
    fun cancellationStopsAnyClientStartedByTheSession() {
        val provider = FakeProvider("one", listOf(SyncCompletionState.SYNCING))
        val coordinator = SyncMaintenanceCoordinator(
            SyncMaintenanceTrigger.PERIODIC_SLEEP,
            listOf(provider)
        )

        coordinator.begin(networkReady = true, nowMs = 0L)
        val result = coordinator.cancel()

        assertEquals(SyncMaintenanceOutcome.CANCELLED, result.outcome)
        assertEquals(1, provider.stops)
    }

    @Test
    fun twoProvidersCanFinishAtDifferentTimes() {
        val first = FakeProvider(
            "first",
            listOf(
                SyncCompletionState.SYNCING,
                SyncCompletionState.SYNCED,
                SyncCompletionState.UNKNOWN
            )
        )
        val second = FakeProvider(
            "second",
            listOf(
                SyncCompletionState.SYNCING,
                SyncCompletionState.SYNCING,
                SyncCompletionState.SYNCED
            )
        )
        val coordinator = SyncMaintenanceCoordinator(
            SyncMaintenanceTrigger.AFTER_WAKE,
            listOf(first, second)
        )

        coordinator.begin(networkReady = true, nowMs = 0L)
        coordinator.poll(nowMs = 100L)
        coordinator.poll(nowMs = 200L)
        val result = coordinator.poll(nowMs = 300L)

        assertEquals(SyncMaintenanceOutcome.COMPLETED, result.outcome)
        assertEquals(setOf("first", "second"), result.completedProviderIds)
        assertEquals(1, first.stops)
        assertEquals(1, second.stops)
    }

    @Test
    fun oneStartFailureLetsOtherProviderFinishButReportsErrors() {
        val failed = FakeProvider(
            "failed",
            emptyList(),
            startSucceeds = false
        )
        val good = FakeProvider(
            "good",
            listOf(
                SyncCompletionState.SYNCING,
                SyncCompletionState.SYNCED
            )
        )
        val coordinator = SyncMaintenanceCoordinator(
            SyncMaintenanceTrigger.BEFORE_SLEEP,
            listOf(failed, good)
        )

        coordinator.begin(networkReady = true, nowMs = 0L)
        coordinator.poll(nowMs = 100L)
        val result = coordinator.poll(nowMs = 200L)

        assertEquals(SyncMaintenanceOutcome.COMPLETED_WITH_ERRORS, result.outcome)
        assertTrue("failed" in result.failedProviderIds)
        assertEquals(0, failed.stops)
        assertEquals(1, good.stops)
    }

    @Test
    fun stopFailureIsReportedWithoutLosingCompletion() {
        val provider = FakeProvider(
            "one",
            listOf(
                SyncCompletionState.SYNCING,
                SyncCompletionState.SYNCED
            ),
            stopSucceeds = false
        )
        val coordinator = SyncMaintenanceCoordinator(
            SyncMaintenanceTrigger.AFTER_WAKE,
            listOf(provider)
        )

        coordinator.begin(networkReady = true, nowMs = 0L)
        coordinator.poll(nowMs = 100L)
        val result = coordinator.poll(nowMs = 200L)

        assertEquals(SyncMaintenanceOutcome.COMPLETED_WITH_ERRORS, result.outcome)
        assertTrue("one" in result.completedProviderIds)
        assertTrue("one" in result.failedProviderIds)
    }
}
