package com.med.sleepmanager.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeTransitionSyncStateTest {
    @Test
    fun arm_requiresStoredWakeArmAndAvailableSyncThenStop() {
        val state = WakeTransitionSyncState()

        state.arm(
            wakeSyncWasArmed = true,
            syncThenStopAvailable = true
        )

        assertTrue(state.isPending)

        state.arm(
            wakeSyncWasArmed = true,
            syncThenStopAvailable = false
        )

        assertFalse(state.isPending)

        state.arm(
            wakeSyncWasArmed = false,
            syncThenStopAvailable = true
        )

        assertFalse(state.isPending)
    }

    @Test
    fun helperPending_doesNotBlockSyncWhenWakeAndWifiAlreadyOn() {
        val state = WakeTransitionSyncState()
        state.arm(wakeSyncWasArmed = true, syncThenStopAvailable = true)

        assertTrue(
            state.mayStartWithHelperPending(
                realWake = true,
                wifiEnabled = true,
                networkRestorePending = false
            )
        )
    }

    @Test
    fun helperPending_neverStartsSyncWithoutWifiOrOnFalseWake() {
        val state = WakeTransitionSyncState()
        state.arm(wakeSyncWasArmed = true, syncThenStopAvailable = true)

        assertFalse(state.mayStartWithHelperPending(true, false, false))
        assertFalse(state.mayStartWithHelperPending(true, null, false))
        assertFalse(state.mayStartWithHelperPending(false, true, false))
        assertFalse(state.mayStartWithHelperPending(true, true, true))

        state.clear()
        assertFalse(state.mayStartWithHelperPending(true, true, false))
    }

    @Test
    fun clear_cancelsPendingWakeTransitionSync() {
        val state = WakeTransitionSyncState()
        state.arm(
            wakeSyncWasArmed = true,
            syncThenStopAvailable = true
        )

        state.clear()

        assertFalse(state.isPending)
    }
}
