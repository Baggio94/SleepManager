package com.med.sleepmanager.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeSyncFallbackPolicyTest {
    private fun decision(
        pending: Boolean = true,
        cycleMatches: Boolean = true,
        realWake: Boolean = true,
        wifiReportedEnabled: Boolean? = true,
        wifiNetworkPresent: Boolean = false,
        networkRestorePending: Boolean = false
    ) = WakeSyncFallbackPolicy.evaluate(
        pending,
        cycleMatches,
        realWake,
        wifiReportedEnabled,
        wifiNetworkPresent,
        networkRestorePending
    )

    @Test fun readyWhenWifiRestoredByFirmwareDespiteMissingHelperAck() {
        assertEquals(WakeSyncFallbackDecision.READY, decision())
        assertEquals(
            WakeSyncFallbackDecision.READY,
            decision(wifiReportedEnabled = false, wifiNetworkPresent = true)
        )
    }

    @Test fun waitForWifiUntilItReallyReturns() {
        assertEquals(
            WakeSyncFallbackDecision.WAIT_FOR_WIFI,
            decision(wifiReportedEnabled = false)
        )
        assertEquals(
            WakeSyncFallbackDecision.WAIT_FOR_WIFI,
            decision(wifiReportedEnabled = null)
        )
        // The next timed recheck can start sync; no one-shot lost opportunity.
        assertEquals(
            WakeSyncFallbackDecision.READY,
            decision(wifiReportedEnabled = true)
        )
    }

    @Test fun doNotRunOnFalseWakeOrBeforeConnectorRestoration() {
        assertEquals(
            WakeSyncFallbackDecision.WAIT_FOR_REAL_WAKE,
            decision(realWake = false)
        )
        assertEquals(
            WakeSyncFallbackDecision.WAIT_FOR_CONNECTOR,
            decision(networkRestorePending = true)
        )
    }

    @Test fun cancelIfTransitionWasConsumedOrCycleChanged() {
        assertEquals(WakeSyncFallbackDecision.CANCEL, decision(pending = false))
        assertEquals(WakeSyncFallbackDecision.CANCEL, decision(cycleMatches = false))
    }

    @Test fun boundedRechecksNeverRunIndefinitely() {
        assertTrue(WakeSyncFallbackPolicy.canRetry(1, 20))
        assertTrue(WakeSyncFallbackPolicy.canRetry(19, 20))
        assertFalse(WakeSyncFallbackPolicy.canRetry(20, 20))
        assertFalse(WakeSyncFallbackPolicy.canRetry(21, 20))
        assertFalse(WakeSyncFallbackPolicy.canRetry(0, 20))
    }
}
