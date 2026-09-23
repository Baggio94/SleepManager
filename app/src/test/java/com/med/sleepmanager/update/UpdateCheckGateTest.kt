package com.med.sleepmanager.update

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckGateTest {
    private val now = 2_000_000_000L

    private fun UpdateCheckGate.begin(
        trigger: UpdateCheckTrigger,
        automatic: Boolean = true,
        online: Boolean = true,
        time: Long = now,
        attempted: Long = 0L,
        succeeded: Long = 0L
    ) = tryBegin(trigger, automatic, online, time, attempted, succeeded)

    @Test
    fun everyForegroundSessionChecksEvenAfterARecentSuccessfulCheck() {
        val gate = UpdateCheckGate()
        repeat(3) {
            assertEquals(
                UpdateCheckStart.READY,
                gate.begin(UpdateCheckTrigger.FOREGROUND, attempted = now, succeeded = now)
            )
            gate.finish()
        }
    }

    @Test
    fun dailyJobUsesLastSuccessAndBecomesDueAtTwentyFourHours() {
        val gate = UpdateCheckGate()
        val lastSuccess = now - UpdateCheckGate.BACKGROUND_INTERVAL_MS
        assertEquals(
            UpdateCheckStart.NOT_DUE,
            gate.begin(UpdateCheckTrigger.BACKGROUND, time = now - 1L, succeeded = lastSuccess)
        )
        assertEquals(
            UpdateCheckStart.READY,
            gate.begin(UpdateCheckTrigger.BACKGROUND, succeeded = lastSuccess)
        )
    }

    @Test
    fun backgroundFailureHasShortBackoffRatherThanAnotherDayOfSuppression() {
        val gate = UpdateCheckGate()
        assertEquals(
            UpdateCheckStart.NOT_DUE,
            gate.begin(UpdateCheckTrigger.BACKGROUND, attempted = now - 1L)
        )
        assertEquals(
            UpdateCheckStart.READY,
            gate.begin(
                UpdateCheckTrigger.BACKGROUND,
                attempted = now - UpdateCheckGate.FAILURE_BACKOFF_MS
            )
        )
    }

    @Test
    fun failedAttemptDoesNotBlockNextForegroundSession() {
        val gate = UpdateCheckGate()
        assertEquals(UpdateCheckStart.READY, gate.begin(UpdateCheckTrigger.FOREGROUND))
        gate.finish() // Network error: no success timestamp was recorded.
        assertEquals(
            UpdateCheckStart.READY,
            gate.begin(UpdateCheckTrigger.FOREGROUND, attempted = now)
        )
    }

    @Test
    fun automaticOptOutAppliesToForegroundAndBackgroundButNotManualChecks() {
        val gate = UpdateCheckGate()
        listOf(UpdateCheckTrigger.FOREGROUND, UpdateCheckTrigger.BACKGROUND).forEach {
            assertEquals(UpdateCheckStart.DISABLED, gate.begin(it, automatic = false))
        }
        assertEquals(UpdateCheckStart.READY, gate.begin(UpdateCheckTrigger.MANUAL, automatic = false))
    }

    @Test
    fun offlineAutomaticCheckDoesNotClaimTheGateOrPreventNetworkRecovery() {
        val gate = UpdateCheckGate()
        assertEquals(UpdateCheckStart.NOT_DUE, gate.begin(UpdateCheckTrigger.FOREGROUND, online = false))
        assertEquals(UpdateCheckStart.READY, gate.begin(UpdateCheckTrigger.FOREGROUND))
    }

    @Test
    fun manualCheckCanReportItsOwnNetworkErrorWithoutAndroidValidation() {
        assertEquals(
            UpdateCheckStart.READY,
            UpdateCheckGate().begin(UpdateCheckTrigger.MANUAL, online = false)
        )
    }

    @Test
    fun foregroundAndManualRequestsJoinAnInFlightDailyCheckWithoutAnotherWorker() {
        val gate = UpdateCheckGate()
        assertEquals(UpdateCheckStart.READY, gate.begin(UpdateCheckTrigger.BACKGROUND))
        assertEquals(UpdateCheckStart.NOT_DUE, gate.begin(UpdateCheckTrigger.FOREGROUND))
        assertEquals(UpdateCheckStart.NOT_DUE, gate.begin(UpdateCheckTrigger.MANUAL))
        gate.finish()
        assertEquals(UpdateCheckStart.READY, gate.begin(UpdateCheckTrigger.MANUAL))
    }

    @Test
    fun concurrentTriggersStartOnlyOneRequest() {
        val gate = UpdateCheckGate()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val results = (1..8).map {
                pool.submit<UpdateCheckStart> {
                    assertTrue(start.await(5L, TimeUnit.SECONDS))
                    gate.begin(UpdateCheckTrigger.FOREGROUND)
                }
            }
            start.countDown()
            assertEquals(1, results.count { it.get(5L, TimeUnit.SECONDS) == UpdateCheckStart.READY })
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun clockMovedBackwardsDoesNotSuppressChecksIndefinitely() {
        assertEquals(
            UpdateCheckStart.READY,
            UpdateCheckGate().begin(UpdateCheckTrigger.BACKGROUND, attempted = now + 1000L, succeeded = now + 1000L)
        )
    }
}
