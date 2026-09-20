package com.med.sleepmanager.rules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepWakePolicyTest {
    @Test
    fun closedLidFalseWake_doesNotCancelGraceOrRestore() {
        val decision =
            SleepWakePolicy.onScreenOn(
                thorProtectionEnabled = true,
                lidClosed = true,
                sleepDelayPending = true
            )

        assertTrue(decision.suppressWake)
        assertTrue(decision.requestThorLock)
        assertFalse(decision.cancelSleepDelay)
        assertFalse(decision.restoreNormalWake)
    }

    @Test
    fun realWakeWithOpenLid_cancelsGraceAndRestores() {
        val decision =
            SleepWakePolicy.onScreenOn(
                thorProtectionEnabled = true,
                lidClosed = false,
                sleepDelayPending = true
            )

        assertFalse(decision.suppressWake)
        assertFalse(decision.requestThorLock)
        assertTrue(decision.cancelSleepDelay)
        assertTrue(decision.restoreNormalWake)
    }

    @Test
    fun closedLidFalseWake_isSuppressedEvenWithoutGrace() {
        val decision =
            SleepWakePolicy.onScreenOn(
                thorProtectionEnabled = true,
                lidClosed = true,
                sleepDelayPending = false
            )

        assertTrue(decision.suppressWake)
        assertFalse(decision.cancelSleepDelay)
        assertFalse(decision.restoreNormalWake)
    }
}
