package com.med.sleepmanager.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperRestoreRetryStateTest {
    @Test
    fun sameCycleRetriesAreBounded() {
        val state = HelperRestoreRetryState()
        state.begin(cycleId = 42L, requestSent = true)

        assertTrue(state.pending)
        assertEquals(1, state.attempts)
        assertEquals(
            HelperRestoreRetryDecision.RETRY,
            state.decision(
                activeCycleId = 42L,
                stillNeedsRestore = true,
                maxAttempts = 3
            )
        )

        state.recordRetrySent()
        assertEquals(
            HelperRestoreRetryDecision.RETRY,
            state.decision(
                activeCycleId = 42L,
                stillNeedsRestore = true,
                maxAttempts = 3
            )
        )

        state.recordRetrySent()
        assertEquals(
            HelperRestoreRetryDecision.EXHAUSTED,
            state.decision(
                activeCycleId = 42L,
                stillNeedsRestore = true,
                maxAttempts = 3
            )
        )
    }

    @Test
    fun firstRetryUsesBroadcastOnlyOnce() {
        val state = HelperRestoreRetryState()
        assertFalse(state.shouldUseBroadcastFallback())
        state.begin(cycleId = 42L, requestSent = true)
        assertTrue(state.shouldUseBroadcastFallback())
        state.recordRetrySent()
        assertFalse(state.shouldUseBroadcastFallback())
        state.acknowledge(resultCycleId = 42L)
        assertFalse(state.shouldUseBroadcastFallback())
    }

    @Test
    fun stoppedPackageStartsWithBroadcast_thenAlternatesWithService() {
        val state = HelperRestoreRetryState()
        state.begin(
            cycleId = 42L,
            requestSent = true,
            initialWasBroadcast = true
        )
        assertFalse(state.shouldUseBroadcastFallback()) // second attempt: service
        state.recordRetrySent()
        assertTrue(state.shouldUseBroadcastFallback()) // third attempt: broadcast
        state.recordRetrySent()
        assertEquals(
            HelperRestoreRetryDecision.EXHAUSTED,
            state.decision(42L, stillNeedsRestore = true, maxAttempts = 3)
        )
        state.acknowledge(42L)
        assertFalse(state.shouldUseBroadcastFallback())
    }

    @Test
    fun packageStoppedRoutingDistinguishesStoppedFromProcessDeath() {
        assertTrue(HelperWakeRoutingPolicy.useBroadcastFirst(true))
        assertFalse(HelperWakeRoutingPolicy.useBroadcastFirst(false))
        assertFalse(HelperWakeRoutingPolicy.useBroadcastFirst(null))
    }

    @Test
    fun sameCycleAcknowledgementClearsRetry() {
        val state = HelperRestoreRetryState()
        state.begin(cycleId = 77L, requestSent = true)
        state.acknowledge(resultCycleId = 77L)

        assertFalse(state.pending)
        assertEquals(0, state.attempts)
    }

    @Test
    fun staleCycleNeverReplays() {
        val state = HelperRestoreRetryState()
        state.begin(cycleId = 9L, requestSent = true)

        assertEquals(
            HelperRestoreRetryDecision.STALE,
            state.decision(
                activeCycleId = 10L,
                stillNeedsRestore = true,
                maxAttempts = 3
            )
        )
    }

    @Test
    fun alreadyRestoredStateStopsReplay() {
        val state = HelperRestoreRetryState()
        state.begin(cycleId = 9L, requestSent = true)

        assertEquals(
            HelperRestoreRetryDecision.STALE,
            state.decision(
                activeCycleId = 9L,
                stillNeedsRestore = false,
                maxAttempts = 3
            )
        )
    }
}
